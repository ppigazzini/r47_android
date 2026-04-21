package com.example.r47

import android.util.Log
import android.view.Choreographer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class NativeCoreRuntime(
    private val filesDirPath: String,
    private val currentSlotIdProvider: () -> Int,
    private val nativePreInit: (String) -> Unit,
    private val initNative: (String, Int) -> Unit,
    private val updateNativeActivityRef: () -> Unit,
    private val tick: () -> Unit,
    private val saveStateNative: () -> Unit,
    private val forceRefreshNative: () -> Unit,
    private val getDisplayPixels: (IntArray) -> Unit,
    private val getKeyboardStateNative: () -> IntArray,
    private val onLcdPixels: (IntArray) -> Unit,
    private val onDynamicRefresh: (KeyboardStateSnapshot) -> Unit,
) {
    companion object {
        private const val TAG = "R47CoreRuntime"

        private val coreTasks = LinkedBlockingQueue<Runnable>()

        @Volatile
        private var isCoreThreadStarted = false

        @Volatile
        private var isAppRunningShared = false

        @Volatile
        private var isNativeInitializedShared = false

        fun isAppRunning(): Boolean = isAppRunningShared
    }

    private val lcdPixels = IntArray(400 * 240)
    private var lastLabelRefresh = 0L
    private var lastKeyboardState = KeyboardStateSnapshot.EMPTY
    private var frameLoopActive = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopActive || !isAppRunningShared) {
                return
            }

            if (isNativeInitializedShared) {
                getDisplayPixels(lcdPixels)
                if (lcdPixels.isNotEmpty()) {
                    onLcdPixels(lcdPixels)
                }

                val currentState = KeyboardStateSnapshot.fromNative(getKeyboardStateNative())
                val now = System.currentTimeMillis()
                val shouldRefreshLabels = now - lastLabelRefresh > 500
                if (shouldRefreshLabels || currentState != lastKeyboardState) {
                    lastKeyboardState = currentState
                    onDynamicRefresh(currentState)
                }
                if (shouldRefreshLabels) {
                    lastLabelRefresh = now
                }
            }

            if (frameLoopActive && isAppRunningShared) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun attach() {
        isAppRunningShared = true
        startOrAttachCoreThread()
        startFrameLoop()
    }

    fun dispose(stopApp: Boolean) {
        stopFrameLoop()
        if (stopApp) {
            isAppRunningShared = false
        }
    }

    fun offerTask(task: Runnable) {
        if (isNativeInitializedShared) {
            coreTasks.offer(task)
        }
    }

    fun processCoreTasks() {
        drainCoreTasks()
    }

    fun requestForceRefresh() {
        if (isNativeInitializedShared) {
            offerTask(Runnable { forceRefreshNative() })
        }
    }

    fun saveStateOnPause(autoSaveEnabled: Boolean, timeoutSeconds: Long = 2) {
        if (!autoSaveEnabled || !isNativeInitializedShared) {
            return
        }

        val latch = CountDownLatch(1)
        offerTask(
            Runnable {
                try {
                    saveStateNative()
                } finally {
                    latch.countDown()
                }
            }
        )

        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out waiting for state save on pause")
            }
        } catch (error: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for state save", error)
        }
    }

    private fun startOrAttachCoreThread() {
        if (!isCoreThreadStarted) {
            isCoreThreadStarted = true
            Thread {
                try {
                    Log.i(TAG, "Core thread starting; nativeInitialized=$isNativeInitializedShared")
                    if (!isNativeInitializedShared) {
                        nativePreInit(filesDirPath)
                        initNative(filesDirPath, currentSlotIdProvider())
                        isNativeInitializedShared = true
                    } else {
                        updateNativeActivityRef()
                    }

                    var lastTickLog = 0L
                    while (isAppRunningShared) {
                        val now = System.currentTimeMillis()
                        if (now - lastTickLog > 5000) {
                            Log.i(TAG, "Core thread heartbeat")
                            lastTickLog = now
                        }

                        drainCoreTasks()
                        tick()
                        Thread.sleep(10)
                    }
                    Log.i(TAG, "Core thread exiting")
                } catch (error: Exception) {
                    Log.e(TAG, "Native core thread crashed", error)
                } finally {
                    isCoreThreadStarted = false
                }
            }.start()
        } else {
            Log.i(TAG, "Core thread already running; updating activity ref")
            updateNativeActivityRef()
        }
    }

    private fun startFrameLoop() {
        if (frameLoopActive) {
            return
        }

        frameLoopActive = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!frameLoopActive) {
            return
        }

        frameLoopActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun drainCoreTasks() {
        var task = coreTasks.poll()
        while (task != null) {
            try {
                task.run()
            } catch (error: Exception) {
                Log.e(TAG, "Core task failed", error)
            }
            task = coreTasks.poll()
        }
    }
}
package com.example.r47

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.view.*
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import com.example.r47.databinding.ActivityMainBinding
import android.content.SharedPreferences
import android.content.res.Configuration

@Keep
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val TAG = "R47Activity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var replicaOverlay: ReplicaOverlay
    private lateinit var coreRuntime: NativeCoreRuntime
    private lateinit var storageAccessCoordinator: StorageAccessCoordinator
    private lateinit var displayActionController: DisplayActionController
    private lateinit var factoryResetController: FactoryResetController
    private lateinit var preferenceController: MainActivityPreferenceController
    private val hapticFeedbackController by lazy {
        HapticFeedbackController(this, DEFAULT_HAPTIC_INTENSITY)
    }
    private val appPreferences by lazy {
        getSharedPreferences(SlotStore.APP_PREFS_NAME, MODE_PRIVATE)
    }
    private lateinit var slotSessionController: SlotSessionController
    private lateinit var windowModeController: WindowModeController
    
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val DEFAULT_HAPTIC_INTENSITY = 64

        init {
            System.loadLibrary("c47-android")
        }

        fun createFactoryResetIntent(context: Context): Intent {
            return FactoryResetController.createIntent(context)
        }
    }

    private val VINTAGE_TEXT = 0xFF303030.toInt()
    private val VINTAGE_BG = 0xFFDFF5CC.toInt()
    private val BW_TEXT = Color.BLACK
    private val BW_BG = Color.parseColor("#E0E0E0")
    private val YELLOW_SHIFT = Color.parseColor("#FFBF00")

    private fun syncAudioSettings(isBeeperEnabled: Boolean, beeperVolume: Int) {
        AudioEngine.updateSettings(isBeeperEnabled, beeperVolume)
    }

    @Keep
    fun playTone(milliHz: Int, durationMs: Int) {
        AudioEngine.playTone(milliHz, durationMs)
    }

    @Keep
    fun stopTone() {}

    private fun applyLcdMode(mode: String) {
        if (mode == MainActivityPreferenceController.DEFAULT_LCD_MODE) {
            setLcdColors(VINTAGE_TEXT, VINTAGE_BG)
        }
        else setLcdColors(BW_TEXT.toInt(), BW_BG.toInt())
    }

    private fun normalizeChromeMode(mode: String?): String {
        return when {
            mode == null -> MainActivityPreferenceController.DEFAULT_CHROME_MODE
            mode == ReplicaOverlay.CHROME_MODE_NATIVE ||
                mode == ReplicaOverlay.CHROME_MODE_TEXTURE ||
                mode == ReplicaOverlay.CHROME_MODE_BACKGROUND -> mode
            else -> MainActivityPreferenceController.DEFAULT_CHROME_MODE
        }
    }

    private fun applyChromeMode(mode: String) {
        if (::replicaOverlay.isInitialized) {
            replicaOverlay.setChromeMode(mode)
            setupInteractiveZones()
            if (::coreRuntime.isInitialized && mode != ReplicaOverlay.CHROME_MODE_TEXTURE) {
                updateDynamicKeys()
            }
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (prefs == null || key == null) return
        preferenceController.onPreferenceChanged(key)
    }

    private var isPhysicalShiftHeld = false
    private var isPhysicalCtrlHeld = false
    private var interceptedWhileHeld = false
    private val activeKeyIdMap = mutableMapOf<Int, PhysicalKeyboardAction>()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            isPhysicalShiftHeld = true; interceptedWhileHeld = false; return true
        }
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            isPhysicalCtrlHeld = true; interceptedWhileHeld = false; return true
        }
        if (isPhysicalShiftHeld || isPhysicalCtrlHeld) interceptedWhileHeld = true
        if (event?.repeatCount ?: 0 > 0) return true

        PhysicalKeyboardMapper.resolve(keyCode, event)?.let { action ->
            activeKeyIdMap[keyCode] = action
            when (action) {
                is PhysicalKeyboardAction.NativeKey -> {
                    offerCoreTask { sendSimKeyNative(action.id, action.isFunctionKey, false) }
                }
                is PhysicalKeyboardAction.Shortcut -> {
                    PhysicalKeyboardShortcuts.dispatch(
                        action,
                        ::offerCoreTask,
                        ::sendSimKeyNative,
                        ::sendSimMenuNative,
                    )
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!interceptedWhileHeld) { sendSimKeyNative("10", false, false); sendSimKeyNative("10", false, true) }
            isPhysicalShiftHeld = false; return true
        }
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (!interceptedWhileHeld) { sendSimKeyNative("11", false, false); sendSimKeyNative("11", false, true) }
            isPhysicalCtrlHeld = false; return true
        }
        activeKeyIdMap.remove(keyCode)?.let { action ->
            if (action is PhysicalKeyboardAction.NativeKey) {
                offerCoreTask { sendSimKeyNative(action.id, action.isFunctionKey, true) }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    internal fun currentKeypadSnapshot(meta: IntArray? = null): KeypadSnapshot {
        val resolvedMeta = meta ?: getKeypadMetaNative(true)
        return KeypadSnapshot.fromNative(
            resolvedMeta,
            getKeypadLabelsNative(true),
        )
    }

    private fun updateDynamicKeys(snapshot: KeypadSnapshot? = null) {
        val resolvedSnapshot = snapshot ?: currentKeypadSnapshot()
        ReplicaKeypadLayout.updateDynamicKeys(replicaOverlay, resolvedSnapshot)
    }

    private fun offerCoreTask(task: Runnable) {
        coreRuntime.offerTask(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val prefs = appPreferences

        windowModeController = WindowModeController(
            activity = this,
            mainHandler = mainHandler,
            onPiPModeChanged = { isInPictureInPictureMode ->
                Log.i(TAG, "Updating overlay for pipMode=$isInPictureInPictureMode")
                if (::replicaOverlay.isInitialized) {
                    replicaOverlay.setPiPMode(isInPictureInPictureMode)
                }
            },
        )

        factoryResetController = FactoryResetController(
            activity = this,
            onResetRequested = {
                coreRuntime.dispose(stopApp = true)
                AudioEngine.stop()
            },
            onDestroyFactoryReset = {
                AudioEngine.stop()
                releaseNativeRuntime()
                NativeCoreRuntime.resetSharedState()
            },
            onDestroyFinish = {
                AudioEngine.stop()
                releaseNativeRuntime()
            },
        )

        storageAccessCoordinator = StorageAccessCoordinator(
            activity = this,
            appPreferences = prefs,
            rootView = binding.root,
            launchSettings = {
                startActivity(Intent(this, SettingsActivity::class.java).apply {
                    putExtra("trigger_work_dir_picker", true)
                })
            },
            onNativeFileSelected = ::onFileSelectedNative,
            onNativeFileCancelled = ::onFileCancelledNative,
        )
        storageAccessCoordinator.registerLaunchers()

        displayActionController = DisplayActionController(
            context = this,
            mainHandler = mainHandler,
            offerCoreTask = ::offerCoreTask,
            getXRegisterNative = ::getXRegisterNative,
            sendSimFuncNative = ::sendSimFuncNative,
            sendSimKeyNative = ::sendSimKeyNative,
            enterPiP = windowModeController::enterPictureInPicture,
        )

        slotSessionController = SlotSessionController(
            context = this,
            mainHandler = mainHandler,
            offerCoreTask = ::offerCoreTask,
            saveStateNative = ::saveStateNative,
            loadStateNative = ::loadStateNative,
            setSlotNative = ::setSlotNative,
        )
        
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        replicaOverlay = binding.replicaOverlay
        replicaOverlay.onPiPKeyEvent = { code ->
            offerCoreTask { sendKey(code) }
        }

        preferenceController = MainActivityPreferenceController(
            preferences = prefs,
            window = window,
            hapticFeedbackController = hapticFeedbackController,
            windowModeController = windowModeController,
            syncAudioSettings = ::syncAudioSettings,
            applyLcdMode = ::applyLcdMode,
            applyChromeMode = ::applyChromeMode,
            applyScalingMode = replicaOverlay::setScalingMode,
            applyShowTouchZones = replicaOverlay::setShowTouchZones,
            normalizeChromeMode = ::normalizeChromeMode,
        )
        prefs.registerOnSharedPreferenceChangeListener(this)
        preferenceController.applyInitialPreferences()
        
        coreRuntime = NativeCoreRuntime(
            filesDirPath = filesDir.absolutePath,
            currentSlotIdProvider = slotSessionController::currentSlotId,
            nativePreInit = ::nativePreInit,
            initNative = ::initNative,
            updateNativeActivityRef = ::updateNativeActivityRef,
            tick = ::tick,
            saveStateNative = ::saveStateNative,
            forceRefreshNative = ::forceRefreshNative,
            getDisplayPixels = ::getDisplayPixels,
            getKeypadMetaNative = ::getKeypadMetaNative,
            useSceneDrivenKeypadProvider = { true },
            getKeypadSnapshot = ::currentKeypadSnapshot,
            onLcdPixels = { pixels -> replicaOverlay.updateLcd(pixels) },
            onDynamicRefresh = ::updateDynamicKeys,
        )
        
        replicaOverlay.post {
            preferenceController.applyDeferredOverlayPreferences()
            if (preferenceController.chromeMode != ReplicaOverlay.CHROME_MODE_TEXTURE) {
                updateDynamicKeys()
            }
        }

        displayActionController.bindOverlay(replicaOverlay)
        
        replicaOverlay.onSettingsTapListener = {
            Log.i(TAG, "Settings tap received in MainActivity")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        coreRuntime.attach()
        AudioEngine.start { NativeCoreRuntime.isAppRunning() }

        if (factoryResetController.isFactoryResetIntent(intent)) {
            binding.root.post { factoryResetController.handleResetRequest() }
        }
    }

    private external fun updateNativeActivityRef()

    override fun onDestroy() { 
        Log.i(TAG, "onDestroy: isFinishing=$isFinishing isFactoryResetInProgress=${factoryResetController.isResetInProgress}")
        val shouldStopApp = isFinishing || factoryResetController.isResetInProgress
        coreRuntime.dispose(stopApp = shouldStopApp)
        appPreferences.unregisterOnSharedPreferenceChangeListener(this)
        factoryResetController.handleDestroy(shouldStopApp)
        super.onDestroy() 
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (factoryResetController.isFactoryResetIntent(intent)) {
            factoryResetController.handleResetRequest()
        }
    }

    override fun onResume() {
        super.onResume()
        coreRuntime.requestForceRefresh()
        storageAccessCoordinator.handleResume()
    }

    override fun onPause() {
        super.onPause()
        val isEnteringPiP = windowModeController.isEnteringPictureInPicture()
        Log.i(TAG, "onPause: isEnteringPiP=$isEnteringPiP")
        
        if (!isEnteringPiP && !factoryResetController.isResetInProgress && appPreferences.getBoolean("auto_save_minimize", true)) {
            Log.i(TAG, "Auto-saving state on pause (synchronous via core thread)...")
            coreRuntime.saveStateOnPause(autoSaveEnabled = true)
        }
    }

    @Keep
    fun requestFile(isSave: Boolean, defaultName: String, fileType: Int) {
        mainHandler.post {
            storageAccessCoordinator.requestNativeFile(isSave, defaultName, fileType)
        }
    }

    @Keep
    fun quitApp() {
        Log.i(TAG, "quitApp called from native")
        mainHandler.post { 
            val forceClose = appPreferences.getBoolean("force_close_on_exit", false)
            if (forceClose) {
                finishAndRemoveTask()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    @Keep
    fun processCoreTasks() {
        coreRuntime.processCoreTasks()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        windowModeController.handlePictureInPictureModeChanged(isInPictureInPictureMode)
    }

    private fun setupInteractiveZones() {
        ReplicaKeypadLayout.rebuild(
            activity = this,
            overlay = replicaOverlay,
            chromeMode = preferenceController.chromeMode,
            performHapticClick = hapticFeedbackController::performClick,
            dispatchKey = { keyCode -> offerCoreTask { sendKey(keyCode) } },
        )
    }

    private external fun nativePreInit(storagePath: String)
    private external fun initNative(storagePath: String, slotId: Int)
    private external fun tick()
    private external fun releaseNativeRuntime()
    private external fun sendKey(keyCode: Int)
    private external fun sendSimKeyNative(keyId: String, isFn: Boolean, isRelease: Boolean)
    private external fun sendSimMenuNative(menuId: Int)
    private external fun sendSimFuncNative(funcId: Int)
    private external fun saveStateNative()
    private external fun loadStateNative()
    private external fun forceRefreshNative()
    private external fun setSlotNative(slot: Int)
    private external fun getXRegisterNative(): String
    private external fun getDisplayPixels(pixels: IntArray)
    private external fun setLcdColors(text: Int, bg: Int)
    
    // Legacy keypad getters kept for bridge compatibility.
    private external fun getButtonLabelNative(keyCode: Int, type: Int, isDynamic: Boolean): String
    private external fun getSoftkeyLabelNative(fnKeyIndex: Int): String
    private external fun getKeyboardStateNative(): IntArray // returns [shiftF, shiftG, calcMode, userMode, alphaFlag]

    // Snapshot keypad APIs used by the default Android-native keypad.
    private external fun getKeypadMetaNative(isDynamic: Boolean): IntArray
    private external fun getKeypadLabelsNative(isDynamic: Boolean): Array<String>

    @Keep fun onFileSelected(fd: Int) { onFileSelectedNative(fd) }
    @Keep fun onFileCancelled() { onFileCancelledNative() }
    private external fun onFileSelectedNative(fd: Int)
    private external fun onFileCancelledNative()
}
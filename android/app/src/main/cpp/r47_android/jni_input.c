#include "jni_bridge.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

extern void btnFnPressed(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnFnReleased(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnPressed(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnReleased(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void fnStopProgram(uint16_t unusedButMandatoryParameter);

static char currentPressedKeyStr[4] = {0};
static int currentPressedKeyCode = 0;

enum {
  R47_ASYNC_RUN_KEY_CODE = 36,
  R47_ASYNC_VIEW_TIMEOUT_MS = 2000,
};

static const char *const kR47AsyncRunKeyId = "35";

typedef enum {
  R47_ASYNC_RELEASE_EVENT = 0,
  R47_ASYNC_RELEASE_SIM = 1,
} r47_async_release_kind_t;

typedef struct {
  r47_async_release_kind_t kind;
  bool is_fn;
  char key_id[4];
} r47_async_key_release_t;

static pthread_mutex_t r47_async_program_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool r47_async_program_running = false;
static uint32_t r47_async_program_view_since_ms = 0;

static bool r47_is_async_run_key_id(const char *keyId) {
  return keyId != NULL && strcmp(keyId, kR47AsyncRunKeyId) == 0;
}

static bool r47_is_async_program_running(void) {
  bool running = false;

  pthread_mutex_lock(&r47_async_program_mutex);
  running = r47_async_program_running;
  pthread_mutex_unlock(&r47_async_program_mutex);

  return running;
}

static void r47_set_async_program_running(bool running) {
  pthread_mutex_lock(&r47_async_program_mutex);
  r47_async_program_running = running;
  r47_async_program_view_since_ms = 0;
  pthread_mutex_unlock(&r47_async_program_mutex);
}

static void *r47_async_key_release_worker_main(void *arg) {
  r47_async_key_release_t *release = (r47_async_key_release_t *)arg;

  pthread_mutex_lock(&screenMutex);
  if (release->kind == R47_ASYNC_RELEASE_EVENT) {
    if (release->is_fn) {
      btnFnReleased(NULL, &releaseEvent, release->key_id);
    } else {
      btnReleased(NULL, &releaseEvent, release->key_id);
    }
  } else {
    if (release->is_fn) {
      extern void btnFnClickedR(void *w, void *data);
      btnFnClickedR(NULL, release->key_id);
    } else {
      extern void btnClickedR(void *w, void *data);
      btnClickedR(NULL, release->key_id);
    }
  }
  pthread_mutex_unlock(&screenMutex);

  r47_set_async_program_running(false);
  free(release);
  return NULL;
}

static bool r47_begin_async_key_release(r47_async_release_kind_t kind,
                                        const char *keyId,
                                        bool isFn) {
  pthread_t worker_thread;
  r47_async_key_release_t *release = NULL;

  pthread_mutex_lock(&r47_async_program_mutex);
  if (r47_async_program_running) {
    pthread_mutex_unlock(&r47_async_program_mutex);
    return false;
  }
  r47_async_program_running = true;
  r47_async_program_view_since_ms = 0;
  pthread_mutex_unlock(&r47_async_program_mutex);

  release = calloc(1, sizeof(*release));
  if (release == NULL) {
    r47_set_async_program_running(false);
    return false;
  }

  release->kind = kind;
  release->is_fn = isFn;
  strncpy(release->key_id, keyId, sizeof(release->key_id) - 1);
  release->key_id[sizeof(release->key_id) - 1] = 0;

  if (pthread_create(&worker_thread, NULL, r47_async_key_release_worker_main,
                     release) != 0) {
    free(release);
    r47_set_async_program_running(false);
    return false;
  }

  pthread_detach(worker_thread);
  return true;
}

static void r47_request_async_program_stop(void) {
  bool should_stop = false;

  pthread_mutex_lock(&r47_async_program_mutex);
  should_stop = r47_async_program_running;
  r47_async_program_view_since_ms = 0;
  pthread_mutex_unlock(&r47_async_program_mutex);

  if (should_stop) {
    fnStopProgram(NOPARAM);
  }
}

void r47_handle_async_program_tick(uint32_t now_ms) {
  bool should_stop = false;

  if (!ram) {
    return;
  }

  pthread_mutex_lock(&r47_async_program_mutex);
  if (r47_async_program_running && programRunStop == PGM_RUNNING &&
      temporaryInformation == TI_VIEW_REGISTER) {
    if (r47_async_program_view_since_ms == 0) {
      r47_async_program_view_since_ms = now_ms;
    } else if ((uint32_t)(now_ms - r47_async_program_view_since_ms) >=
               R47_ASYNC_VIEW_TIMEOUT_MS) {
      should_stop = true;
      r47_async_program_view_since_ms = 0;
    }
  } else {
    r47_async_program_view_since_ms = 0;
  }
  pthread_mutex_unlock(&r47_async_program_mutex);

  if (should_stop) {
    LOGI("Stopping async Android R/S run after %u ms of VIEW loop",
         R47_ASYNC_VIEW_TIMEOUT_MS);
    fnStopProgram(NOPARAM);
  }
}

void r47_send_sim_function(int funcId) {
  pthread_mutex_lock(&screenMutex);
  extern void runFunction(int16_t id);
  runFunction((int16_t)funcId);
  pthread_mutex_unlock(&screenMutex);
}

void r47_send_sim_menu(int menuId) {
  pthread_mutex_lock(&screenMutex);
  extern void showSoftmenu(int16_t id);
  showSoftmenu((int16_t)menuId);
  refreshScreen(1);
  pthread_mutex_unlock(&screenMutex);
}

void r47_send_sim_key(const char *keyId, bool isFn, bool isRelease) {
  if (!ram || isCoreBlockingForIo || keyId == NULL) {
    return;
  }

  if (r47_is_async_program_running()) {
    if (!isRelease && !isFn && r47_is_async_run_key_id(keyId)) {
      r47_request_async_program_stop();
    }
    return;
  }

  if (!isFn && isRelease && r47_is_async_run_key_id(keyId) &&
      r47_begin_async_key_release(R47_ASYNC_RELEASE_SIM, keyId, false)) {
    return;
  }

  pthread_mutex_lock(&screenMutex);
  if (isFn) {
    if (isRelease) {
      extern void btnFnClickedR(void *w, void *data);
      btnFnClickedR(NULL, (void *)keyId);
    } else {
      extern void btnFnClickedP(void *w, void *data);
      btnFnClickedP(NULL, (void *)keyId);
    }
  } else {
    if (isRelease) {
      extern void btnClickedR(void *w, void *data);
      btnClickedR(NULL, (void *)keyId);
    } else {
      extern void btnClickedP(void *w, void *data);
      btnClickedP(NULL, (void *)keyId);
    }
  }
  pthread_mutex_unlock(&screenMutex);
}

JNIEXPORT void JNICALL
Java_com_example_r47_MainActivity_sendSimFuncNative(
    JNIEnv *env, jobject thiz, jint funcId) {
  (void)env;
  (void)thiz;
  r47_send_sim_function((int)funcId);
}

JNIEXPORT void JNICALL
Java_com_example_r47_MainActivity_sendSimMenuNative(
    JNIEnv *env, jobject thiz, jint menuId) {
  (void)env;
  (void)thiz;
  r47_send_sim_menu((int)menuId);
}

JNIEXPORT void JNICALL
Java_com_example_r47_MainActivity_sendSimKeyNative(
    JNIEnv *env, jobject thiz, jstring keyId, jboolean isFn,
    jboolean isRelease) {
  (void)thiz;
  if (!ram || isCoreBlockingForIo) {
    return;
  }

  const char *nativeKeyId = (*env)->GetStringUTFChars(env, keyId, 0);
  if (!nativeKeyId ||
      jni_check_and_clear_exception(env,
                                    "sendSimKeyNative GetStringUTFChars")) {
    return;
  }

  r47_send_sim_key(nativeKeyId, isFn == JNI_TRUE, isRelease == JNI_TRUE);
  (*env)->ReleaseStringUTFChars(env, keyId, nativeKeyId);
}

JNIEXPORT void JNICALL Java_com_example_r47_MainActivity_sendKey(
    JNIEnv *env, jobject thiz, jint keyCode) {
  (void)env;
  (void)thiz;
  if (!ram) {
    return;
  }

  onUIActivity();
  if (r47_is_async_program_running()) {
    if (keyCode == R47_ASYNC_RUN_KEY_CODE) {
      r47_request_async_program_stop();
    }
    if (keyCode <= 0) {
      currentPressedKeyCode = 0;
      currentPressedKeyStr[0] = 0;
    }
    return;
  }

  if (keyCode > 0) {
    LOGD("sendKey: DOWN %d", keyCode);
    currentPressedKeyCode = keyCode;
    pthread_mutex_lock(&screenMutex);
    if (keyCode >= 38 && keyCode <= 43) {
      snprintf(currentPressedKeyStr, sizeof(currentPressedKeyStr), "%c",
               keyCode - 38 + '1');
      btnFnPressed(NULL, &pressEvent, currentPressedKeyStr);
    } else if (keyCode >= 1 && keyCode <= 37) {
      snprintf(currentPressedKeyStr, sizeof(currentPressedKeyStr), "%02u",
               keyCode - 1);
      btnPressed(NULL, &pressEvent, currentPressedKeyStr);
    }
    pthread_mutex_unlock(&screenMutex);
    return;
  }

  LOGD("sendKey: UP (last=%d)", currentPressedKeyCode);
  if (currentPressedKeyCode == R47_ASYNC_RUN_KEY_CODE &&
      r47_begin_async_key_release(R47_ASYNC_RELEASE_EVENT,
                                  currentPressedKeyStr, false)) {
    currentPressedKeyCode = 0;
    currentPressedKeyStr[0] = 0;
    return;
  }

  pthread_mutex_lock(&screenMutex);
  if (currentPressedKeyCode >= 38 && currentPressedKeyCode <= 43) {
    btnFnReleased(NULL, &releaseEvent, currentPressedKeyStr);
  } else if (currentPressedKeyCode >= 1 && currentPressedKeyCode <= 37) {
    btnReleased(NULL, &releaseEvent, currentPressedKeyStr);
  }
  pthread_mutex_unlock(&screenMutex);
  currentPressedKeyCode = 0;
  currentPressedKeyStr[0] = 0;
}

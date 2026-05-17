#include "jni_bridge.h"

#include <stdio.h>
#include <string.h>

extern void btnFnPressed(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnFnReleased(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnPressed(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void btnReleased(GtkWidget *notUsed, GdkEvent *event, gpointer data);
extern void fnDropY(uint16_t unusedButMandatoryParameter);
extern void fnStore(uint16_t regist);
extern void runProgram(bool_t singleStep, uint16_t menuLabel);

extern uint8_t lastErrorCode;
extern uint16_t currentInputVariable;
extern int16_t dynamicMenuItem;
extern uint8_t *currentStep;

static char currentPressedKeyStr[4] = {0};
static int currentPressedKeyCode = 0;

enum {
  R47_ASYNC_RUN_KEY_CODE = 36,
  R47_ASYNC_RUN_SLICE_MS = 4,
  R47_ASYNC_RUN_MAX_STEPS = 64,
  R47_PROGRAM_END_OPCODE = 0x7fff,
};

static const char *const kR47AsyncRunKeyId = "35";

static pthread_mutex_t r47_async_program_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool r47_async_program_running = false;
static bool r47_async_program_start_pending = false;
static bool r47_async_program_stop_requested = false;

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
  r47_async_program_start_pending = false;
  r47_async_program_stop_requested = false;
  pthread_mutex_unlock(&r47_async_program_mutex);
}

static void r47_schedule_async_program_start(void) {
  pthread_mutex_lock(&r47_async_program_mutex);
  if (!r47_async_program_running) {
    r47_async_program_running = true;
    r47_async_program_start_pending = true;
    r47_async_program_stop_requested = false;
  }
  pthread_mutex_unlock(&r47_async_program_mutex);
}

static void r47_request_async_program_stop(void) {
  pthread_mutex_lock(&r47_async_program_mutex);
  if (r47_async_program_running) {
    r47_async_program_stop_requested = true;
  }
  pthread_mutex_unlock(&r47_async_program_mutex);
}

static uint16_t r47_decode_step_opcode(const uint8_t *step) {
  uint16_t opcode;

  opcode = *step;
  if ((opcode & 0x80u) != 0u) {
    opcode = (uint16_t)(((opcode & 0x7fu) << 8) | *(step + 1));
  }

  return opcode;
}

static void r47_prepare_async_program_start_locked(void) {
  if (currentInputVariable != INVALID_VARIABLE) {
    if ((currentInputVariable & 0x8000u) != 0u) {
      fnDropY(NOPARAM);
    }
    fnStore((uint16_t)(currentInputVariable & 0x3fffu));
    currentInputVariable = INVALID_VARIABLE;
  }
  dynamicMenuItem = -1;
}

static bool r47_should_finish_async_program_after_step(uint16_t opcode) {
  if (lastErrorCode != ERROR_NONE || isCoreBlockingForIo ||
      programRunStop == PGM_WAITING || programRunStop == PGM_PAUSED) {
    return true;
  }

  switch (opcode) {
    case ITM_STOP:
    case ITM_RTN:
    case ITM_END:
    case ITM_RTNP1:
    case R47_PROGRAM_END_OPCODE:
      return true;

    default:
      return false;
  }
}

void r47_handle_async_program_tick(uint32_t now_ms) {
  (void)now_ms;

  if (!ram) {
    return;
  }

  for (uint32_t step_count = 0; step_count < R47_ASYNC_RUN_MAX_STEPS;
       ++step_count) {
    bool start_pending = false;
    bool stop_requested = false;
    uint16_t opcode;

    pthread_mutex_lock(&r47_async_program_mutex);
    if (!r47_async_program_running) {
      pthread_mutex_unlock(&r47_async_program_mutex);
      break;
    }
    start_pending = r47_async_program_start_pending;
    stop_requested = r47_async_program_stop_requested;
    r47_async_program_start_pending = false;
    pthread_mutex_unlock(&r47_async_program_mutex);

    if (stop_requested) {
      r47_set_async_program_running(false);
      break;
    }

    if (start_pending) {
      r47_prepare_async_program_start_locked();
    }

    if (currentStep == NULL) {
      r47_set_async_program_running(false);
      break;
    }

    opcode = r47_decode_step_opcode(currentStep);
    runProgram(true, INVALID_VARIABLE);

    if (r47_should_finish_async_program_after_step(opcode)) {
      r47_set_async_program_running(false);
      break;
    }

    if ((uint32_t)(sys_current_ms() - now_ms) >= R47_ASYNC_RUN_SLICE_MS) {
      break;
    }
  }
}

void r47_send_sim_function(int funcId) {
  if (r47_is_async_program_running()) {
    return;
  }

  pthread_mutex_lock(&screenMutex);
  extern void runFunction(int16_t id);
  runFunction((int16_t)funcId);
  pthread_mutex_unlock(&screenMutex);
}

void r47_send_sim_menu(int menuId) {
  if (r47_is_async_program_running()) {
    return;
  }

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

  if (!isFn && r47_is_async_run_key_id(keyId)) {
    if (r47_is_async_program_running()) {
      r47_request_async_program_stop();
    } else if (isRelease) {
      r47_schedule_async_program_start();
    }
    return;
  }

  if (r47_is_async_program_running()) {
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

  if (keyCode > 0) {
    LOGD("sendKey: DOWN %d", keyCode);

    if (keyCode == R47_ASYNC_RUN_KEY_CODE) {
      currentPressedKeyCode = keyCode;
      strncpy(currentPressedKeyStr, kR47AsyncRunKeyId,
              sizeof(currentPressedKeyStr) - 1);
      currentPressedKeyStr[sizeof(currentPressedKeyStr) - 1] = 0;
      if (r47_is_async_program_running()) {
        r47_request_async_program_stop();
      }
      return;
    }

    if (r47_is_async_program_running()) {
      return;
    }

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
  if (currentPressedKeyCode == R47_ASYNC_RUN_KEY_CODE) {
    if (r47_is_async_program_running()) {
      r47_request_async_program_stop();
    } else {
      r47_schedule_async_program_start();
    }
    currentPressedKeyCode = 0;
    currentPressedKeyStr[0] = 0;
    return;
  }

  if (r47_is_async_program_running()) {
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

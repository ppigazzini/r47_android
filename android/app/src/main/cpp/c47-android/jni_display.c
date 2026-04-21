#include "jni_bridge.h"

#include "keyboard.h"

#include <stdlib.h>
#include <string.h>

static int16_t calculateKeyLogicalId(int16_t keyId) {
  if (keyId < 30)
    return keyId - 21;
  if (keyId < 40)
    return keyId - 25;
  if (keyId < 50)
    return keyId - 29;
  if (keyId < 60)
    return keyId - 34;
  if (keyId < 70)
    return keyId - 39;
  if (keyId < 80)
    return keyId - 44;
  return keyId - 49;
}

static void fillKeyboardState(jint *fill) {
  extern bool_t getSystemFlag(int32_t sf);
  fill[0] = (jint)shiftF;
  fill[1] = (jint)shiftG;
  fill[2] = (jint)calcMode;
  fill[3] = (jint)getSystemFlag(0x8014);
  fill[4] = (jint)getSystemFlag(0x800e);
}

JNIEXPORT jstring JNICALL
Java_com_example_r47_MainActivity_getXRegisterNative(
    JNIEnv *env, jobject thiz) {
  (void)thiz;
  if (!ram || isCoreBlockingForIo) {
    return (*env)->NewStringUTF(env, "0");
  }

  pthread_mutex_lock(&screenMutex);
  extern char *getXRegisterString(void);
  char *registerText = getXRegisterString();
  jstring result = (*env)->NewStringUTF(env, registerText ? registerText : "0");
  pthread_mutex_unlock(&screenMutex);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_example_r47_MainActivity_getButtonLabelNative(JNIEnv *env,
                                                              jobject thiz,
                                                              jint keyCode,
                                                              jint type,
                                                              jboolean isDynamic) {
  (void)thiz;
  if (!ram) {
    return (*env)->NewStringUTF(env, "");
  }

  pthread_mutex_lock(&screenMutex);
  extern bool_t getSystemFlag(int32_t sf);
  bool_t alphaOn = (calcMode == CM_AIM) ||
                   ((calcMode == CM_PEM || calcMode == CM_ASSIGN) &&
                    getSystemFlag(0x800e)) ||
                   ((tam.mode != 0 || tam.alpha) && getSystemFlag(0x800e));

  const calcKey_t *keys =
      (isDynamic && getSystemFlag(0x8014)) ? kbd_usr : kbd_std;
  if (keyCode < 1 || keyCode > 37) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  const calcKey_t *key = &keys[keyCode - 1];
  int16_t item = 0;
  if (alphaOn) {
    switch (type) {
    case 0:
      item = key->primaryAim;
      break;
    case 1:
      item = key->fShiftedAim;
      break;
    case 2:
      item = key->gShiftedAim;
      break;
    case 3:
      item = 0;
      break;
    }
  } else {
    switch (type) {
    case 0:
      if (isDynamic) {
        if (shiftF)
          item = key->fShifted;
        else if (shiftG)
          item = key->gShifted;
        else
          item = key->primary;
      } else {
        item = key->primary;
      }
      break;
    case 1:
      item = key->fShifted;
      break;
    case 2:
      item = key->gShifted;
      break;
    case 3:
      item = key->primaryAim;
      break;
    }
  }

  if (item == 0) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  const char *name = indexOfItems[abs(item)].itemSoftmenuName;
  if (!name) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  if (isDynamic && (userKeyLabelSize > 0) &&
      (strcmp(name, "DYNMNU") == 0 || strcmp(name, "XEQ") == 0 ||
       strcmp(name, "RCL") == 0)) {
    int16_t keyLogicalId = calculateKeyLogicalId(key->keyId);
    int16_t keyStateCode = type;
    uint8_t *userLabel =
        getNthString((uint8_t *)userKeyLabel, keyLogicalId * 6 + keyStateCode);
    if (userLabel && userLabel[0] != 0) {
      name = (char *)userLabel;
    }
  }

  uint8_t utf8[64];
  memset(utf8, 0, sizeof(utf8));
  stringToUtf8(name, utf8);
  pthread_mutex_unlock(&screenMutex);
  return (*env)->NewStringUTF(env, (char *)utf8);
}

JNIEXPORT jstring JNICALL
Java_com_example_r47_MainActivity_getSoftkeyLabelNative(JNIEnv *env,
                                                               jobject thiz,
                                                               jint fnKeyIndex) {
  (void)thiz;
  if (!ram || fnKeyIndex < 1 || fnKeyIndex > 6) {
    return (*env)->NewStringUTF(env, "");
  }

  pthread_mutex_lock(&screenMutex);
  int16_t softmenuId = softmenuStack[0].softmenuId;
  if (softmenuId < 0 || softmenuId >= NUMBER_OF_DYNAMIC_SOFTMENUS) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  int16_t firstItem = softmenuStack[0].firstItem;
  int16_t itemShift = (shiftF ? 6 : (shiftG ? 12 : 0));
  int16_t index = firstItem + itemShift + (fnKeyIndex - 1);
  char *labelName = (char *)getNthString(dynamicSoftmenu[softmenuId].menuContent,
                                         index);
  if (!labelName || labelName[0] == 0) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  uint8_t utf8[64];
  memset(utf8, 0, sizeof(utf8));
  stringToUtf8(labelName, utf8);
  pthread_mutex_unlock(&screenMutex);
  return (*env)->NewStringUTF(env, (char *)utf8);
}

JNIEXPORT jintArray JNICALL
Java_com_example_r47_MainActivity_getKeyboardStateNative(JNIEnv *env,
                                                                jobject thiz) {
  (void)thiz;
  if (!ram) {
    return NULL;
  }

  pthread_mutex_lock(&screenMutex);
  jintArray result = (*env)->NewIntArray(env, 5);
  if (result == NULL) {
    pthread_mutex_unlock(&screenMutex);
    return NULL;
  }

  jint fill[5];
  fillKeyboardState(fill);
  (*env)->SetIntArrayRegion(env, result, 0, 5, fill);
  pthread_mutex_unlock(&screenMutex);
  return result;
}

JNIEXPORT void JNICALL
Java_com_example_r47_MainActivity_getDisplayPixels(
    JNIEnv *env, jobject thiz, jintArray pixels) {
  (void)thiz;
  if (!screenData) {
    return;
  }

  extern bool screenDataDirty;
  if (!screenDataDirty) {
    return;
  }

  if (pthread_mutex_trylock(&screenMutex) != 0) {
    return;
  }

  (*env)->SetIntArrayRegion(env, pixels, 0, 400 * 240, (jint *)screenData);
  screenDataDirty = false;
  pthread_mutex_unlock(&screenMutex);
}

void dmcpResetAutoOff() {}
void rtc_wakeup_delay() {}
void LCD_power_on() {}

void triggerQuit() {
  LOGI("triggerQuit called");
  if (!g_mainActivityObj || !g_jvm) {
    LOGE("triggerQuit: MainActivity or JVM reference is NULL");
    return;
  }

  JNIEnv *env;
  jint result = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
  if (result == JNI_EDETACHED) {
    LOGI("triggerQuit: Attaching thread to JVM");
    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
      LOGE("triggerQuit: Failed to attach thread");
      return;
    }
  } else if (result != JNI_OK) {
    LOGE("triggerQuit: GetEnv failed");
    return;
  }

  jclass clazz = (*env)->GetObjectClass(env, g_mainActivityObj);
  jmethodID methodId = (*env)->GetMethodID(env, clazz, "quitApp", "()V");
  if (methodId) {
    LOGI("triggerQuit: Calling Java quitApp()");
    (*env)->CallVoidMethod(env, g_mainActivityObj, methodId);
  } else {
    LOGE("triggerQuit: Could not find quitApp method ID");
  }
  (*env)->DeleteLocalRef(env, clazz);
}

void LCD_power_off(int mode) {
  (void)mode;
  LOGI("LCD_power_off triggered");
  triggerQuit();
}

void draw_power_off_image(int mode) { (void)mode; }

void pgm_exit(void) {
  LOGI("pgm_exit triggered");
  triggerQuit();
}
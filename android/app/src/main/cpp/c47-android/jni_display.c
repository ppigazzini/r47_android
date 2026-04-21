#include "jni_bridge.h"

#include "keyboard.h"

#include <stdlib.h>
#include <string.h>

enum {
  KEYPAD_LABEL_PRIMARY = 0,
  KEYPAD_LABEL_F = 1,
  KEYPAD_LABEL_G = 2,
  KEYPAD_LABEL_LETTER = 3,
  KEYPAD_LABELS_PER_KEY = 4,
  KEYPAD_KEY_COUNT = 43,
  KEYPAD_META_SHIFT_F = 0,
  KEYPAD_META_SHIFT_G = 1,
  KEYPAD_META_CALC_MODE = 2,
  KEYPAD_META_USER_MODE = 3,
  KEYPAD_META_ALPHA = 4,
  KEYPAD_META_SOFTMENU_ID = 5,
  KEYPAD_META_SOFTMENU_FIRST_ITEM = 6,
  KEYPAD_META_SOFTMENU_ITEM_COUNT = 7,
  KEYPAD_META_SOFTMENU_VISIBLE_ROW = 8,
  KEYPAD_META_SOFTMENU_PAGE = 9,
  KEYPAD_META_SOFTMENU_PAGE_COUNT = 10,
  KEYPAD_META_SOFTMENU_HAS_PREVIOUS = 11,
  KEYPAD_META_SOFTMENU_HAS_NEXT = 12,
  KEYPAD_META_KEY_ENABLED_OFFSET = 13,
  KEYPAD_META_LENGTH = KEYPAD_META_KEY_ENABLED_OFFSET + KEYPAD_KEY_COUNT,
};

extern void changeSoftKey(int16_t menuNr, int16_t itemNr, char *itemName,
                          videoMode_t *vm, int8_t *showCb,
                          int16_t *showValue, char *showText);
extern bool_t itemNotAvail(int16_t itemNr);

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

static bool_t isUserKeyboardEnabled(void) {
  extern bool_t getSystemFlag(int32_t sf);
  return getSystemFlag(0x8014);
}

static bool_t isAlphaKeyboardActive(void) {
  extern bool_t getSystemFlag(int32_t sf);
  return (calcMode == CM_AIM) ||
         ((calcMode == CM_PEM || calcMode == CM_ASSIGN) &&
          getSystemFlag(0x800e)) ||
         ((tam.mode != 0 || tam.alpha) && getSystemFlag(0x800e));
}

static const calcKey_t *getVisibleKeyTable(jboolean isDynamic) {
  return (isDynamic && isUserKeyboardEnabled()) ? kbd_usr : kbd_std;
}

static int16_t getCurrentSoftmenuItemCount(int16_t softmenuId) {
  if (softmenuId < 0) {
    return 0;
  }

  if (softmenuId < NUMBER_OF_DYNAMIC_SOFTMENUS) {
    return dynamicSoftmenu[softmenuId].numItems;
  }

  if (softmenu[softmenuId].menuItem == -MNU_EQN && numberOfFormulae == 0) {
    return 1;
  }

  return softmenu[softmenuId].numItems;
}

static int16_t getVisibleSoftkeyRowOffset(void) {
  if (shiftF) {
    return 1;
  }
  if (shiftG) {
    return 2;
  }
  return 0;
}

static void encodeUtf8Label(const char *name, char *utf8, size_t utf8Size) {
  memset(utf8, 0, utf8Size);
  if (!name || name[0] == 0) {
    return;
  }
  stringToUtf8(name, (uint8_t *)utf8);
}

static int16_t resolveMainKeyItem(const calcKey_t *key, jint type,
                                  bool_t alphaOn, jboolean isDynamic) {
  if (alphaOn) {
    switch (type) {
    case KEYPAD_LABEL_PRIMARY:
      return key->primaryAim;
    case KEYPAD_LABEL_F:
      return key->fShiftedAim;
    case KEYPAD_LABEL_G:
      return key->gShiftedAim;
    case KEYPAD_LABEL_LETTER:
    default:
      return 0;
    }
  }

  switch (type) {
  case KEYPAD_LABEL_PRIMARY:
    if (isDynamic) {
      if (shiftF) {
        return key->fShifted;
      }
      if (shiftG) {
        return key->gShifted;
      }
    }
    return key->primary;
  case KEYPAD_LABEL_F:
    return key->fShifted;
  case KEYPAD_LABEL_G:
    return key->gShifted;
  case KEYPAD_LABEL_LETTER:
    return key->primaryAim;
  default:
    return 0;
  }
}

static const char *resolveMainKeyLabel(const calcKey_t *key, jint keyCode,
                                       jint type, jboolean isDynamic,
                                       bool_t alphaOn) {
  if (keyCode == 37 && type == KEYPAD_LABEL_LETTER) {
    return "_";
  }

  if (!alphaOn) {
    if (keyCode == 11 && type == KEYPAD_LABEL_F) {
      return "HOME";
    }
    if (keyCode == 11 && type == KEYPAD_LABEL_G) {
      return "";
    }
    if (keyCode == 12 && type == KEYPAD_LABEL_F) {
      return "CUST";
    }
    if (keyCode == 12 && type == KEYPAD_LABEL_G) {
      return "";
    }
  }

  int16_t item = resolveMainKeyItem(key, type, alphaOn, isDynamic);
  if (item == 0) {
    return "";
  }

  const char *name = indexOfItems[abs(item)].itemSoftmenuName;
  if (!name) {
    return "";
  }

  if (isDynamic && (userKeyLabelSize > 0) &&
      (strcmp(name, "DYNMNU") == 0 || strcmp(name, "XEQ") == 0 ||
       strcmp(name, "RCL") == 0)) {
    int16_t keyLogicalId = calculateKeyLogicalId(key->keyId);
    int16_t keyStateCode = type;
    uint8_t *userLabel =
        getNthString((uint8_t *)userKeyLabel, keyLogicalId * 6 + keyStateCode);
    if (userLabel && userLabel[0] != 0) {
      return (char *)userLabel;
    }
  }

  return name;
}

static int16_t findSoftmenuIndexByItem(int16_t item) {
  int16_t menu = 0;
  while (softmenu[menu].menuItem != 0) {
    if (softmenu[menu].menuItem == item) {
      return menu;
    }
    menu++;
  }
  return -1;
}

static void fillStaticSoftkeyMenuLabel(int16_t item, char *label,
                                       size_t labelSize) {
  int16_t menu = findSoftmenuIndexByItem(item);
  const char *labelName = "";

  if (item == -MNU_HOME || item == -MNU_PFN) {
    labelName = indexOfItems[-item].itemSoftmenuName;
  } else if (menu < 0) {
    labelName = "";
  } else if (softmenu[menu].menuItem == -MNU_ALPHA_OMEGA &&
             alphaCase == AC_UPPER) {
    labelName = indexOfItems[MNU_ALPHA_OMEGA].itemSoftmenuName;
  } else if (softmenu[menu].menuItem == -MNU_ALPHA_OMEGA &&
             alphaCase == AC_LOWER) {
    labelName = indexOfItems[MNU_alpha_omega].itemSoftmenuName;
  } else if (softmenu[menu].menuItem == -MNU_ALPHAINTL &&
             alphaCase == AC_UPPER) {
    labelName = indexOfItems[MNU_ALPHAINTL].itemSoftmenuName;
  } else if (softmenu[menu].menuItem == -MNU_ALPHAINTL &&
             alphaCase == AC_LOWER) {
    labelName = indexOfItems[MNU_ALPHAintl].itemSoftmenuName;
  } else {
    labelName = indexOfItems[-softmenu[menu].menuItem].itemSoftmenuName;
  }

  snprintf(label, labelSize, "%s", labelName ? labelName : "");
}

static void resolveSoftkeyLabel(int16_t fnKeyIndex, char *label,
                                size_t labelSize, bool_t *enabled) {
  label[0] = 0;
  *enabled = false;

  if (fnKeyIndex < 1 || fnKeyIndex > 6) {
    return;
  }

  int16_t softmenuId = softmenuStack[0].softmenuId;
  int16_t numberOfItems = getCurrentSoftmenuItemCount(softmenuId);
  int16_t firstItem = softmenuStack[0].firstItem;
  int16_t visibleRowOffset = getVisibleSoftkeyRowOffset() * 6;
  int16_t index = firstItem + visibleRowOffset + (fnKeyIndex - 1);

  if (softmenuId < 0 || numberOfItems <= 0 || index < 0 || index >= numberOfItems) {
    return;
  }

  if (softmenuId < NUMBER_OF_DYNAMIC_SOFTMENUS) {
    if (!dynamicSoftmenu[softmenuId].menuContent) {
      return;
    }

    char *labelName =
        (char *)getNthString(dynamicSoftmenu[softmenuId].menuContent, index);
    if (!labelName || labelName[0] == 0) {
      return;
    }

    snprintf(label, labelSize, "%s", labelName);
    *enabled = true;
    return;
  }

  if (!softmenu[softmenuId].softkeyItem) {
    return;
  }

  int16_t item = softmenu[softmenuId].softkeyItem[index];
  if (item == 0) {
    return;
  }

  if (item < 0) {
    fillStaticSoftkeyMenuLabel(item, label, labelSize);
  } else {
    videoMode_t videoMode = vmNormal;
    int8_t showCb = NOVAL;
    int16_t showValue = NOVAL;
    char showText[16] = {0};
    char itemName[32] = {0};
    changeSoftKey(softmenu[softmenuId].menuItem, item, itemName, &videoMode,
                  &showCb, &showValue, showText);
    snprintf(label, labelSize, "%s", itemName);
  }

  *enabled = (label[0] != 0) && !itemNotAvail(item);
}

static void fillKeyboardState(jint *fill);

static void fillKeypadMeta(jint *fill) {
  memset(fill, 0, sizeof(jint) * KEYPAD_META_LENGTH);
  fillKeyboardState(fill);

  int16_t softmenuId = softmenuStack[0].softmenuId;
  int16_t softmenuItemCount = getCurrentSoftmenuItemCount(softmenuId);
  int16_t softmenuFirstItem = softmenuStack[0].firstItem;
  int16_t visibleRowOffset = getVisibleSoftkeyRowOffset();

  fill[KEYPAD_META_SOFTMENU_ID] = softmenuId;
  fill[KEYPAD_META_SOFTMENU_FIRST_ITEM] = softmenuFirstItem;
  fill[KEYPAD_META_SOFTMENU_ITEM_COUNT] = softmenuItemCount;
  fill[KEYPAD_META_SOFTMENU_VISIBLE_ROW] = visibleRowOffset;
  fill[KEYPAD_META_SOFTMENU_PAGE] = softmenuFirstItem / 6;
  fill[KEYPAD_META_SOFTMENU_PAGE_COUNT] =
      softmenuItemCount > 0 ? ((softmenuItemCount + 5) / 6) : 0;
  fill[KEYPAD_META_SOFTMENU_HAS_PREVIOUS] = softmenuFirstItem > 0;
  fill[KEYPAD_META_SOFTMENU_HAS_NEXT] =
      (softmenuFirstItem + 18) < softmenuItemCount;

  for (int keyIndex = 0; keyIndex < 37; keyIndex++) {
    fill[KEYPAD_META_KEY_ENABLED_OFFSET + keyIndex] = 1;
  }

  for (int fnKeyIndex = 1; fnKeyIndex <= 6; fnKeyIndex++) {
    char label[64] = {0};
    bool_t enabled = false;
    resolveSoftkeyLabel(fnKeyIndex, label, sizeof(label), &enabled);
    fill[KEYPAD_META_KEY_ENABLED_OFFSET + 36 + fnKeyIndex] = enabled;
  }
}

static void setKeypadLabelElement(JNIEnv *env, jobjectArray labels, int keyCode,
                                  int labelType, const char *name) {
  char utf8[128];
  encodeUtf8Label(name, utf8, sizeof(utf8));
  jstring value = (*env)->NewStringUTF(env, utf8);
  int index = (keyCode - 1) * KEYPAD_LABELS_PER_KEY + labelType;
  (*env)->SetObjectArrayElement(env, labels, index, value);
  (*env)->DeleteLocalRef(env, value);
}

static void fillKeyboardState(jint *fill) {
  fill[0] = (jint)shiftF;
  fill[1] = (jint)shiftG;
  fill[2] = (jint)calcMode;
  fill[3] = (jint)isUserKeyboardEnabled();
  fill[4] = (jint)isAlphaKeyboardActive();
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
  if (keyCode < 1 || keyCode > 37) {
    pthread_mutex_unlock(&screenMutex);
    return (*env)->NewStringUTF(env, "");
  }

  bool_t alphaOn = isAlphaKeyboardActive();
  const calcKey_t *keys = getVisibleKeyTable(isDynamic);
  const calcKey_t *key = &keys[keyCode - 1];
  const char *name = resolveMainKeyLabel(key, keyCode, type, isDynamic, alphaOn);
  char utf8[128];
  encodeUtf8Label(name, utf8, sizeof(utf8));
  pthread_mutex_unlock(&screenMutex);
  return (*env)->NewStringUTF(env, utf8);
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
  char label[64] = {0};
  bool_t enabled = false;
  resolveSoftkeyLabel(fnKeyIndex, label, sizeof(label), &enabled);
  char utf8[128];
  encodeUtf8Label(label, utf8, sizeof(utf8));
  pthread_mutex_unlock(&screenMutex);
  return (*env)->NewStringUTF(env, utf8);
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

JNIEXPORT jintArray JNICALL
Java_com_example_r47_MainActivity_getKeypadMetaNative(JNIEnv *env,
                                                             jobject thiz) {
  (void)thiz;
  jintArray result = (*env)->NewIntArray(env, KEYPAD_META_LENGTH);
  if (result == NULL) {
    return NULL;
  }

  jint fill[KEYPAD_META_LENGTH];
  memset(fill, 0, sizeof(fill));
  if (ram) {
    pthread_mutex_lock(&screenMutex);
    fillKeypadMeta(fill);
    pthread_mutex_unlock(&screenMutex);
  }

  (*env)->SetIntArrayRegion(env, result, 0, KEYPAD_META_LENGTH, fill);
  return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_r47_MainActivity_getKeypadLabelsNative(JNIEnv *env,
                                                               jobject thiz,
                                                               jboolean isDynamic) {
  (void)thiz;

  jclass stringClass = (*env)->FindClass(env, "java/lang/String");
  if (stringClass == NULL) {
    return NULL;
  }

  jstring empty = (*env)->NewStringUTF(env, "");
  jobjectArray result = (*env)->NewObjectArray(
      env, KEYPAD_KEY_COUNT * KEYPAD_LABELS_PER_KEY, stringClass, empty);
  (*env)->DeleteLocalRef(env, stringClass);
  if (result == NULL) {
    (*env)->DeleteLocalRef(env, empty);
    return NULL;
  }

  if (!ram) {
    (*env)->DeleteLocalRef(env, empty);
    return result;
  }

  pthread_mutex_lock(&screenMutex);
  bool_t alphaOn = isAlphaKeyboardActive();
  const calcKey_t *keys = getVisibleKeyTable(isDynamic);

  for (int keyCode = 1; keyCode <= 37; keyCode++) {
    const calcKey_t *key = &keys[keyCode - 1];
    for (int labelType = 0; labelType < KEYPAD_LABELS_PER_KEY; labelType++) {
      const char *name =
          resolveMainKeyLabel(key, keyCode, labelType, isDynamic, alphaOn);
      setKeypadLabelElement(env, result, keyCode, labelType, name);
    }
  }

  for (int fnKeyIndex = 1; fnKeyIndex <= 6; fnKeyIndex++) {
    char label[64] = {0};
    bool_t enabled = false;
    resolveSoftkeyLabel(fnKeyIndex, label, sizeof(label), &enabled);
    setKeypadLabelElement(env, result, 37 + fnKeyIndex, KEYPAD_LABEL_PRIMARY,
                          label);
  }

  pthread_mutex_unlock(&screenMutex);
  (*env)->DeleteLocalRef(env, empty);
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
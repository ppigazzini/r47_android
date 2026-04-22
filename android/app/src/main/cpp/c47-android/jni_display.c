#include "jni_bridge.h"

#include "keyboard.h"

#include <stdlib.h>
#include <string.h>

enum {
  KEYPAD_LABEL_PRIMARY = 0,
  KEYPAD_LABEL_F = 1,
  KEYPAD_LABEL_G = 2,
  KEYPAD_LABEL_LETTER = 3,
  KEYPAD_LABEL_AUX = 4,
  KEYPAD_LABELS_PER_KEY = 5,
  KEYPAD_KEY_COUNT = 43,
  KEYPAD_SCENE_CONTRACT_VERSION = 5,
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
    KEYPAD_META_CONTRACT_VERSION =
      KEYPAD_META_KEY_ENABLED_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_SOFTMENU_DOTTED_ROW = KEYPAD_META_CONTRACT_VERSION + 1,
    KEYPAD_META_FN_PREVIEW_ACTIVE = KEYPAD_META_SOFTMENU_DOTTED_ROW + 1,
    KEYPAD_META_FN_PREVIEW_KEY = KEYPAD_META_FN_PREVIEW_ACTIVE + 1,
    KEYPAD_META_FN_PREVIEW_ROW = KEYPAD_META_FN_PREVIEW_KEY + 1,
    KEYPAD_META_FN_PREVIEW_STATE = KEYPAD_META_FN_PREVIEW_ROW + 1,
    KEYPAD_META_FN_PREVIEW_TIMEOUT_ACTIVE = KEYPAD_META_FN_PREVIEW_STATE + 1,
    KEYPAD_META_FN_PREVIEW_RELEASE_EXEC =
      KEYPAD_META_FN_PREVIEW_TIMEOUT_ACTIVE + 1,
    KEYPAD_META_FN_PREVIEW_NOP_OR_EXECUTED =
      KEYPAD_META_FN_PREVIEW_RELEASE_EXEC + 1,
    KEYPAD_META_STYLE_ROLE_OFFSET =
      KEYPAD_META_FN_PREVIEW_NOP_OR_EXECUTED + 1,
    KEYPAD_META_LABEL_ROLE_OFFSET =
      KEYPAD_META_STYLE_ROLE_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_LAYOUT_CLASS_OFFSET =
      KEYPAD_META_LABEL_ROLE_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_SCENE_FLAGS_OFFSET =
      KEYPAD_META_LAYOUT_CLASS_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_OVERLAY_STATE_OFFSET =
      KEYPAD_META_SCENE_FLAGS_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_SHOW_VALUE_OFFSET =
      KEYPAD_META_OVERLAY_STATE_OFFSET + KEYPAD_KEY_COUNT,
    KEYPAD_META_LENGTH = KEYPAD_META_SHOW_VALUE_OFFSET + KEYPAD_KEY_COUNT,

    KEYPAD_STYLE_DEFAULT = 0,
    KEYPAD_STYLE_SOFTKEY = 1,
    KEYPAD_STYLE_SHIFT_F = 2,
    KEYPAD_STYLE_SHIFT_G = 3,
    KEYPAD_STYLE_SHIFT_FG = 4,
    KEYPAD_STYLE_NUMERIC = 5,
    KEYPAD_STYLE_ALPHA = 6,

    KEYPAD_TEXT_ROLE_NONE = 0,
    KEYPAD_TEXT_ROLE_PRIMARY = 1,
    KEYPAD_TEXT_ROLE_F = 2,
    KEYPAD_TEXT_ROLE_G = 3,
    KEYPAD_TEXT_ROLE_LETTER = 4,
    KEYPAD_TEXT_ROLE_F_UNDERLINE = 5,
    KEYPAD_TEXT_ROLE_G_UNDERLINE = 6,
    KEYPAD_TEXT_ROLE_LONGPRESS = 7,
    KEYPAD_TEXT_ROLE_SOFTKEY = 8,

    KEYPAD_LAYOUT_CLASS_DEFAULT = 0,
    KEYPAD_LAYOUT_CLASS_PACKED = 1,
    KEYPAD_LAYOUT_CLASS_OFFSET = 2,
    KEYPAD_LAYOUT_CLASS_EDGE = 3,
    KEYPAD_LAYOUT_CLASS_ALPHA = 4,
    KEYPAD_LAYOUT_CLASS_TAM = 5,
    KEYPAD_LAYOUT_CLASS_STATIC_SINGLE = 6,
    KEYPAD_LAYOUT_CLASS_SOFTKEY = 7,

    KEYPAD_SCENE_FLAG_SOFTKEY = 1 << 0,
    KEYPAD_SCENE_FLAG_REVERSE_VIDEO = 1 << 1,
    KEYPAD_SCENE_FLAG_TOP_LINE = 1 << 2,
    KEYPAD_SCENE_FLAG_BOTTOM_LINE = 1 << 3,
    KEYPAD_SCENE_FLAG_SHOW_CB = 1 << 4,
    KEYPAD_SCENE_FLAG_SHOW_TEXT = 1 << 5,
    KEYPAD_SCENE_FLAG_SHOW_VALUE = 1 << 6,
    KEYPAD_SCENE_FLAG_STRIKE_OUT = 1 << 7,
    KEYPAD_SCENE_FLAG_STRIKE_THROUGH = 1 << 8,
    KEYPAD_SCENE_FLAG_MENU = 1 << 9,
    KEYPAD_SCENE_FLAG_PREVIEW_TARGET = 1 << 10,
    KEYPAD_SCENE_FLAG_DOTTED_ROW = 1 << 11,
};

extern void changeSoftKey(int16_t menuNr, int16_t itemNr, char *itemName,
                          videoMode_t *vm, int8_t *showCb,
                          int16_t *showValue, char *showText);
extern bool_t itemNotAvail(int16_t itemNr);
  extern void itemToBeCoded(uint16_t unusedButMandatoryParameter);
  extern bool_t savedspace(int16_t itemNr);

static const char *resolveMainKeyLabel(const calcKey_t *key, jint keyCode,
                                       jint type, jboolean isDynamic,
                                       bool_t alphaOn);
static int16_t findSoftmenuIndexByItem(int16_t item);
static void fillStaticSoftkeyMenuLabel(int16_t item, char *label,
                                       size_t labelSize);

  typedef struct {
    char primaryLabel[64];
    char auxLabel[32];
    bool_t enabled;
    jint sceneFlags;
    jint overlayState;
    jint showValue;
  } keypadSoftkeyScene_t;

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

static jint packLabelRole(jint slot, jint role) {
  return role << (slot * 4);
}

static int keypadMetaIndex(int offset, int keyCode) {
  return offset + keyCode - 1;
}

static int16_t resolveVisibleMainStyleItem(const calcKey_t *key, bool_t alphaOn) {
  if (alphaOn) {
    return key->keyLblAim;
  }
  if (tam.mode) {
    return key->primaryTam;
  }
  return key->primary;
}

static bool_t isNumericStyleKey(const calcKey_t *key, int16_t visibleItem,
                                bool_t alphaOn) {
  if (alphaOn || tam.alpha) {
    return false;
  }

  if ((visibleItem >= ITM_0 && visibleItem <= ITM_9) || visibleItem == ITM_PERIOD) {
    return true;
  }

  return key->keyId == 55 || key->keyId == 65 || key->keyId == 75 ||
         key->keyId == 85;
}

static jint resolveMainStyleRole(const calcKey_t *key, bool_t alphaOn) {
  int16_t visibleItem = resolveVisibleMainStyleItem(key, alphaOn);

  if (visibleItem == ITM_SHIFTf) {
    return KEYPAD_STYLE_SHIFT_F;
  }
  if (visibleItem == ITM_SHIFTg) {
    return KEYPAD_STYLE_SHIFT_G;
  }
  if (visibleItem == KEY_fg) {
    return KEYPAD_STYLE_SHIFT_FG;
  }
  if (!alphaOn && visibleItem == ITM_AIM) {
    return KEYPAD_STYLE_ALPHA;
  }
  if (isNumericStyleKey(key, visibleItem, alphaOn)) {
    return KEYPAD_STYLE_NUMERIC;
  }
  return KEYPAD_STYLE_DEFAULT;
}

static bool_t usesLongpressAccentF(const calcKey_t *key, bool_t alphaOn) {
  int16_t visiblePrimary = alphaOn ? key->primaryAim : key->primary;
  return isR47FAM && (visiblePrimary == ITM_SHIFTf || visiblePrimary == KEY_fg);
}

static bool_t usesLongpressAccentG(const calcKey_t *key, bool_t alphaOn) {
  int16_t visiblePrimary = alphaOn ? key->primaryAim : key->primary;
  return isR47FAM && (visiblePrimary == ITM_SHIFTg || visiblePrimary == KEY_fg);
}

static jint resolveMainLabelRoles(const calcKey_t *key, jint keyCode,
                                  jboolean isDynamic, bool_t alphaOn) {
  jint roles = 0;

  const char *primaryLabel =
      resolveMainKeyLabel(key, keyCode, KEYPAD_LABEL_PRIMARY, isDynamic, alphaOn);
  if (primaryLabel[0] != 0) {
    roles |= packLabelRole(KEYPAD_LABEL_PRIMARY, KEYPAD_TEXT_ROLE_PRIMARY);
  }

  const char *fLabel =
      resolveMainKeyLabel(key, keyCode, KEYPAD_LABEL_F, isDynamic, alphaOn);
  if (fLabel[0] != 0) {
    jint role = KEYPAD_TEXT_ROLE_F;
    if (usesLongpressAccentF(key, alphaOn)) {
      role = KEYPAD_TEXT_ROLE_LONGPRESS;
    } else if ((alphaOn && key->primary < 0) ||
               (!alphaOn && !tam.mode && key->fShifted < 0)) {
      role = KEYPAD_TEXT_ROLE_F_UNDERLINE;
    }
    roles |= packLabelRole(KEYPAD_LABEL_F, role);
  }

  const char *gLabel =
      resolveMainKeyLabel(key, keyCode, KEYPAD_LABEL_G, isDynamic, alphaOn);
  if (gLabel[0] != 0) {
    jint role = KEYPAD_TEXT_ROLE_G;
    if (usesLongpressAccentG(key, alphaOn)) {
      role = KEYPAD_TEXT_ROLE_LONGPRESS;
    } else if ((alphaOn && key->gShiftedAim < 0) ||
               (!alphaOn && !tam.mode && key->gShifted < 0)) {
      role = KEYPAD_TEXT_ROLE_G_UNDERLINE;
    }
    roles |= packLabelRole(KEYPAD_LABEL_G, role);
  }

  const char *letterLabel =
      resolveMainKeyLabel(key, keyCode, KEYPAD_LABEL_LETTER, isDynamic, alphaOn);
  if (letterLabel[0] != 0) {
    roles |= packLabelRole(KEYPAD_LABEL_LETTER, KEYPAD_TEXT_ROLE_LETTER);
  }

  return roles;
}

static jint resolveMainLayoutClass(jint keyCode, bool_t alphaOn) {
  if (keyCode >= 38) {
    return KEYPAD_LAYOUT_CLASS_SOFTKEY;
  }
  if (tam.mode && !alphaOn) {
    return KEYPAD_LAYOUT_CLASS_TAM;
  }
  if (alphaOn) {
    return KEYPAD_LAYOUT_CLASS_ALPHA;
  }
  if (keyCode == 11 || keyCode == 12) {
    return KEYPAD_LAYOUT_CLASS_STATIC_SINGLE;
  }

  switch (keyCode) {
  case 1:
  case 2:
  case 3:
  case 4:
  case 5:
  case 6:
  case 7:
  case 8:
  case 9:
  case 13:
  case 18:
  case 37:
    return KEYPAD_LAYOUT_CLASS_PACKED;
  case 10:
  case 14:
  case 27:
  case 32:
  case 34:
  case 35:
    return KEYPAD_LAYOUT_CLASS_OFFSET;
  case 20:
  case 21:
  case 22:
  case 24:
  case 25:
  case 26:
  case 29:
  case 30:
  case 31:
  case 36:
    return KEYPAD_LAYOUT_CLASS_EDGE;
  default:
    return KEYPAD_LAYOUT_CLASS_DEFAULT;
  }
}

static bool_t isSoftkeyStrikeOut(int16_t itemNr) {
  if (itemNr == -MNU_HOME || itemNr == -MNU_PFN) {
    return false;
  }

  if (itemNr > 0) {
    return indexOfItems[itemNr % 10000].func == itemToBeCoded || savedspace(itemNr);
  }

  if (itemNr < 0) {
    int16_t menu = findSoftmenuIndexByItem(itemNr % 10000);
    if (menu >= 0 && menu >= NUMBER_OF_DYNAMIC_SOFTMENUS) {
      return (softmenu[menu].numItems == 0) || savedspace(itemNr);
    }
  }

  return false;
}

static int16_t getSoftmenuDottedRow(int16_t softmenuId, int16_t itemCount,
                                    int16_t firstItem) {
  if (softmenuId < 0) {
    return -1;
  }

  if (softmenu[softmenuId].menuItem == -MNU_EQN) {
    return (numberOfFormulae >= 2) ? 2 : -1;
  }

  if (itemCount <= 18) {
    return -1;
  }

  int16_t dottedRow =
      min(3, (itemCount + modulo(firstItem - itemCount, 6)) / 6 - firstItem / 6) - 1;

  if (softmenuId >= NUMBER_OF_DYNAMIC_SOFTMENUS) {
    for (int pass = 0; pass < 3 && dottedRow >= 0; pass++) {
      int16_t item = 6 * (firstItem / 6 + dottedRow);
      const int16_t *softkeyItem = softmenu[softmenuId].softkeyItem + item;
      if (softkeyItem[0] == 0 && softkeyItem[1] == 0 && softkeyItem[2] == 0 &&
          softkeyItem[3] == 0 && softkeyItem[4] == 0 && softkeyItem[5] == 0) {
        dottedRow--;
      }
    }
  }

  return dottedRow >= 0 && dottedRow <= 2 ? dottedRow : -1;
}

static int16_t getFunctionPreviewKeyCode(void) {
  if (!FN_timeouts_in_progress || FN_key_pressed < 38 || FN_key_pressed > 43) {
    return 0;
  }
  return FN_key_pressed;
}

static int16_t getFunctionPreviewRow(void) {
  if (!FN_timeouts_in_progress || FN_key_pressed < 38 || FN_key_pressed > 43) {
    return -1;
  }
  if (shiftF) {
    return 1;
  }
  if (shiftG) {
    return 2;
  }
  return 0;
}

static void clearSoftkeyScene(keypadSoftkeyScene_t *scene) {
  memset(scene, 0, sizeof(*scene));
  scene->enabled = false;
  scene->overlayState = NOVAL;
  scene->showValue = NOVAL;
  scene->sceneFlags = KEYPAD_SCENE_FLAG_SOFTKEY;
}

static void resolveSoftkeyScene(int16_t fnKeyIndex, keypadSoftkeyScene_t *scene) {
  clearSoftkeyScene(scene);

  if (fnKeyIndex < 1 || fnKeyIndex > 6) {
    return;
  }

  int16_t softmenuId = softmenuStack[0].softmenuId;
  int16_t numberOfItems = getCurrentSoftmenuItemCount(softmenuId);
  int16_t firstItem = softmenuStack[0].firstItem;
  int16_t visibleRowOffset = getVisibleSoftkeyRowOffset() * 6;
  int16_t absoluteIndex = firstItem + visibleRowOffset + (fnKeyIndex - 1);
  int16_t visibleIndex = visibleRowOffset + (fnKeyIndex - 1);

  if (softmenuId < 0 || numberOfItems <= 0 || absoluteIndex < 0 ||
      absoluteIndex >= numberOfItems) {
    return;
  }

  int16_t sceneItem = 0;

  if (softmenuId < NUMBER_OF_DYNAMIC_SOFTMENUS) {
    if (!dynamicSoftmenu[softmenuId].menuContent) {
      return;
    }

    char *labelName =
        (char *)getNthString(dynamicSoftmenu[softmenuId].menuContent, absoluteIndex);
    if (!labelName || labelName[0] == 0) {
      return;
    }

    snprintf(scene->primaryLabel, sizeof(scene->primaryLabel), "%s", labelName);
    scene->enabled = true;
    scene->sceneFlags |= KEYPAD_SCENE_FLAG_TOP_LINE | KEYPAD_SCENE_FLAG_BOTTOM_LINE;

    videoMode_t videoMode = vmNormal;
    int8_t showCb = NOVAL;
    int16_t showValue = NOVAL;
    char showText[16] = {0};
    char itemName[32] = {0};

    switch (-softmenu[softmenuId].menuItem) {
    case MNU_MENU:
    case MNU_MENUS:
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO |
                           KEYPAD_SCENE_FLAG_MENU;
      break;
    case MNU_MyMenu:
      sceneItem = userMenuItems[visibleIndex].item;
      if (sceneItem < 0) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO |
                             KEYPAD_SCENE_FLAG_MENU;
      } else if (userMenuItems[visibleIndex].argumentName[0] == 0) {
        changeSoftKey(softmenu[softmenuId].menuItem, sceneItem, itemName, &videoMode,
                      &showCb, &showValue, showText);
        snprintf(scene->primaryLabel, sizeof(scene->primaryLabel), "%s", itemName);
      }
      break;
    case MNU_MyAlpha:
      sceneItem = userAlphaItems[visibleIndex].item;
      if (sceneItem < 0) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO |
                             KEYPAD_SCENE_FLAG_MENU;
      }
      break;
    case MNU_DYNAMIC:
      sceneItem = userMenus[currentUserMenu].menuItem[visibleIndex].item;
      if (sceneItem < 0) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO |
                             KEYPAD_SCENE_FLAG_MENU;
      } else if (userMenus[currentUserMenu].menuItem[visibleIndex].argumentName[0] ==
                 0) {
        changeSoftKey(softmenu[softmenuId].menuItem, sceneItem, itemName, &videoMode,
                      &showCb, &showValue, showText);
        snprintf(scene->primaryLabel, sizeof(scene->primaryLabel), "%s", itemName);
      }
      break;
    default:
      break;
    }

    if (videoMode == vmReverse) {
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO;
    }
    if (showCb != NOVAL) {
      scene->overlayState = showCb;
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_CB;
    }
    if (showValue != NOVAL) {
      scene->showValue = showValue;
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_VALUE;
    }
    if (showText[0] != 0) {
      snprintf(scene->auxLabel, sizeof(scene->auxLabel), "%s", showText);
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_TEXT;
    }
  } else {
    if (!softmenu[softmenuId].softkeyItem) {
      return;
    }

    int16_t item = softmenu[softmenuId].softkeyItem[absoluteIndex];
    if (item == 0) {
      return;
    }

    sceneItem = item;
    if (item < 0) {
      fillStaticSoftkeyMenuLabel(item, scene->primaryLabel,
                                 sizeof(scene->primaryLabel));
      scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO |
                           KEYPAD_SCENE_FLAG_MENU |
                           KEYPAD_SCENE_FLAG_TOP_LINE |
                           KEYPAD_SCENE_FLAG_BOTTOM_LINE;
    } else {
      videoMode_t videoMode = vmNormal;
      int8_t showCb = NOVAL;
      int16_t showValue = NOVAL;
      char showText[16] = {0};
      char itemName[32] = {0};

      changeSoftKey(softmenu[softmenuId].menuItem, item, itemName, &videoMode,
                    &showCb, &showValue, showText);
      snprintf(scene->primaryLabel, sizeof(scene->primaryLabel), "%s", itemName);

      if (videoMode == vmReverse) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_REVERSE_VIDEO;
      }
      if ((item / 10000) == 0 || (item / 10000) == 2) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_TOP_LINE;
      }
      if ((item / 10000) == 0 || (item / 10000) == 1) {
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_BOTTOM_LINE;
      }
      if (showCb != NOVAL) {
        scene->overlayState = showCb;
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_CB;
      }
      if (showValue != NOVAL) {
        scene->showValue = showValue;
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_VALUE;
      }
      if (showText[0] != 0) {
        snprintf(scene->auxLabel, sizeof(scene->auxLabel), "%s", showText);
        scene->sceneFlags |= KEYPAD_SCENE_FLAG_SHOW_TEXT;
      }
    }

    scene->enabled = scene->primaryLabel[0] != 0;
  }

  int16_t strikeItem = sceneItem > 0 ? sceneItem % 10000 : sceneItem;
  if (sceneItem != 0 && isSoftkeyStrikeOut(sceneItem)) {
    scene->sceneFlags |= KEYPAD_SCENE_FLAG_STRIKE_OUT;
  }
  if (strikeItem != 0 && itemNotAvail(strikeItem)) {
    scene->sceneFlags |= KEYPAD_SCENE_FLAG_STRIKE_THROUGH;
    scene->enabled = false;
  }

  if (scene->primaryLabel[0] != 0 && scene->enabled == false && strikeItem <= 0) {
    scene->enabled = true;
  }
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

  if (tam.mode) {
    return type == KEYPAD_LABEL_PRIMARY ? key->primaryTam : 0;
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
  if (!alphaOn && !tam.mode && keyCode == 37 && type == KEYPAD_LABEL_LETTER) {
    return "_";
  }

  if (!alphaOn && !tam.mode) {
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
  keypadSoftkeyScene_t scene;
  resolveSoftkeyScene(fnKeyIndex, &scene);
  snprintf(label, labelSize, "%s", scene.primaryLabel);
  *enabled = scene.enabled;
}

static void fillKeyboardState(jint *fill);

static void fillKeypadMeta(jint *fill, jboolean isDynamic) {
  memset(fill, 0, sizeof(jint) * KEYPAD_META_LENGTH);
  fillKeyboardState(fill);

  int16_t softmenuId = softmenuStack[0].softmenuId;
  int16_t softmenuItemCount = getCurrentSoftmenuItemCount(softmenuId);
  int16_t softmenuFirstItem = softmenuStack[0].firstItem;
  int16_t visibleRowOffset = getVisibleSoftkeyRowOffset();
  int16_t dottedRow =
      getSoftmenuDottedRow(softmenuId, softmenuItemCount, softmenuFirstItem);
  int16_t previewKeyCode = getFunctionPreviewKeyCode();
  int16_t previewRow = getFunctionPreviewRow();
  bool_t alphaOn = isAlphaKeyboardActive();
  const calcKey_t *keys = getVisibleKeyTable(isDynamic);

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
  fill[KEYPAD_META_CONTRACT_VERSION] = KEYPAD_SCENE_CONTRACT_VERSION;
  fill[KEYPAD_META_SOFTMENU_DOTTED_ROW] = dottedRow;
  fill[KEYPAD_META_FN_PREVIEW_ACTIVE] = previewKeyCode != 0;
  fill[KEYPAD_META_FN_PREVIEW_KEY] = previewKeyCode;
  fill[KEYPAD_META_FN_PREVIEW_ROW] = previewRow;
  fill[KEYPAD_META_FN_PREVIEW_STATE] = FN_state;
  fill[KEYPAD_META_FN_PREVIEW_TIMEOUT_ACTIVE] = FN_timeouts_in_progress;
  fill[KEYPAD_META_FN_PREVIEW_RELEASE_EXEC] = FN_timed_out_to_RELEASE_EXEC;
  fill[KEYPAD_META_FN_PREVIEW_NOP_OR_EXECUTED] =
      FN_timed_out_to_NOP_or_Executed;

  for (int keyCode = 1; keyCode <= 37; keyCode++) {
    const calcKey_t *key = &keys[keyCode - 1];
    fill[keypadMetaIndex(KEYPAD_META_KEY_ENABLED_OFFSET, keyCode)] = 1;
    fill[keypadMetaIndex(KEYPAD_META_STYLE_ROLE_OFFSET, keyCode)] =
        resolveMainStyleRole(key, alphaOn);
    fill[keypadMetaIndex(KEYPAD_META_LABEL_ROLE_OFFSET, keyCode)] =
        resolveMainLabelRoles(key, keyCode, false, alphaOn);
    fill[keypadMetaIndex(KEYPAD_META_LAYOUT_CLASS_OFFSET, keyCode)] =
        resolveMainLayoutClass(keyCode, alphaOn);
    fill[keypadMetaIndex(KEYPAD_META_OVERLAY_STATE_OFFSET, keyCode)] = NOVAL;
    fill[keypadMetaIndex(KEYPAD_META_SHOW_VALUE_OFFSET, keyCode)] = NOVAL;
  }

  for (int fnKeyIndex = 1; fnKeyIndex <= 6; fnKeyIndex++) {
    int keyCode = 37 + fnKeyIndex;
    keypadSoftkeyScene_t scene;
    resolveSoftkeyScene(fnKeyIndex, &scene);

    jint sceneFlags = scene.sceneFlags;
    if (previewKeyCode == keyCode) {
      sceneFlags |= KEYPAD_SCENE_FLAG_PREVIEW_TARGET;
    }
    if (dottedRow >= 0 && dottedRow == visibleRowOffset) {
      sceneFlags |= KEYPAD_SCENE_FLAG_DOTTED_ROW;
    }

    fill[keypadMetaIndex(KEYPAD_META_KEY_ENABLED_OFFSET, keyCode)] = scene.enabled;
    fill[keypadMetaIndex(KEYPAD_META_STYLE_ROLE_OFFSET, keyCode)] =
        KEYPAD_STYLE_SOFTKEY;
    fill[keypadMetaIndex(KEYPAD_META_LABEL_ROLE_OFFSET, keyCode)] =
        packLabelRole(KEYPAD_LABEL_PRIMARY, KEYPAD_TEXT_ROLE_SOFTKEY) |
        (scene.auxLabel[0] != 0
             ? packLabelRole(KEYPAD_LABEL_AUX, KEYPAD_TEXT_ROLE_SOFTKEY)
             : 0);
    fill[keypadMetaIndex(KEYPAD_META_LAYOUT_CLASS_OFFSET, keyCode)] =
        KEYPAD_LAYOUT_CLASS_SOFTKEY;
    fill[keypadMetaIndex(KEYPAD_META_SCENE_FLAGS_OFFSET, keyCode)] = sceneFlags;
    fill[keypadMetaIndex(KEYPAD_META_OVERLAY_STATE_OFFSET, keyCode)] =
        scene.overlayState;
    fill[keypadMetaIndex(KEYPAD_META_SHOW_VALUE_OFFSET, keyCode)] =
        scene.showValue;
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
                                                             jobject thiz,
                                                             jboolean isDynamic) {
  (void)thiz;
  jintArray result = (*env)->NewIntArray(env, KEYPAD_META_LENGTH);
  if (result == NULL) {
    return NULL;
  }

  jint fill[KEYPAD_META_LENGTH];
  memset(fill, 0, sizeof(fill));
  if (ram) {
    pthread_mutex_lock(&screenMutex);
    fillKeypadMeta(fill, isDynamic);
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
    keypadSoftkeyScene_t scene;
    resolveSoftkeyScene(fnKeyIndex, &scene);
    setKeypadLabelElement(env, result, 37 + fnKeyIndex, KEYPAD_LABEL_PRIMARY,
                          scene.primaryLabel);
    setKeypadLabelElement(env, result, 37 + fnKeyIndex, KEYPAD_LABEL_AUX,
                          scene.auxLabel);
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
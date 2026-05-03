#include "jni_bridge.h"

#include <unistd.h>

JavaVM *g_jvm = NULL;
jobject g_mainActivityObj = NULL;
jmethodID g_requestFileId = NULL;
jmethodID g_playToneId = NULL;
jmethodID g_stopToneId = NULL;
jmethodID g_processCoreTasksId = NULL;

pthread_mutex_t fileMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t fileCond = PTHREAD_COND_INITIALIZER;
pthread_mutex_t screenMutex;
int fileDescriptor = -1;
bool fileReady = false;
bool fileCancelled = false;
bool isCoreBlockingForIo = false;

int16_t debugWindow = 0;
int16_t screenStride = 400;
bool_t screenChange = FALSE;
int currentBezel = 0;
calcKeyboard_t calcKeyboard[43];
gboolean ui_is_active = FALSE;

uint32_t nextTimerRefresh = 0;
uint32_t nextScreenRefresh = 0;

GdkEvent pressEvent;
GdkEvent releaseEvent;

uint16_t getBeepVolume(void) { return 80; }

void onUIActivity(void) { ui_is_active = TRUE; }

gint64 g_get_monotonic_time(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (gint64)ts.tv_sec * 1000000LL + ts.tv_nsec / 1000LL;
}

gint64 g_get_real_time(void) {
  struct timespec ts;
  clock_gettime(CLOCK_REALTIME, &ts);
  return (gint64)ts.tv_sec * 1000000LL + ts.tv_nsec / 1000LL;
}

uint32_t sys_current_ms(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

void _Buzz(uint32_t frequency, uint32_t ms_delay) {
  if (!g_mainActivityObj || !g_jvm || !g_playToneId) {
    return;
  }

  if (frequency > 0) {
    jni_env_scope_t scope;
    if (!jni_acquire_env(&scope, "_Buzz")) {
      return;
    }

    (*scope.env)->CallVoidMethod(scope.env, g_mainActivityObj, g_playToneId,
                           (jint)frequency, (jint)ms_delay);
    jni_check_and_clear_exception(scope.env, "_Buzz CallVoidMethod(playTone)");
    jni_release_env(&scope, "_Buzz");
  }

  usleep((ms_delay + 10) * 1000);
}

void audioTone(uint32_t frequency) { _Buzz(frequency, 200); }

void processCoreTasksNative(void) {
  if (!g_mainActivityObj || !g_jvm || !g_processCoreTasksId) {
    return;
  }

  jni_env_scope_t scope;
  if (!jni_acquire_env(&scope, "processCoreTasksNative")) {
    return;
  }

  (*scope.env)->CallVoidMethod(scope.env, g_mainActivityObj,
                               g_processCoreTasksId);
  jni_check_and_clear_exception(scope.env,
                                "processCoreTasksNative CallVoidMethod");
  jni_release_env(&scope, "processCoreTasksNative");
}

void yieldToAndroidWithMs(int ms) {
  if (!ram) {
    return;
  }

  refreshLcd(NULL);
  lcd_refresh();

  int lockCount = 0;
  while (pthread_mutex_unlock(&screenMutex) == 0) {
    lockCount++;
  }

  processCoreTasksNative();
  if (ms > 0) {
    usleep(ms * 1000);
  } else {
    usleep(1000);
  }

  while (lockCount > 0) {
    pthread_mutex_lock(&screenMutex);
    lockCount--;
  }
}

void yieldToAndroid(void) { yieldToAndroidWithMs(1); }

void fnSetVolume(uint16_t v) { (void)v; }
void fnGetVolume(uint16_t v) { (void)v; }
void fnVolumeUp(uint16_t v) { (void)v; }
void fnVolumeDown(uint16_t v) { (void)v; }
void fnBuzz(uint16_t v) { (void)v; }
void fnPlay(uint16_t v) { (void)v; }
void squeak(void) { _Buzz(1000, 10); }
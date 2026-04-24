#!/bin/bash

# =============================================================================
# R47 Android Build Script
# =============================================================================

# --- 1. Setup Variables ---
# Ensure Gradle is in path if installed via SDKMAN, but don't hardcode user home
if [ -d "$HOME/.sdkman/candidates/gradle/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/gradle/current/bin:$PATH"
fi

resolve_path() {
    local target="$1"
    local dir

    while [ -L "$target" ]; do
        dir=$(cd -P "$(dirname "$target")" && pwd)
        target=$(readlink "$target")
        case "$target" in
            /*) ;;
            *) target="$dir/$target" ;;
        esac
    done

    dir=$(cd -P "$(dirname "$target")" && pwd)
    printf '%s/%s\n' "$dir" "$(basename "$target")"
}

if [ -n "$JAVA_HOME" ] && [ ! -d "$JAVA_HOME" ]; then
    unset JAVA_HOME
fi

if [ -z "$JAVA_HOME" ]; then
    if [ "$(uname -s)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
        JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home 2>/dev/null || true)
        if [ -d "$JAVA_HOME_CANDIDATE" ]; then
            export JAVA_HOME="$JAVA_HOME_CANDIDATE"
            echo "Detected JAVA_HOME from /usr/libexec/java_home: $JAVA_HOME"
        fi
    fi
fi

if [ -z "$JAVA_HOME" ]; then
    JAVA_BIN=$(command -v java 2>/dev/null || true)
    if [ -n "$JAVA_BIN" ]; then
        JAVA_HOME_CANDIDATE=$(dirname "$(dirname "$(resolve_path "$JAVA_BIN")")")
        if [ -d "$JAVA_HOME_CANDIDATE" ]; then
            export JAVA_HOME="$JAVA_HOME_CANDIDATE"
            echo "Detected JAVA_HOME from PATH: $JAVA_HOME"
        fi
    fi
fi

if [ -z "$JAVA_HOME" ] && [ -d /usr/lib/jvm ]; then
    for jvm in /usr/lib/jvm/default-java /usr/lib/jvm/*; do
        if [ -d "$jvm" ] && [ -x "$jvm/bin/java" ]; then
            export JAVA_HOME="$jvm"
            echo "Detected JAVA_HOME from /usr/lib/jvm: $JAVA_HOME"
            break
        fi
    done
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
else
    echo "WARNING: No local Java installation detected. Gradle build requires JDK 17+."
fi

normalize_job_count() {
    case "$1" in
        ''|*[!0-9]*|0)
            return 1
            ;;
        *)
            printf '%s\n' "$1"
            ;;
    esac
}

detect_job_count() {
    local detected_jobs=""

    if command -v nproc >/dev/null 2>&1; then
        detected_jobs=$(nproc 2>/dev/null || true)
    fi

    if [ -z "$detected_jobs" ] && command -v getconf >/dev/null 2>&1; then
        detected_jobs=$(getconf _NPROCESSORS_ONLN 2>/dev/null || true)
    fi

    if ! detected_jobs=$(normalize_job_count "$detected_jobs"); then
        detected_jobs=1
    fi

    printf '%s\n' "$detected_jobs"
}

R47_BUILD_JOBS_INPUT=${R47_BUILD_JOBS-}
if ! R47_BUILD_JOBS=$(normalize_job_count "$R47_BUILD_JOBS_INPUT"); then
    CMAKE_BUILD_JOBS_INPUT=${CMAKE_BUILD_PARALLEL_LEVEL-}
    if ! R47_BUILD_JOBS=$(normalize_job_count "$CMAKE_BUILD_JOBS_INPUT"); then
        R47_BUILD_JOBS=$(detect_job_count)
    fi
fi

export R47_BUILD_JOBS
export CMAKE_BUILD_PARALLEL_LEVEL="$R47_BUILD_JOBS"

PROJECT_ROOT="$(pwd)"
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}

# --- NDK Version Selection ---
# 1. Check for Environment Override
# 2. Check for Gradle Property in android/gradle.properties
# 3. Extract Default from build.gradle
# 4. Fallback to latest installed
IF_NDK_VERSION=${R47_NDK_VERSION}
if [ -z "$IF_NDK_VERSION" ]; then
    IF_NDK_VERSION=$(grep "r47.ndkVersion=" "$PROJECT_ROOT/android/gradle.properties" 2>/dev/null | cut -d'=' -f2)
fi
if [ -z "$IF_NDK_VERSION" ]; then
    IF_NDK_VERSION=$(grep "ndkVersion" "$PROJECT_ROOT/android/app/build.gradle" | grep -o '".*"' | sed 's/"//g')
fi

if [ -n "$IF_NDK_VERSION" ] && [ -d "$ANDROID_SDK_ROOT/ndk/$IF_NDK_VERSION" ]; then
    echo "Using detected NDK version: $IF_NDK_VERSION"
    export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$IF_NDK_VERSION"
else
    # Fallback to latest available NDK
    LATEST_NDK=$(ls -1 "$ANDROID_SDK_ROOT/ndk" 2>/dev/null | sort -V | tail -n1)
    if [ -n "$LATEST_NDK" ]; then
        echo "NDK $IF_NDK_VERSION not found. Falling back to latest: $LATEST_NDK"
        export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$LATEST_NDK"
    else
        echo "ERROR: No NDK found in $ANDROID_SDK_ROOT/ndk"
        exit 1
    fi
fi

export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_NDK_ROOT

ANDROID_PROJECT_DIR="$PROJECT_ROOT/android"
CPP_DIR="$ANDROID_PROJECT_DIR/app/src/main/cpp"

echo "======================================================="
echo "R47 Android Builder"
echo "SDK: $ANDROID_SDK_ROOT"
echo "NDK: $ANDROID_NDK_ROOT"
echo "Jobs: $R47_BUILD_JOBS"
echo "======================================================="

# --- 2. Update Version from Upstream (c43 Core) ---
# We pull the hash from SwissMicros. 
# Public repo uses 'upstream', Private repo uses 'origin'.
COMMIT_HASH=$(git rev-parse --short upstream/master 2>/dev/null || git rev-parse --short origin/master 2>/dev/null || echo "unknown")
echo "--- SwissMicros Core Version: $COMMIT_HASH ---"

# --- 3. Generate Assets (make sim) ---
echo "--- Generating Core Assets (running make sim) ---"
# Clean polluted generated headers that might break Meson include precedence
rm -f src/generated/*.c src/generated/constantPointers.h src/generated/softmenuCatalogs.h

if [ -d "build.sim" ] && [ ! -f "build.sim/build.ninja" ]; then
    rm -rf build.sim
fi

make -j "$R47_BUILD_JOBS" NINJAFLAGS="-j $R47_BUILD_JOBS" sim

if [ $? -ne 0 ]; then
    echo "ERROR: 'make sim' failed."
    exit 1
fi

# --- 4. Stage Native Source Code ---
if ! R47_CORE_HASH="$COMMIT_HASH" bash "$ANDROID_PROJECT_DIR/stage_native_sources.sh"; then
    echo "ERROR: Android native staging failed."
    exit 1
fi

# Copy Assets (Fonts)
echo "--- Copying Font Assets ---"
ASSETS_FONTS_DIR="$ANDROID_PROJECT_DIR/app/src/main/assets/fonts"
mkdir -p "$ASSETS_FONTS_DIR"
cp -v res/fonts/*.ttf "$ASSETS_FONTS_DIR/"

# Create local.properties
echo "sdk.dir=$ANDROID_SDK_ROOT" > "$ANDROID_PROJECT_DIR/local.properties"
# Note: ndk.dir is deprecated; we pass the version via Gradle property instead.

cd "$ANDROID_PROJECT_DIR"

# --- 4. Build APK ---
echo "--- Building APK ---"
GRADLE_CMD="./gradlew"

if [ ! -f "$GRADLE_CMD" ]; then
    gradle wrapper --gradle-version 8.9 --distribution-type bin
fi
chmod +x "$GRADLE_CMD"

# Pass detected NDK/SDK versions as Project Properties to override build.gradle defaults
GRADLE_PROPS="-Pr47.ndkVersion=$IF_NDK_VERSION"
GRADLE_PROPS="$GRADLE_PROPS -Pr47.coreVersion=$COMMIT_HASH"
if [ -n "$R47_COMPILE_SDK" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.compileSdk=$R47_COMPILE_SDK"; fi
if [ -n "$R47_VERSION_CODE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.versionCode=$R47_VERSION_CODE"; fi
if [ -n "$R47_VERSION_NAME" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.versionName=$R47_VERSION_NAME"; fi

# Clean cxx to ensure fresh cmake run
rm -rf app/.cxx
$GRADLE_CMD clean
$GRADLE_CMD --max-workers "$R47_BUILD_JOBS" assembleDebug $GRADLE_PROPS

APK_PATH="app/build/outputs/apk/debug/R47calculator-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "SUCCESS: APK created at: $ANDROID_PROJECT_DIR/$APK_PATH"
else
    echo "ERROR: APK build failed."
    exit 1
fi

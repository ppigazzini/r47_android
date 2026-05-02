#!/bin/bash

set -Eeuo pipefail

on_error() {
    local exit_code="$?"
    local line_no="${1:-${BASH_LINENO[0]:-$LINENO}}"

    echo "ERROR: build_android.sh failed with exit code ${exit_code} at line ${line_no} while running: ${BASH_COMMAND}" >&2
    exit "$exit_code"
}

trap 'on_error "${BASH_LINENO[0]:-$LINENO}"' ERR

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

if [ -n "${JAVA_HOME-}" ] && [ ! -d "$JAVA_HOME" ]; then
    unset JAVA_HOME
fi

if [ -z "${JAVA_HOME-}" ]; then
    if [ "$(uname -s)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
        JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home 2>/dev/null || true)
        if [ -d "$JAVA_HOME_CANDIDATE" ]; then
            export JAVA_HOME="$JAVA_HOME_CANDIDATE"
            echo "Detected JAVA_HOME from /usr/libexec/java_home: $JAVA_HOME"
        fi
    fi
fi

if [ -z "${JAVA_HOME-}" ]; then
    JAVA_BIN=$(command -v java 2>/dev/null || true)
    if [ -n "$JAVA_BIN" ]; then
        JAVA_HOME_CANDIDATE=$(dirname "$(dirname "$(resolve_path "$JAVA_BIN")")")
        if [ -d "$JAVA_HOME_CANDIDATE" ]; then
            export JAVA_HOME="$JAVA_HOME_CANDIDATE"
            echo "Detected JAVA_HOME from PATH: $JAVA_HOME"
        fi
    fi
fi

if [ -z "${JAVA_HOME-}" ] && [ -d /usr/lib/jvm ]; then
    for jvm in /usr/lib/jvm/default-java /usr/lib/jvm/*; do
        if [ -d "$jvm" ] && [ -x "$jvm/bin/java" ]; then
            export JAVA_HOME="$jvm"
            echo "Detected JAVA_HOME from /usr/lib/jvm: $JAVA_HOME"
            break
        fi
    done
fi

if [ -n "${JAVA_HOME-}" ]; then
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

detect_android_sdk_root() {
    local candidate=""

    for candidate in "${ANDROID_SDK_ROOT-}" "${ANDROID_HOME-}" "$HOME/.android/sdk" "$HOME/Android/Sdk"; do
        if [ -n "$candidate" ] && [ -d "$candidate" ] && { [ -d "$candidate/platform-tools" ] || [ -d "$candidate/ndk" ]; }; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

ensure_xlsxio_toolchain() {
    local xlsxio_prefix="${R47_XLSXIO_PREFIX:-$HOME/.cache/r47/xlsxio/$R47_XLSXIO_COMMIT}"
    local xlsxio_dir="${TMPDIR:-/tmp}/r47-xlsxio-$R47_XLSXIO_COMMIT"
    local minizip_prefix=""
    local cmake_args=(
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5
        -DBUILD_STATIC=ON
        -DBUILD_SHARED=OFF
        -DBUILD_DOCUMENTATION=FALSE
        -DBUILD_EXAMPLES=FALSE
        -DBUILD_TOOLS=ON
        -DWITH_LIBZIP=OFF
    )

    if command -v xlsxio_xlsx2csv >/dev/null 2>&1; then
        return 0
    fi

    if [ -x "$xlsxio_prefix/bin/xlsxio_xlsx2csv" ]; then
        export PATH="$xlsxio_prefix/bin:$PATH"
        return 0
    fi

    echo "--- Bootstrapping pinned xlsxio toolchain ---"
    rm -rf "$xlsxio_dir"
    git init --initial-branch=main "$xlsxio_dir" >/dev/null
    git -C "$xlsxio_dir" remote add origin "$R47_XLSXIO_URL"
    git -C "$xlsxio_dir" fetch --depth 1 origin "$R47_XLSXIO_COMMIT"
    git -C "$xlsxio_dir" checkout --detach FETCH_HEAD >/dev/null

    mkdir -p "$xlsxio_prefix"
    if ! pkg-config --exists minizip 2>/dev/null; then
        minizip_prefix=$(ensure_local_minizip_prefix) || return 1
        cmake_args+=("-DMINIZIP_DIR=$minizip_prefix")
    fi

    "$R47_CMAKE_BIN" -S "$xlsxio_dir" -B "$xlsxio_dir/build" "${cmake_args[@]}"
    "$R47_CMAKE_BIN" --build "$xlsxio_dir/build" --parallel "$R47_BUILD_JOBS"
    "$R47_CMAKE_BIN" --install "$xlsxio_dir/build" --prefix "$xlsxio_prefix"

    export PATH="$xlsxio_prefix/bin:$PATH"
    command -v xlsxio_xlsx2csv >/dev/null 2>&1
}

ensure_local_minizip_prefix() {
    local minizip_prefix="${R47_MINIZIP_PREFIX:-$HOME/.cache/r47/minizip/dev}"
    local deb_dir="${TMPDIR:-/tmp}/r47-minizip-deb"
    local extract_dir="$deb_dir/extract"
    local deb_file=""
    local lib_file=""

    if [ -f "$minizip_prefix/include/minizip/unzip.h" ] && [ -f "$minizip_prefix/lib/libminizip.a" ]; then
        printf '%s\n' "$minizip_prefix"
        return 0
    fi

    if ! command -v apt-get >/dev/null 2>&1 || ! command -v dpkg-deb >/dev/null 2>&1; then
        echo "ERROR: Automatic minizip bootstrap requires apt-get and dpkg-deb. Install a static minizip development package manually and set R47_MINIZIP_PREFIX to a prefix containing include/minizip/unzip.h and lib/libminizip.a." >&2
        return 1
    fi

    rm -rf "$deb_dir"
    mkdir -p "$deb_dir"

    if ! (
        cd "$deb_dir"
        apt-get download libminizip-dev >/dev/null
    ); then
        echo "ERROR: Failed to download libminizip-dev for the pinned xlsxio bootstrap. Install minizip development files manually or set R47_MINIZIP_PREFIX." >&2
        return 1
    fi

    deb_file=$(find "$deb_dir" -maxdepth 1 -name 'libminizip-dev_*.deb' | head -n1)
    if [ -z "$deb_file" ]; then
        echo "ERROR: libminizip-dev download completed without producing a .deb payload. Install minizip development files manually or set R47_MINIZIP_PREFIX." >&2
        return 1
    fi

    dpkg-deb -x "$deb_file" "$extract_dir"
    lib_file=$(find "$extract_dir" -path '*/libminizip.a' | head -n1)
    if [ -z "$lib_file" ]; then
        echo "ERROR: Extracted libminizip-dev payload did not contain libminizip.a. Install a compatible static minizip package manually or set R47_MINIZIP_PREFIX." >&2
        return 1
    fi

    mkdir -p "$minizip_prefix/include/minizip" "$minizip_prefix/lib"
    cp -f "$extract_dir"/usr/include/minizip/*.h "$minizip_prefix/include/minizip/"
    cp -f "$lib_file" "$minizip_prefix/lib/"

    printf '%s\n' "$minizip_prefix"
}

resolve_cmake_bin() {
    local candidate=""
    local sdk_root="${ANDROID_SDK_ROOT-}"

    if command -v cmake >/dev/null 2>&1; then
        command -v cmake
        return 0
    fi

    if [ -n "$sdk_root" ]; then
        for candidate in "$sdk_root/cmake/$R47_CMAKE_VERSION/bin/cmake" "$sdk_root"/cmake/*/bin/cmake; do
            if [ -x "$candidate" ]; then
                printf '%s\n' "$candidate"
                return 0
            fi
        done
    fi

    return 1
}

PROJECT_ROOT="$(pwd)"
if ANDROID_SDK_ROOT_CANDIDATE=$(detect_android_sdk_root); then
    export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_CANDIDATE"
else
    export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
fi

R47_CMAKE_VERSION=${R47_CMAKE_VERSION-}
if [ -z "$R47_CMAKE_VERSION" ]; then
    R47_CMAKE_VERSION=$(grep "r47.cmakeVersion=" "$PROJECT_ROOT/android/gradle.properties" 2>/dev/null | cut -d'=' -f2 || true)
fi
if [ -z "$R47_CMAKE_VERSION" ]; then
    R47_CMAKE_VERSION=$(sed -n "s/.*project.findProperty('r47.cmakeVersion') ?: \"\([^\"]*\)\".*/\1/p" "$PROJECT_ROOT/android/app/build.gradle" | head -n1)
fi

# --- NDK Version Selection ---
# 1. Check for Environment Override
# 2. Check for Gradle Property in android/gradle.properties
# 3. Extract Default from build.gradle
# 4. Fallback to latest installed
IF_NDK_VERSION=${R47_NDK_VERSION-}
if [ -z "$IF_NDK_VERSION" ]; then
    IF_NDK_VERSION=$(grep "r47.ndkVersion=" "$PROJECT_ROOT/android/gradle.properties" 2>/dev/null | cut -d'=' -f2 || true)
fi
if [ -z "$IF_NDK_VERSION" ]; then
    IF_NDK_VERSION=$(grep "ndkVersion" "$PROJECT_ROOT/android/app/build.gradle" | grep -o '".*"' | sed 's/"//g' || true)
fi

if [ -n "$IF_NDK_VERSION" ] && [ -d "$ANDROID_SDK_ROOT/ndk/$IF_NDK_VERSION" ]; then
    echo "Using detected NDK version: $IF_NDK_VERSION"
    export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$IF_NDK_VERSION"
else
    # Fallback to latest available NDK
    LATEST_NDK=$(ls -1 "$ANDROID_SDK_ROOT/ndk" 2>/dev/null | sort -V | tail -n1 || true)
    if [ -n "$LATEST_NDK" ]; then
        echo "NDK $IF_NDK_VERSION not found. Falling back to latest: $LATEST_NDK"
        export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$LATEST_NDK"
    else
        echo "ERROR: No NDK found in $ANDROID_SDK_ROOT/ndk"
        exit 1
    fi
fi

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"

R47_XLSXIO_URL=${R47_XLSXIO_URL:-https://github.com/brechtsanders/xlsxio.git}
R47_XLSXIO_COMMIT=${R47_XLSXIO_COMMIT:-a9016eb2eb46dcd613a68fcfcd1002b5adf64ae9}
if ! R47_CMAKE_BIN=$(resolve_cmake_bin); then
    echo "ERROR: No usable cmake executable found. Install cmake or Android SDK CMake $R47_CMAKE_VERSION."
    exit 1
fi
export PATH="$(dirname "$R47_CMAKE_BIN"):$PATH"
if ! ensure_xlsxio_toolchain; then
    echo "ERROR: Failed to provision xlsxio_xlsx2csv."
    exit 1
fi

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
COMPILE_SDK_OVERRIDE=${R47_COMPILE_SDK-}
VERSION_CODE_OVERRIDE=${R47_VERSION_CODE-}
VERSION_NAME_OVERRIDE=${R47_VERSION_NAME-}
SOURCE_REPOSITORY_URL_OVERRIDE=${R47_SOURCE_REPOSITORY_URL-}
SOURCE_COMMIT_OVERRIDE=${R47_SOURCE_COMMIT-}
UPSTREAM_SOURCE_REPOSITORY_URL_OVERRIDE=${R47_UPSTREAM_SOURCE_REPOSITORY_URL-}
UPSTREAM_SOURCE_COMMIT_OVERRIDE=${R47_UPSTREAM_SOURCE_COMMIT-}
XLSXIO_SOURCE_REPOSITORY_URL_VALUE=${R47_XLSXIO_URL-}
XLSXIO_SOURCE_COMMIT_VALUE=${R47_XLSXIO_COMMIT-}

if [ -n "$COMPILE_SDK_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.compileSdk=$COMPILE_SDK_OVERRIDE"; fi
if [ -n "$VERSION_CODE_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.versionCode=$VERSION_CODE_OVERRIDE"; fi
if [ -n "$VERSION_NAME_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.versionName=$VERSION_NAME_OVERRIDE"; fi
if [ -n "$SOURCE_REPOSITORY_URL_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.sourceRepositoryUrl=$SOURCE_REPOSITORY_URL_OVERRIDE"; fi
if [ -n "$SOURCE_COMMIT_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.sourceCommit=$SOURCE_COMMIT_OVERRIDE"; fi
if [ -n "$UPSTREAM_SOURCE_REPOSITORY_URL_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.upstreamSourceRepositoryUrl=$UPSTREAM_SOURCE_REPOSITORY_URL_OVERRIDE"; fi
if [ -n "$UPSTREAM_SOURCE_COMMIT_OVERRIDE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.upstreamSourceCommit=$UPSTREAM_SOURCE_COMMIT_OVERRIDE"; fi
if [ -n "$XLSXIO_SOURCE_REPOSITORY_URL_VALUE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.xlsxioSourceRepositoryUrl=$XLSXIO_SOURCE_REPOSITORY_URL_VALUE"; fi
if [ -n "$XLSXIO_SOURCE_COMMIT_VALUE" ]; then GRADLE_PROPS="$GRADLE_PROPS -Pr47.xlsxioSourceCommit=$XLSXIO_SOURCE_COMMIT_VALUE"; fi
GRADLE_EXTRA_ARGS=${R47_GRADLE_ARGS-}

# Clean cxx to ensure fresh cmake run
rm -rf app/.cxx
$GRADLE_CMD clean $GRADLE_EXTRA_ARGS
$GRADLE_CMD --max-workers "$R47_BUILD_JOBS" assembleDebug $GRADLE_EXTRA_ARGS $GRADLE_PROPS

APK_PATH="app/build/outputs/apk/debug/R47calculator-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "SUCCESS: APK created at: $ANDROID_PROJECT_DIR/$APK_PATH"
else
    echo "ERROR: APK build failed."
    exit 1
fi

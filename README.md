# R47 Android Port: RPN Calculator

This repository provides an Android port of the [SwissMicros C43/C47 project](https://gitlab.com/rpncalculators/c43.git). It runs the high-performance mathematical core of the C43 via a native JNI wrapper and exposes three live shell modes: the default `r47_texture` classic image shell, the background-backed `r47_background` shell, and a native-drawn chrome mode.

## 🎯 Features
- **Scene-Driven Android Keypad**: `native` renders all 43 keys, including the stateful F1-F6 softkeys, from one native snapshot instead of relying on per-label JNI polling. One shared Kotlin topology model now defines the Android row and key-family contract used by layout and touch geometry, while the softkey row keeps its dedicated scene-driven renderer for reverse video, overlays, preview cues, and value text. In native mode the default dark keys now draw as plain rounded fills at `RGB(63, 63, 63)`, and softkeys move the primary legend into the upper row when auxiliary text is visible. `r47_background` reuses that same snapshot for keypad labels and softkey text without repainting the key bodies baked into the background asset.
- **Restored Shell Backgrounds**: Settings expose `r47_texture` as the default shell image, plus `r47_background` and `native` chrome.
- **Normalized Shared Touch Geometry**: `r47_texture` keeps the image-backed shell with invisible touch zones, and all chrome modes now reuse one normalized touch grid with contiguous cell boundaries, matched outer keypad bounds, and the same active-key layout.
- **Shell-Mode Parity**: All three chrome modes now share the same settings-entry touch strip, the same texture-derived LCD position and size, and the same adaptive visible crop. The native software shell now renders a pure-black body, density-scaled top and bottom bars, and a larger rounded silhouette while staying aligned with the bitmap-shell LCD and touch contracts.
- **Native Performance**: Math engine runs in C via JNI for 100% parity with the hardware.
- **Modern Android Support**: Optimized for high-refresh screens and 16KB page sizes (Android 15+).
- **SAF Integration**: Full support for Android's Storage Access Framework for programs and state files.

## 🛠️ Linux Dependencies
To build the simulator or the Android APK on Linux (Ubuntu/Debian), you will need the following:

### 1. Build Tools & Math Libraries
```bash
sudo apt-get install git build-essential meson ninja-build libgmp-dev libgtk-3-dev libpulse-dev
```

**NOTE:** Please refer to the [Build instructions](https://gitlab.com/h2x/c47-wiki/-/wikis/Build-instructions) in the [C47-wiki](https://gitlab.com/h2x/c47-wiki/-/wikis/home) for further details on how to set up a development environment in Macos, Linux, Windows. 

### 2. Android Development
- **Java**: OpenJDK 17 or higher.
- **Android SDK & NDK**: The checked-in app defaults target compile SDK 35,
  target SDK 35, NDK `29.0.14206865`, and CMake `3.22.1`.
- **Package Identity**: The checked-in base app identity is
    `com.example.r47`. Debug builds install as
    `com.example.r47.debug` so the snapshot lane stays separate from a
    future store release.
- **APK ABI**: The checked-in debug APK currently packages `arm64-v8a` only.

### 3. Firmware Cross-Compilation (Optional)
```bash
sudo apt-get install gcc-arm-none-eabi
```

## 🚀 Getting Started

### 1. Clone & Sync
The repository contains the Android shell and build blueprints. You must run the sync script to pull the mathematical core from C43 GitLab.
```bash
git clone https://github.com/paletochen/r47_android.git
cd r47_android
chmod +x sync_public.sh
./sync_public.sh
```

Repository constraint for contributors: the synced upstream core under `src/`
is authoritative. Keep repo-owned changes in the Android shell, build scripts,
docs, workflows, and Android-specific native bridge or HAL code. Do not keep
local overrides under `src/**`, including `src/**/meson.build`, because they
can silently override the synced upstream build graph and break future builds.

### 2. Build the Simulator (PC)
```bash
./dist.sh
```
This builds the GTK-based simulators for Linux/macOS and the firmware files for the physical DM42/DM42n hardware.

### 3. Build the Android App (APK)
Ensure `ANDROID_SDK_ROOT` is set in your environment.
```bash
./build_android.sh
```
`build_android.sh` resolves its worker count from `R47_BUILD_JOBS`, then
`CMAKE_BUILD_PARALLEL_LEVEL`, then the host CPU count. It exports
`CMAKE_BUILD_PARALLEL_LEVEL`, runs
`make -j <jobs> NINJAFLAGS="-j <jobs>" sim`, then delegates native staging to
`android/stage_native_sources.sh`. That step copies the synced `src/c47` tree,
`dep/decNumberICU`, generated files, and mini-gmp inputs into
`android/app/src/main/cpp` before Gradle builds the debug APK with
`--max-workers <jobs>`. The staged tree is an Android build input, not the
preferred source of truth.
Because sync and CI overlay the upstream core before restoring repo-owned
scaffolding, local build fixes should go in the Android shell, Android HAL or
JNI bridge, or staging scripts instead of in checked-in `src/**` overrides.
The checked-in release version inputs default to `r47.versionCode=1` and
`r47.versionName=0.1.0`. Debug builds append the synchronized core revision as a
`-snapshot.<core>` suffix automatically.
The resulting debug APK is
`android/app/build/outputs/apk/debug/R47calculator-debug.apk`.

### 🛠️ Advanced Build Configuration
If you have different versions of the Android SDK, NDK, or CMake installed (common on Windows), you can override the defaults. Depending on your operating system and terminal, some methods may be more reliable than others.

#### Option 1: Using gradle.properties (Most Reliable / Cross-Platform)
Create or edit `android/gradle.properties`. This method works on all systems (Linux, macOS, Windows) and is the recommended approach:
```properties
r47.compileSdk=35
r47.targetSdk=35
r47.ndkVersion=29.0.14206865
r47.cmakeVersion=3.22.1
r47.versionCode=1
r47.versionName=0.1.0
```
Note: You can also target **API 36 (Android 16 preview)** by setting `r47.compileSdk=36` and `r47.targetSdk=36` if you have the preview SDK installed.

#### Option 2: Environment Variables (Linux / macOS / WSL)
You can pass the NDK version, an explicit worker count, and optional
release-version overrides as environment variables before running the build
script. If `R47_BUILD_JOBS` is unset, the script auto-detects the host CPU
count:
```bash
export R47_NDK_VERSION="29.0.14206865"
export R47_BUILD_JOBS="8"
export R47_VERSION_CODE="1"
export R47_VERSION_NAME="0.1.0"
./build_android.sh
```

#### Option 3: PowerShell / CMD (Windows Native)
If you are running from a native Windows terminal without WSL or Git Bash:
**PowerShell:**
```powershell
$env:R47_NDK_VERSION="29.0.14206865"; $env:R47_BUILD_JOBS="8"; $env:R47_VERSION_CODE="1"; $env:R47_VERSION_NAME="0.1.0"; ./build_android.sh
```
**CMD:**
```cmd
set R47_NDK_VERSION=29.0.14206865 && set R47_BUILD_JOBS=8 && set R47_VERSION_CODE=1 && set R47_VERSION_NAME=0.1.0 && ./build_android.sh
```

#### Option 4: Append To gradle.properties (Alternative For Windows)
If setting environment variables is inconvenient, append the same project
properties directly to `android/gradle.properties`:
```cmd
echo r47.ndkVersion=29.0.14206865>> android/gradle.properties
echo r47.versionCode=1>> android/gradle.properties
echo r47.versionName=0.1.0>> android/gradle.properties
```

## 📜 Acknowledgments
Based on the excellent work by the [WP43/C47 team](https://gitlab.com/rpncalculators/c43) and [SwissMicros](https://www.swissmicros.com/). This port is an independent community contribution.

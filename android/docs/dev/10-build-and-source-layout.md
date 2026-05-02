# Build And Source Layout

## Checked-in defaults

- AGP `9.2.0`
- Kotlin Gradle plugin `2.3.21`, supplied through the AGP `9.2.0` runtime
  Kotlin integration path
- Java `17`, which matches the AGP `9.2.0` supported minimum and default JDK
- `compileSdk 36`
- `targetSdk 36`
- `minSdk 24`
- `ndkVersion 29.0.14206865`
- CMake `3.22.1`
- default ABI filter `arm64-v8a` with optional `r47.abiFilters` override
- base `namespace` and `applicationId` `com.example.r47`
- debug builds add `applicationIdSuffix ".debug"`
- release version inputs come from `r47.versionCode`, `r47.versionName`, and
  `r47.coreVersion`

## Build entry points

- `./sync_public.sh` is the authoritative upstream hydration entry point. It
  reads Git-tracked defaults from `upstream.source`, uses a Git-ignored local
  `upstream.lock` only when it contains `upstream_commit`, otherwise resolves
  the latest commit from the configured upstream ref, writes the resolved state
  back to `upstream.lock`, restores repo-owned files from `HEAD`, and refuses
  dirty tracked worktrees unless `--force` is passed.
- `./build_android.sh` is the authoritative Android debug-build entry point. It
  detects Java and the Android NDK, resolves one shared upstream commit through
  `tools/upstream.sh`, hydrates that resolved core when `src/c47` is missing,
  runs `make sim`, stages native inputs into `android/.staged-native/cpp`,
  regenerates staged native metadata there, copies fonts, writes
  `android/local.properties`, and runs Gradle clean plus `assembleDebug`. It
  also forwards optional extra Gradle arguments from `R47_GRADLE_ARGS`, which
  is how hosted CI applies the temporary multi-ABI emulator override.
- `cd android && ./gradlew assembleDebug` is appropriate only when the staged
  build-only native tree under `android/.staged-native/cpp` is already current
  and the change is isolated to the Android module.
- `cd android && ./gradlew :app:testDebugUnitTest` validates the Robolectric
  and fixture-backed Android JVM suite when the build-only staged native tree
  is already current.
- `cd android && ./gradlew :app:assembleDebugAndroidTest` compiles and
  packages the instrumentation suite. `:app:connectedDebugAndroidTest` requires
  a device or emulator, and `-Pr47.abiFilters=arm64-v8a,x86_64` is the
  supported override when that emulator is `x86_64`.
- `make sim` is the generator step behind Android native staging. It produces
  the simulator build plus generated files consumed by the build-only Android
  staging root under `android/.staged-native/cpp/generated`.

## Canonical versus staged native inputs

Canonical inputs for shared core work:

- `src/c47`
- `dep/decNumberICU`
- generated outputs under `build.sim`
- Android-only code under `android/app/src/main/java`
- Android bridge, HAL, and stub code under `android/app/src/main/cpp/c47-android`

Staged Android inputs built by CMake:

- `android/.staged-native/cpp/c47`
- `android/.staged-native/cpp/decNumberICU`
- `android/.staged-native/cpp/generated`
- `android/.staged-native/cpp/gmp`

Development rule:

- Change the canonical root sources for shared calculator behavior.
- Change the build-only staged tree directly only when working on staging logic
  or generated metadata.
- Change tracked Android-specific code under
  `android/app/src/main/cpp/c47-android` for shims, stubs, and bridge logic.

Build-safety rule:

- The synced upstream `src/**` tree, including `src/**/meson.build`, is
  authoritative for the shared native build graph.
- `sync_public.sh` and hosted CI overlay the resolved upstream tree first and
  then restore repo-owned files from Git, so restore allowlists and generic
  restore loops must never restore `src/**`.
- Android-only native fixes belong under
  `android/app/src/main/cpp/c47-android` or in staging logic, not in tracked
  root `src/**` overrides.
- The tracked directories
  `android/app/src/main/cpp/{c47,decNumberICU,generated,gmp}` are no longer the
  authoritative Android staging output and should stay untouched during normal
  builds.

## Android build flow

1. `sync_public.sh` overlays the resolved upstream core into the working tree,
  restores tracked Android-port files, and refreshes the local ignored
  `upstream.lock` with the commit used for that run.
2. `build_android.sh` runs `make sim`.
3. `android/stage_native_sources.sh` copies the synced core tree,
  `dep/decNumberICU`, generated outputs, `vcs.h`, and mini-gmp inputs into
  `android/.staged-native/cpp`, then regenerates
  `STAGED-SOURCE-MANIFEST.txt` and `staged_native_sources.cmake` there.
4. `android/app/build.gradle` invokes CMake at
   `android/app/src/main/cpp/CMakeLists.txt` and passes
   `-DR47_STAGED_CPP_DIR=<repo>/android/.staged-native/cpp`.
5. CMake regenerates the staged metadata when needed and builds the
  `c47-android` shared library from the build-only staged core, explicit
  decNumberICU sources, generated files, Android bridge files, and mini-gmp
  without using recursive globs.
6. Gradle packages the debug APK as
  `android/app/build/outputs/apk/debug/app-debug.apk`.

## CI lane

The GitHub Actions workflow at `.github/workflows/android-ci.yml` keeps the same
ownership model as the local build:

- it runs on `pull_request`, pushes to `github_ci` and `main`, scheduled
  nightly runs, and manual `workflow_dispatch`
- `resolve-upstream-core` resolves the latest upstream commit once per workflow
  run through `tools/upstream.sh resolve --latest`.
- `simulator-tests` syncs that resolved revision into the workspace through
  `sync_public.sh --commit ...` and runs `make test`.
- `android-debug` installs the pinned SDK, CMake, and NDK versions, runs
  `./build_android.sh`, verifies that build-only staged metadata exists under
  `android/.staged-native/cpp` while the tracked staging snapshots stay clean,
  and records packaging evidence for the default `arm64-v8a` debug APK.
- `android-tests` uses the same resolved upstream commit and staged-native
  build path, applies `-Pr47.abiFilters=arm64-v8a,x86_64` only for the hosted
  test lane, assembles `:app:assembleDebugAndroidTest`, runs
  `:app:testDebugUnitTest`, enables KVM, and runs
  `:app:connectedDebugAndroidTest` on a hosted `x86_64` emulator.
- the simulator and Android jobs consume the same resolved upstream commit for
  a given workflow run.
- Android build logs, Android test logs, and test reports are uploaded with
  `if: always()` where later steps can fail.
- `publish-main-snapshot` waits for `simulator-tests`, `android-debug`, and
  `android-tests` before publishing a main-branch prerelease.
- the uploaded Android artifact contains the debug APK plus `SHA256SUMS.txt`,
  `abis.txt`, `zipalign.txt`, `elf-load-segments.txt`, and `BUILD-METADATA.txt`.
- pushes to `main` and manual runs on `main` publish a debug-signed prerelease
  tagged with the upstream short SHA and titled from that same core revision.

The CI lane verifies packaged ABIs and 16 KB alignment. It is not a store-release
lane.

## Verification by change type

- Kotlin-only Android UI changes: `cd android && ./gradlew assembleDebug` when
  the build-only staged native tree is already current.
- Robolectric, fixture, or runtime-seam changes:
  `cd android && ./gradlew :app:testDebugUnitTest` when the build-only staged
  native tree is already current.
- SAF, debug-manifest, or Activity Result lifecycle changes:
  `cd android && ./gradlew :app:assembleDebugAndroidTest`, then
  `:app:connectedDebugAndroidTest` on a device or emulator. Add
  `-Pr47.abiFilters=arm64-v8a,x86_64` when that emulator is `x86_64`.
- JNI, HAL, CMake, or packaging changes: `./build_android.sh`.
- root core or generator changes: `make test` and then `./build_android.sh`.
- CI-only changes: verify `.github/workflows/android-ci.yml` against the local
  build contract and the artifact names described above.
- sync or restore-boundary changes: confirm restore logic still excludes `^src/`
  and does not reintroduce tracked local overrides under `src/**`; confirm the
  build-only staged metadata under `android/.staged-native/cpp` regenerates and
  the tracked `android/app/src/main/cpp/{c47,decNumberICU,generated,gmp}` tree
  still stays clean.

## When to rebuild from the top

Use `./build_android.sh` after any of the following:

- a sync from upstream
- changes under `src/c47`
- changes under `dep/decNumberICU`
- changes that affect generated files
- changes to `android/stage_native_sources.sh`
- changes to `android/app/src/main/cpp/CMakeLists.txt`
- changes that alter Android packaging or the JNI bridge surface

## Practical maintenance rules

- Treat the debug APK as a derived artifact, not as the source of truth.
- Keep `README.md`, Gradle literals, and shell-script defaults aligned when
  toolchain versions or package identity change.
- If a change affects both the canonical root tree and the staged Android tree,
  change the canonical owner first and restage the build-only Android tree.
- Keep artifact naming stable unless the workflow, docs, and release notes all
  change together.

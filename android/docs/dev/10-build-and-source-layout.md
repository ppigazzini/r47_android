# Build And Source Layout

## Checked-in defaults

- machine-readable Android tool defaults live in
  `android/r47-defaults.properties`; the checked-in values currently cover
  Java `17`, `compileSdk 36`, `targetSdk 36`, `minSdk 24`, build tools
  `36.0.0`, `ndkVersion 29.0.14206865`, CMake `3.22.1`, default ABI filter
  `arm64-v8a`, hosted Android test API `34`, hosted Android test ABI filters
  `arm64-v8a,x86_64`, and the pinned xlsxio URL plus commit.
- settings-owned repositories via `android/settings.gradle`
- version catalog `android/gradle/libs.versions.toml`, which owns the checked-in
  AGP `9.2.0` plugin coordinate plus AndroidX and Material library versions.
- Jetifier explicitly disabled in `android/gradle.properties`
- base `namespace` and `applicationId` `com.example.r47`
- debug builds add `applicationIdSuffix ".debug"`
- release version inputs come from `r47.versionCode`, `r47.versionName`, and
  `r47.coreVersion`
- release minify and resource shrinking default to on via
  `r47.releaseMinify` and `r47.releaseShrinkResources`
- release native debug symbols default to `FULL` via
  `r47.releaseNativeDebugSymbolLevel`

## Dependency update cadence

- Update `android/r47-defaults.properties` when SDK, NDK, CMake, build-tools,
  hosted-emulator, or xlsxio pins change, then rerun `./build_android.sh --doctor`
  before a broader build.
- Review AGP compatibility and JDK requirements whenever a new AGP stable line
  is adopted; keep `android/gradle/libs.versions.toml`, the Gradle wrapper, and
  `android/r47-defaults.properties` aligned when that happens.
- Review compile and target SDK levels when Android publishes the next stable
  API level, and keep local plus hosted test lanes on explicit API images.
- Review NDK and CMake pins when Android's AGP/NDK guidance or CMake release
  notes move, and rerun packaging checks before landing the update.
- Review Kotlin's current stable line on the JetBrains release page during
  quarterly maintenance or when build-tool work is already in flight, even
  though this repo currently uses AGP's built-in Kotlin integration instead of
  a standalone Kotlin plugin.
- Review the xlsxio pin only when upstream font-generation behavior, security
  fixes, or CI breakage require it.

## Build entry points

Public maintainer entrypoints:

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
  also exposes `--doctor` for SDK, NDK, CMake, xlsxio, upstream-lock, and
  staged-input status plus `--android-only` for the fast module-local lane that
  refuses stale staged native inputs.
  also forwards optional extra Gradle arguments from `R47_GRADLE_ARGS`, which
  is how hosted CI applies the temporary multi-ABI emulator override. Add
  `--verify-packaging` when you want the local build to write the same release
  evidence files CI publishes for the debug APK.
- `./build_android.sh --android-only` is the preferred fast Android-only path.
  It skips `make sim`, skips native restaging, and refuses to continue unless
  `android/.staged-native/cpp/STAGED-INPUTS.properties` still matches the
  canonical root plus generated inputs.
- `cd android && ./gradlew assembleDebug` is appropriate only when the staged
  build-only native tree under `android/.staged-native/cpp` is already current
  and the change is isolated to the Android module.
- `cd android && ./gradlew :app:bundleRelease` or
  `cd android && ./gradlew :app:assembleRelease` is the module-local release
  lane only when the staged build-only native tree is already current. Release
  builds remain unsigned unless all `r47.releaseStore*` and `r47.releaseKey*`
  inputs are supplied together.
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

Internal helpers:

- `tools/upstream.sh` is the shared implementation behind `sync_public.sh` and
  `build_android.sh`. Document it directly only when the task is about upstream
  resolution or restore-boundary internals.
- `android/stage_native_sources.sh` stages canonical native inputs into
  `android/.staged-native/cpp` and is normally invoked by `build_android.sh`.
- `android/generate_staged_native_metadata.sh` refreshes
  `STAGED-SOURCE-MANIFEST.txt` and `staged_native_sources.cmake` inside the
  build-only staging root. It is an internal helper, not a primary maintainer
  entrypoint.
- `android/compute_staged_native_inputs.sh` fingerprints the canonical root,
  generated, and mini-gmp inputs behind `--android-only` freshness checks and
  writes `STAGED-INPUTS.properties` during staging.

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
  `STAGED-SOURCE-MANIFEST.txt`, `staged_native_sources.cmake`, and
  `STAGED-INPUTS.properties` there.
4. `android/app/build.gradle` invokes CMake at
   `android/app/src/main/cpp/CMakeLists.txt` and passes
   `-DR47_STAGED_CPP_DIR=<repo>/android/.staged-native/cpp`.
5. CMake regenerates the staged metadata when needed and builds the
  `c47-android` shared library from the build-only staged core, explicit
  decNumberICU sources, generated files, Android bridge files, and mini-gmp
  without using recursive globs.
6. Gradle packages the debug APK as
  `android/app/build/outputs/apk/debug/app-debug.apk`.
7. When the caller requests packaging verification, the repo-owned helper
  `android/collect_packaging_evidence.sh` copies the artifact and writes ABI,
  zipalign, ELF `LOAD` segment, SHA256, and provenance evidence beside it.

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
  and records packaging evidence for the default `arm64-v8a` debug APK through
  `android/collect_packaging_evidence.sh`.
- `android-tests` uses the same resolved upstream commit and staged-native
  build path, applies the defaults-file `android_test_abi_filters` override
  only for the hosted
  test lane, assembles `:app:assembleDebugAndroidTest`, runs
  `:app:testDebugUnitTest`, enables KVM, and runs
  `:app:connectedDebugAndroidTest` on the defaults-file hosted emulator API.
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

## Release and packaging policy

- The Android app keeps the default checked-in lane debug-first. Release work is
  opt-in and remains a maintainer lane.
- `android/app/build.gradle` defines release signing from
  `r47.releaseStoreFile`, `r47.releaseStorePassword`, `r47.releaseKeyAlias`,
  and `r47.releaseKeyPassword`. Supplying only some of those values is a hard
  configuration error.
- Release builds default `minifyEnabled` and `shrinkResources` to `true` and
  request `ndk.debugSymbolLevel "FULL"`.
- `bundleRelease` is the canonical AAB command. `assembleRelease` remains
  available when an APK is required for local inspection.
- `android/collect_packaging_evidence.sh` is the canonical provenance collector
  for both CI and local packaging checks. For debug it verifies ABI contents,
  zip alignment, and ELF `LOAD` segment alignment. For release it also accepts a
  bundle, mapping file, and native-symbol archive so provenance can travel with
  the output.

## Verification by change type

- Kotlin-only Android UI changes: `cd android && ./gradlew assembleDebug` when
  the build-only staged native tree is already current.
- Android module-only changes with the staged tree already current:
  `./build_android.sh --android-only`.
- Host or cache diagnosis before building: `./build_android.sh --doctor`.
- Robolectric, fixture, or runtime-seam changes:
  `cd android && ./gradlew :app:testDebugUnitTest` when the build-only staged
  native tree is already current.
- SAF, debug-manifest, or Activity Result lifecycle changes:
  `cd android && ./gradlew :app:assembleDebugAndroidTest`, then
  `:app:connectedDebugAndroidTest` on a device or emulator. Add
  `-Pr47.abiFilters=arm64-v8a,x86_64` when that emulator is `x86_64`.
- JNI, HAL, CMake, or packaging changes: `./build_android.sh`.
- packaging evidence changes with local proof: `./build_android.sh --verify-packaging`
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
- Keep `android/settings.gradle`, `android/gradle/libs.versions.toml`, and the
  app module in sync when dependency ownership changes.
- Keep `README.md`, `android/r47-defaults.properties`,
  `android/gradle/libs.versions.toml`, and CI defaults aligned when toolchain
  versions or package identity change.
- If a change affects both the canonical root tree and the staged Android tree,
  change the canonical owner first and restage the build-only Android tree.
- Keep artifact naming stable unless the workflow, docs, and release notes all
  change together.

# Android Development Docs

These pages describe the current Android development contract for the checked-in
R47 shell.

They are code-facing development notes, not end-user usage docs.

Read in this order:

- [10-build-and-source-layout.md](10-build-and-source-layout.md): toolchain,
  source ownership, build entry points, and CI.
- [20-kotlin-shell-architecture.md](20-kotlin-shell-architecture.md):
  lifecycle, runtime ownership, input flow, storage, and Kotlin-side structure.
- [30-native-core-and-jni.md](30-native-core-and-jni.md): CMake, JNI,
  synchronization, HAL I/O, and packaging constraints.
- [40-ui-rendering-and-gtk-mapping.md](40-ui-rendering-and-gtk-mapping.md):
  shell projection, keypad scene data, and GTK-derived rendering rules.
- [90-official-references.md](90-official-references.md): official Android,
  NDK, Kotlin, storage, and view-system references.

The CI lane follows the same ownership model as the local build: resolve one
authoritative upstream core revision, run the root simulator tests, build the
debug APK, run Android JVM plus emulator-backed instrumentation tests, then
publish a main-branch snapshot prerelease only after those jobs pass.

The public maintainer entrypoints are `./sync_public.sh` and
`./build_android.sh`. Use `./build_android.sh --doctor` to inspect host and
staging readiness, and `./build_android.sh --android-only` for the fast
module-local lane when staged native inputs are current. Staging helpers stay
internal unless the task is specifically about sync or staging internals.

Shared Android SDK, NDK, CMake, build-tools, hosted-emulator, and xlsxio pins
live in `android/r47-defaults.properties`.

Two rules govern most Android work in this repository:

1. The preferred source of truth for shared calculator behavior is the synced
   root tree plus generated outputs from `build.sim`.
2. The build-only staged Android native input tree lives under
  `android/.staged-native/cpp`. The tracked
  `android/app/src/main/cpp/{c47,generated,decNumberICU,gmp}` tree is legacy
  non-authoritative content, while `android/app/src/main/cpp/c47-android`
  stays the Android-owned bridge, HAL, and stub surface.

If a change crosses both Kotlin and native boundaries, read the build page and
the JNI page before editing.

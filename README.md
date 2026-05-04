# R47 Android

This repository is the Android port, build wrapper, and maintainer overlay for
the R47 calculator variant implemented in the upstream C47 codebase.

The authoritative upstream source repository is the Git repo configured in
`upstream.source`, currently `https://gitlab.com/rpncalculators/c43.git`.
That GitLab path still uses the older `c43` repository name, but this Android
repo should document it as the upstream C47 core consumed by the R47 Android
port.

This repo does not own the shared calculator core. It owns the Android app,
JNI bridge, native staging flow, CI wiring, and local build scaffolding around
the hydrated upstream core plus the root generator pipeline.

## Upstream Source

- source repo: `https://gitlab.com/rpncalculators/c43.git`
- tracked defaults: `upstream.source`
- optional local pin: `upstream.lock`
- public hydration entrypoint: `./scripts/sync_public.sh`

## What This Repo Owns

- Android app code under `android/`
- repo-owned automation under `scripts/`
- Android-managed code under `android/app/src/main/java/com/example/r47`
- Android-only native glue under `android/app/src/main/cpp/c47-android`
- sync, build, and CI scaffolding under `scripts/` and `.github/`
- maintainer-facing Android docs under `android/docs/dev/`

Shared calculator behavior still belongs to the hydrated upstream core and the
root generator pipeline.

## Maintainer Entrypoints

- `./scripts/sync_public.sh` hydrates the authoritative upstream core defined by
	`upstream.source`.
- `android/r47-defaults.properties` is the machine-readable source of truth for
	shared Android SDK, NDK, CMake, build-tools, test-emulator, and xlsxio
	defaults consumed by local scripts and CI.
- `./scripts/build_android.sh` is the canonical Android debug-build path. Use
	`--doctor` to report host and staging readiness, `--android-only` for the
	fast module-only lane when staged native inputs are current, and
	`--verify-packaging` to emit the same ABI, zipalign, ELF, SHA256, and
	provenance evidence classes that CI records for the debug APK.
- `make sim` and `make test` are the canonical root simulator and generator
	validation paths.
- `cd android && ./gradlew assembleDebug` is a module-local shortcut only when
	the build-only staged native tree is already current.
## Source Ownership

- canonical shared-native inputs live in hydrated `src/c47`,
	`dep/decNumberICU`, and generated outputs from `build.sim`
- the authoritative Android staged native build root is
	`android/.staged-native/cpp`
- tracked Android-owned native glue lives under
	`android/app/src/main/cpp/c47-android`
- the former tracked directories
	`android/app/src/main/cpp/{c47,decNumberICU,generated,gmp}` have been
	retired and must stay absent during normal builds
- public checkouts keep one explicit staging-only mini-gmp fallback under
	`android/compat/mini-gmp-fallback`

For the Android maintainer docs, start at `android/docs/dev/README.md`. Use
`android/docs/dev/10-build-and-source-layout.md` as the canonical Android
ownership, build, and rebuild contract.

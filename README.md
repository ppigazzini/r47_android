# C47

The [C47 project](https://gitlab.com/rpncalculators/c43) is a fork of the [WP43 project](https://gitlab.com/rpncalculators/wp43), adapted to work on a standard Swiss Micros DM42 calculator with the standard keyboard layout.

The two shifted key positions (f and g) on the calculator are supported by applying the infamous cycling shift behaviour to the DM42's single shift key, which means a custom faceplate or overlay is all that is needed to convert the DM42 hardware into a C47 calculator. No key stickers are required on a DM42.

Building on the user experience of the legendary HP-42S, tapping into the unprecedented accuracy of the WP34S engine, and adding a quite few extra features of its own, the C47 is a thriving open source project aiming to make RPN calculators highly relevant for mathematicians, scientists and engineers of today.

C43 / C47: The C47 historically is based on the WP43, which was called WP43S before it was renamed to WP43. The C47 as such initially was forked and called WP43C, then renamed to C43, then renamed to C47. Many references in the source code and documents on GitLab still refer to any or all of the listed prior names! This makes no difference to the current (C47) state of the project.

The C47 Wiki can be found [here](https://gitlab.com/rpncalculators/c43/-/wikis/home).
The C47 Community Wiki, which every user can help to create, can be found [here](https://gitlab.com/h2x/c47-wiki/-/wikis/home).

The page where you can order the bezel to glue it on a DM42 to make it a C47 can be found [here](https://47calc.com).

A comprehensive reference to all functions of the C47 as PDF downloads can be found [here](https://47calc.com/documentation/combined/doc.html).

## Upstream Hydration

- `upstream.source` is Git-tracked and defines the authoritative upstream URL
	plus ref for the shared C43 import.
- `upstream.lock` is generated locally, ignored by Git, and may optionally pin
	one resolved upstream commit for repeatable local reruns.
- `./sync_public.sh` is the authoritative hydration entry point. Its shared
	resolver uses this policy:
	- an explicit `--commit` wins
	- otherwise a local `upstream.lock` commit wins when present
	- otherwise it resolves the latest commit from `upstream.source`
- `./sync_public.sh --latest` refreshes the local lock to the newest upstream
	HEAD.
- `./sync_public.sh --locked` requires a local pinned commit and fails if none
	exists.
- `./build_android.sh` resolves one upstream commit through the same shared
	command, writes a local `upstream.lock` when needed, stages authoritative
	native inputs plus staged metadata under `android/.staged-native/cpp`, and
	builds the Android APK from that resolved upstream state.
- the tracked directories `android/app/src/main/cpp/{c47,decNumberICU,generated,gmp}`
	are no longer the authoritative staged build output and should stay untouched
	during normal builds.
- CI resolves the latest upstream commit once per workflow run and passes that
	exact commit into each downstream `sync_public.sh` invocation so one workflow
	cannot split across multiple upstream revisions.

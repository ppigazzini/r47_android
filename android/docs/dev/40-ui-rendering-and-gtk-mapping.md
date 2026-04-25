# UI Rendering And GTK Mapping

## Logical shell model

The Android shell still renders against two logical calculator body canvases,
not against device pixels:

- `r47_texture`: shell size `537 x 1005`, visual bezel and settings touch
  strip height `67.5`, LCD viewport left `25.5`, top `67.5`, width `486`,
  height `266.7`
- `native`: shell size `526 x 980`, visual bezel `72`, shared settings touch
  strip height `67.5 x 980 / 1005`, LCD viewport derived from the texture
  shell by scaling left and width with `526 / 537` and top and height with
  `980 / 1005`
- `r47_background`: shell size `526 x 980`, visual bezel `72`, shared
  settings touch strip height `67.5 x 980 / 1005`, LCD viewport derived from
  the texture shell by scaling left and width with `526 / 537` and top and
  height with `980 / 1005`

In `full_width`, `ReplicaOverlay` now fits one shared visible frame across all
three modes. The shared-shell modes trim `12 / 14 / 12 / 16` logical units,
and `r47_texture` trims those same margins scaled back into texture space so
the LCD window and the shell crop land at the same on-screen position.

`ReplicaOverlay` projects the active shell into the current window. In normal
mode it either draws native shell chrome with `Canvas`, draws the restored
`r47_background` background shell image behind the scene-driven keypad, or
draws the restored `r47_texture` classic image shell. `ReplicaKeypadLayout`
now owns one normalized shared touch-cell map across all chrome modes. The
grid uses contiguous row bands, shared midline boundaries, and consistent outer
keypad bounds inside each row group. `r47_texture` uses that map without
rendered key views, while the native and background-backed modes keep the
scene-driven key views on top of the same active-cell geometry.
`ReplicaOverlay` also keeps one shared settings-entry touch strip across all
chrome modes. All three modes now share one texture-derived LCD placement
contract, and `full_width` uses one shared visible-frame crop contract across
the texture, background-backed, and native shells. In PiP mode the overlay draws the LCD bitmap
full-window and maps horizontal touches across the LCD to the six softkeys.

The overlay exposes two scaling modes:

- `full_width`: fit the logical shell inside the trimmed window frame
- `physical`: cap the fit scale to a physical-size target derived from display
  DPI

The overlay exposes three shell chrome values:

- `r47_texture`: restore the classic full-image shell and use the shared
  invisible touch-cell map plus the same settings-entry touch strip as the
  other modes
- `r47_background`: draw the density-qualified background shell while
  keeping the scene-driven keypad renderer on the shared touch-cell map and the
  texture-aligned LCD frame
- `native`: draw the body, bezel, and LCD frame with Android `Canvas` while
  keeping the same logical keypad geometry, settings-entry touch strip, and
  texture-aligned LCD frame

The native software shell now uses a tighter outer corner radius than the
older 32-unit round-rect so its silhouette stays closer to the bitmap shells.

The projection is the first place to inspect when the shell or LCD looks
correctly rendered but globally misplaced.

Projection errors usually affect the whole shell, the LCD frame, and the keypad
children together. A single-key issue is usually lower in the stack.

## LCD path

The native core exposes a `400 x 240` LCD pixel buffer. `NativeCoreRuntime`
pulls that buffer on the frame callback and hands it to `ReplicaOverlay`.

`ReplicaOverlay.updateLcd(...)`:

- compares the new frame against the cached pixel buffer
- computes the smallest changed rectangle
- updates the backing `Bitmap`
- invalidates only the changed on-screen region

That partial invalidation path is why LCD refresh bugs and keypad-layout bugs
must be debugged separately.

## Refresh path

`NativeCoreRuntime` pulls LCD pixels every frame while the app is active. It
also checks keypad metadata on the same frame loop and rebuilds the Kotlin
snapshot when native keypad state changes or when the fallback refresh interval
expires.

That means LCD bugs, keypad-state bugs, and rendering bugs can share the same
frame boundary while still having different owners.

## Keypad conversion model

The rendered keypad path is scene-driven. Android does not poll per-label text
from separate bridge calls. `r47_texture` still keeps invisible image-backed
touch zones, but those zones now come from the same normalized touch-cell map
used by the rendered shells.

The native side provides two arrays:

- keypad metadata
- keypad labels

`KeypadSnapshot.fromNative(...)` converts those arrays into:

- keyboard-wide state such as shift, alpha, and softmenu status
- one `KeypadKeySnapshot` per key
- style roles, layout classes, scene flags, overlay state, and show-value
  fields used by the Android renderer

The snapshot also preserves softmenu paging state, dotted-row state, function
preview state, and per-key enabled state. Android should consume those fields,
not recreate them from label text.

This keeps content and state on the native side while Android owns measurement,
projection, and drawing.

## GTK-derived geometry

`ReplicaKeypadLayout` places all 43 touch cells using fixed logical coordinates
on the same shell model used by the overlay:

- one softkey row on a shared six-column boundary model
- two small rows on that same six-column boundary model
- one enter row where key `13` spans two columns on the shared midline
  boundaries
- four large rows on one shared five-column boundary model

The layout constants are GTK-derived geometry values expressed in the Android
logical shell space. The Android side does not recalculate key positions from
live GTK code. The current touch-cell map is normalized by row template instead
of older per-row shrink or shift tweaks, so adjacent cells meet at shared
midlines and row groups keep consistent outer bounds. That map is shared across
the texture shell and the rendered shells even though the repo still keeps two
logical shell canvases.

This is a mapping layer, not a port of the GTK layout engine. When geometry
parity changes, update the Android logical constants or the native scene data,
not an imagined GTK runtime dependency.

## Per-key renderer

Each key is a `CalculatorKeyView`. The view combines a button surface plus label
views and custom drawing logic.

It renders:

- primary label inside the button
- F and G faceplate labels above the button
- letter label in the right-side spacer
- softkey text, auxiliary text, value text, preview accents, reverse-video
  states, dotted rows, and overlay-state decorations when the scene contract
  asks for them

Main keys and softkeys share one renderer, but not the same drawing path:

- main keys use a button surface plus positioned text views and measured label
  offsets
- function keys use a dedicated softkey drawing path driven by scene flags,
  overlay state, and preview state

Typography and style come from scene data plus staged calculator fonts. Android
chooses how to measure and draw those roles; native code chooses which roles are
active.

For non-softkeys, the faceplate labels are positioned from the actual button
geometry inside the cell, not from the whole cell bounds. This is the place to
check when label centering diverges from GTK.

The faceplate group is centered from measured label widths and the actual button
center, not from hardcoded left and right split positions. That distinction is
important when one legend is empty, hidden, or visually narrow.

## Softkey scene states

The softkey row can carry more than text:

- reverse video
- top-line and bottom-line decorations
- checkbox, radio-button, or macro overlays
- `showText` and `showValue`
- dotted pagination cues
- function-preview targeting
- strike-out and strike-through states

If one of those surfaces is wrong, inspect the native keypad snapshot before
changing Android drawing code.

## Typography and label roles

`CalculatorKeyView` uses the staged calculator fonts and scene roles rather than
hardcoding one text style for all keys. In practice that means:

- primary labels can use different visual roles from faceplate labels
- numeric and softkey roles can diverge without changing geometry ownership
- faceplate labels that open menus are underlined because the native snapshot
  marks them as dedicated underline roles, not because Android inspects label
  text or moves the labels
- label-role changes should come from native scene metadata, not from ad hoc
  Android string inspection

For the faceplate legends specifically, Android follows the upstream GTK rule:
an underlined F or G label means that legend opens a menu, while a non-
underlined F or G label is a direct function. The underline lives in the
styling path only. Faceplate spacing, centering, and size rules stay shared.

## PiP interaction model

PiP mode is deliberately narrower than the normal shell. The overlay stops
drawing the full shell and maps horizontal touches across the LCD surface to the
six softkeys.

That is an interaction contract, not a reduced copy of the full keypad layout.

## Ownership of rendering decisions

When a visual rule is controlled by scene data, fix it in the native scene
contract. When it is controlled by Android-only projection or measurement, fix it
in the overlay, layout, or key renderer.

Use this split:

1. native scene data decides what a key means and which state is visible
2. `ReplicaKeypadLayout` decides where the key lives in logical space
3. `ReplicaOverlay` decides how logical space maps into the current window
4. `CalculatorKeyView` decides how one key is measured and drawn

When a change touches more than one layer, prefer fixing the highest true owner
first.

## Practical debugging rules

When a visual mismatch appears, locate it in one of four places before editing:

1. native scene data
2. logical keypad geometry
3. overlay projection
4. per-key drawing behavior

Use that order because a per-key patch is often wrong when the actual defect is
in scene metadata or logical geometry.

Prefer fixing the owning contract instead of adding per-key exceptions.

As a rule:

- wrong text or wrong mode state means native snapshot first
- every key shifted together means layout or projection
- one key drawn wrong with correct content means `CalculatorKeyView`

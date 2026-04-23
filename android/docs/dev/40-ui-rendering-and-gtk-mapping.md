# UI Rendering And GTK Mapping

## Logical shell model

The Android shell renders against a logical calculator canvas, not against
device pixels:

- shell size: `526 x 980`
- top bezel height: `72`
- LCD viewport: left `43`, top `60`, width `440`, height `264`

`ReplicaOverlay` projects that logical shell into the current window. In normal
mode it draws the body, bezel, LCD frame, and LCD bitmap with `Canvas`. In PiP
mode it draws the LCD bitmap full-window and maps horizontal touches across the
LCD to the six softkeys.

The overlay exposes two scaling modes:

- `full_width`: fit the logical shell inside the trimmed window frame
- `physical`: cap the fit scale to a physical-size target derived from display
  DPI

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

The keypad is scene-driven. Android does not poll per-label text from separate
bridge calls and does not use invisible image hit zones.

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

`ReplicaKeypadLayout` places all 43 keys using fixed logical coordinates on the
same shell model used by the overlay:

- one softkey row
- two small rows
- one enter row
- four large rows

The layout constants are GTK-derived geometry values expressed in the Android
logical shell space. The Android side does not recalculate key positions from
live GTK code.

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

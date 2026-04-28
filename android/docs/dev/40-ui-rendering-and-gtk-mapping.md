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
`r47_background` background shell image behind the scene-driven keypad labels
and softkey state text, or
draws the restored `r47_texture` classic image shell. `ReplicaKeypadLayout`
now owns one normalized shared touch-cell map across all chrome modes. The
grid uses contiguous row bands, shared midline boundaries, and consistent outer
keypad bounds inside each row group. `r47_texture` uses that map without
rendered key views, `native` keeps the full scene-driven key views, and
`r47_background` keeps the scene-driven labels and softkey state text without
Android-painted key surfaces on top of the same active-cell geometry.
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
  keeping the scene-driven label and softkey-state overlay on the shared
  touch-cell map and the texture-aligned LCD frame
- `native`: draw the body, bezel, and LCD frame with Android `Canvas` while
  keeping the same logical keypad geometry, settings-entry touch strip, and
  texture-aligned LCD frame

The native software shell now fills the rounded calculator body with
`RGB(31, 31, 31)`, drops the separate top and bottom bar pass, and keeps the
`48`-unit shared-shell corner radius before projection. The view background
outside that rounded silhouette stays black.

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

## Measured keypad geometry

`ReplicaKeypadLayout` places all 43 keys from one measured reference projection
in shared-shell space. `KeypadTopology` owns the Android-local 43-key lane,
family, and column map that this measured projection consumes for view
construction and touch-row membership. The live `R47MeasuredGeometry`
constants are:

- standard left: `38.727473`
- standard pitch: `78.610989`
- standard key body width: `55.490110`
- matrix first visible left: `134.390110`
- matrix pitch: `95.662637`
- matrix key body width: `65.894505`
- enter width: `133.523077`
- measured row height: `41.469292`
- row step: `74.875110`
- softkey row top: `371.495739`
- first small-row top: `446.370849`
- enter-row top: `596.121070`
- first large-row top: `670.996180`

The row families resolve as:

```text
softkey_x(c) = 38.727473 + 78.610989 * c      where c = 0..5
softkey_y = 371.495739

small_row_x(c) = 38.727473 + 78.610989 * c    where c = 0..5
small_row_y(r) = 446.370849 + 74.875110 * r   where r = 0..1

enter_key_x = 38.727473
enter_key_y = 596.121070
enter_key_width = 133.523077
enter_row_small_x(c) = 38.727473 + 78.610989 * c   where c = 2..5

large_row_left_x = 38.727473
large_row_matrix_x(c) = 134.390110 + 95.662637 * c where c = 0..3
large_row_y(r) = 670.996180 + 74.875110 * r        where r = 0..3
```

Rendered slot and body rules:

- small-row and enter-row small keys use a `78.610989` slot and a `55.490110`
  painted dark-key body inside a `68`-unit view height
- key `13` uses a measured `133.523077` width and is not re-derived from a
  nominal two-column gap model
- the lower left column uses a `55.490110` body width and the visible matrix
  keys use a `65.894505` body width inside `95.662637` slots
- the softkey row uses `59.490110 x 45.469292` slots, but its `2 px` inset on
  each side resolves the painted cap back to `55.490110 x 41.469292`, the same
  painted cap size as the standard dark keys

The touch-cell map follows the same measured geometry. The upper keypad uses a
`4 x 6` grid with the enter key spanning two columns, the lower keypad uses a
`4 x 5` grid, all row bands have height `74.875110`, and the texture shell uses
that same map after scaling from shared-shell space. Android still maps GTK
font roles and label semantics, but the live coordinates now come from this
measured projection rather than from a copied GTK screen layout.

## Per-key renderer

Each key is a `CalculatorKeyView`. The view combines a painted key surface,
label views, and softkey-specific drawing logic.

Softkeys stay on a dedicated function-key renderer path because the native
scene contract carries reverse-video, overlay, preview, and value-state rules
that the main-key path does not.

It renders:

- primary label inside the painted key body
- F and G faceplate labels above the painted key body
- the fourth label from the right edge of the painted key body
- softkey text, auxiliary text, value text, preview accents, reverse-video
  states, dotted rows, and overlay-state decorations when the scene contract
  asks for them

Main keys and softkeys share one view class, but the renderer separates the
layout slot from the painted body geometry.

Current native key-surface contract:

- default dark key fill is `RGB(63, 63, 63)`
- F accents use `RGB(242, 171, 94)` for faceplate labels, F-shift key fills,
  and the combined FG shift fill
- G accents use `RGB(131, 183, 223)` for faceplate labels and G-shift key
  fills
- those accent values come from the standard HSL lightness adjustment path:
  keep hue and saturation, then raise lightness by `10` points
- the Android touch path keeps no separate hover palette; F/G/FG styles use
  dedicated brighter pressed fills for touch feedback, while alpha keeps its
  base accent fill
- reverse-video states keep their state-specific fill colors
- main keys draw as plain rounded fills with no extra border, top bar, or
  bottom bar
- main-key corner radius is `6 * button_scale`
- softkey corner radius is `16 px`

For main keys, the painted body rectangle is derived from the live measured row
height and the per-family width bonus:

```text
button_scale = buttonView.width / design_button_width
inset = button_scale
half_width_bonus = (button_visual_width_bonus * button_scale) / 2

mainKeyRect.left = max(buttonView.left + inset - half_width_bonus, inset)
mainKeyRect.top = buttonView.top + inset
mainKeyRect.right = min(buttonView.right - inset + half_width_bonus,
                        view_width - inset)
mainKeyRect.bottom = buttonView.bottom - inset
```

That `mainKeyRect` then drives the label placement rules:

```text
button_center_x = mainKeyRect.centerX()
raw_button_center_x = buttonView.left + buttonView.width / 2
primary_translation_x = button_center_x - raw_button_center_x

gap = 3 * cell_scale
group_width = measured_f_width + gap + measured_g_width
group_left = mainKeyRect.centerX() - group_width / 2

letter_left = mainKeyRect.right + 3 * cell_scale
            + 0.2 * measured_letter_width
letter_top = 18 * cell_scale
           + 0.25 * measured_letter_height
```

The primary label is centered on the painted body, the `f` plus `g` pair is
centered as one group on that same body centerline, and the fourth label is
anchored from the painted body right edge rather than from the middle of the
spare lane.

For softkeys, the slot is intentionally larger than the painted body:

```text
softkey_view_width = STANDARD_KEY_WIDTH + 4
softkey_view_height = ROW_HEIGHT + 4
softkey_rect = [2, 2, width - 2, height - 2]

painted_softkey_width = STANDARD_KEY_WIDTH
painted_softkey_height = ROW_HEIGHT
```

Softkeys therefore keep the same painted cap width and height as the standard
dark keys while using a dedicated interior drawing path for text, value fields,
pagination dots, overlays, and reverse-video states.

When auxiliary text is visible, the softkey primary legend anchors in the upper
band at `softkeyRect.top + 0.28 * softkeyRect.height()` instead of using the
lower centered baseline.

The Android settings theme reuses the same lifted F/G palette through
`colorPrimary = RGB(242, 171, 94)` and `colorSecondary = RGB(131, 183, 223)`.
It also keeps the upstream role split between `colorPrimary` and the blue
container or activated roles so the slider does not collapse to one color.

Typography and style come from scene data plus staged calculator fonts. Android
chooses how to measure and draw those roles; native code chooses which roles are
active.

For non-softkeys, inspect `CalculatorKeyView` first when label centering
diverges from the measured key body rather than from the full cell bounds.

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
2. `KeypadTopology` decides the Android-local row, family, and column contract
  for each key code
3. `ReplicaKeypadLayout` decides where the key lives in logical space
4. `ReplicaOverlay` decides how logical space maps into the current window
5. `CalculatorKeyView` decides how one key is measured and drawn

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

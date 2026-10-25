# Batch 10 — Accent palettes (THM-02)

## Session prompt

```text
1.0.0 batch 10 (THM-02): Accent palettes.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-10-palettes.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Replace the one-seed generated schemes with hand-designed palettes for light and dark: accent, a stronger selected colour and a fixed callout set (TIP green-teal, blue for Teal/Green/Lime). The colour circles preview the real palette.

## Entries

### THM-02: Overhauled accent palettes

**Problem:** In theme customization (Screenshot_20261024-143847, -144006, -144012) the user picks Material You or one of the accent colors. Each accent only sets one color, and the app works out all the others by itself. The results do not fit together well:

- The TIP boxes get a color that was never chosen on purpose. With Violet it is an orange-brown box with violet links on it, in dark mode (Screenshot_20261024-144037) and in light mode (Screenshot_20261024-144028).
- With Material You in dark mode, the TIP box (Screenshot_20261024-143452) is a blue-grey that hardly differs from the cards and from the selected subsection.
- The selected color (selected bubbles, buttons, the current item in the bottom bar) and the TIP boxes are not distinct enough from each other.

**Desired behavior:**

- The way of choosing stays as it is now: the Material You card, the Accent card with its color circles, and a custom color.
- Every accent palette is designed as a whole, by hand, for light and for dark mode. Each one has:
    - an **accent** for buttons, links, switches and the selected item,
    - a **selected** color for selected bubbles and options, clearly stronger than plain cards,
    - **callout colors** for TIP, NOTE, WARNING and DANGER boxes, taken from a separate, fixed set and not worked out from the accent.
- The TIP color is always clearly different from the accent, from the selected color and from the cards. TIP is always a calm green-teal, except with the Teal, Green and Lime accents, where it is blue. WARNING is always amber, DANGER always red.
- Links inside callout boxes are easy to read in every palette.
- Material You keeps the colors of the phone, but uses the same fixed callout colors (slightly adapted to the phone's colors) so TIP boxes always stand out.
- A custom color gets the callout set that is furthest away from it.
- The small color circles on the Accent and Material You cards show the real accent, selected and TIP colors of that palette, so the preview matches what the app looks like.
- Every palette is checked in light, dark and pure black, on a section page with a TIP box, the selected subsection, the bottom bar and settings.

**The palettes** (exact shades to be fine-tuned with screenshots):

|Palette|Accent|TIP box|
|---|---|---|
|Violet|Violet|Green-teal|
|Blue|Blue|Green-teal|
|Teal|Teal|Blue|
|Green|Green|Blue|
|Lime|Lime|Blue|
|Amber|Amber|Green-teal|
|Orange|Orange|Green-teal|
|Red|Red|Green-teal|
|Pink|Pink|Green-teal|
|Grey|Blue-grey|Green-teal|

**Things to check when implementing:**

- People who update keep their chosen palette and see the new version of it.
- Other colored parts that do not follow the theme today (the change type colors in What's new, the verdict colors in the link checker, the star color) still look right next to the new palettes.
- Color-blind users: the TIP box also has its icon and label, so it never relies on color alone.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-24 | Theme colors are chosen the same way as now, with the palettes overhauled and designed as a whole | THM-02 |
| 2026-10-25 | TIP boxes are green-teal, blue with the Teal, Green and Lime accents | THM-02 |

## Depends on / affects

- THM-01 (batch 11) does the light-mode pass on top of these palettes.
- HOME-03 (batch 16) switches the search bar to the primary colour.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/theme/ColorSchemes.kt` — `AccentPreset` (~L11), `ACCENT_PRESETS` (~L13), `seedColorScheme` (~L21, everything derived from one seed), `toPureBlack` (~L82)
- `app/src/main/java/io/github/omeg4dev/fmhy/theme/Theme.kt` — `FMHYTheme` (~L26), scheme choice (~L38-42)
- `app/src/main/java/io/github/omeg4dev/fmhy/data/AppearancePrefs.kt` — `ColorSource` (~L4, CUSTOM is only implied by a seed outside the presets), `Appearance.accentSeed` (~L11)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/CalloutBox.kt` — `containerColor` (~L21), `borderColor` (~L26), `CalloutBox` (~L29), `accentColor` (~L31)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/AppearancePage.kt` — Accent card (~L154-232, circles ~L212), Material You card (~L234-296, circles ~L287), swatches (~L318-350), custom colour (~L367)
- Note: People who update keep their palette: map today's stored seed to the new palette of the same name.

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.theme.ColorSchemesTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.theme.CalloutColorsTest` (:app): TIP differs from accent, selected and cards; furthest callout set for a custom colour

## Done when

- [ ] THM-02: The way of choosing stays as it is now: the Material You card, the Accent card with its color circles, and a custom color.
- [ ] THM-02: Every accent palette is designed as a whole, by hand, for light and for dark mode.
- [ ] THM-02: The TIP color is always clearly different from the accent, from the selected color and from the cards.
- [ ] THM-02: Links inside callout boxes are easy to read in every palette.
- [ ] THM-02: Material You keeps the colors of the phone, but uses the same fixed callout colors (slightly adapted to the phone's colors) so TIP boxes always stand out.
- [ ] THM-02: A custom color gets the callout set that is furthest away from it.
- [ ] THM-02: The small color circles on the Accent and Material You cards show the real accent, selected and TIP colors of that palette, so the preview matches what the app looks like.
- [ ] THM-02: Every palette is checked in light, dark and pure black, on a section page with a TIP box, the selected subsection, the bottom bar and settings.
- [ ] THM-02: (exact shades to be fine-tuned with screenshots):

## Out of scope

- Light-mode contrast fixes outside the palettes (THM-01, batch 11).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143452.png`
- `Screenshot_20261024-143847.png`
- `Screenshot_20261024-144006.png`
- `Screenshot_20261024-144012.png`
- `Screenshot_20261024-144028.png`
- `Screenshot_20261024-144037.png`

# Batch 11 — Light mode pass (THM-01)

## Session prompt

```text
1.0.0 batch 11 (THM-01): Light mode pass.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-11-light-mode.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `sonnet`), then verify and finish yourself.
```

## Goal

Bring light mode up to dark mode: visible accent fills for selected items, cards that stand apart from the background, calm callouts, a solid floating back-to-FMHY button and readable search highlights, with at least 4.5:1 text and 3:1 icon/edge contrast.

## Entries

### THM-01: Light mode brought up to the level of dark mode

**Problem:** Light mode got less attention than dark mode during development. Seen in the light mode screenshots (Screenshot_20261024-144006 to -144108, accent Violet):

- The icon circles in settings, the selected item in the bottom bar, the selected bubble, the "All" filter in Saved and the "Browse FMHY" button use a pale violet with dark violet on top. They look washed out and have little contrast against the almost white background.
- The TIP box is a heavy orange-brown block that dominates the whole page.
- The floating back-to-FMHY button in the browser is a pale violet spot that is hard to see on bright web pages.
- Cards (the subsection card, the settings groups, the Random pick card) are nearly the same color as the background, so the parts of a screen blend into each other.
- Search highlights are a pale violet behind black text and are easy to miss.

**Desired behavior:**

- Every screen gets the same care in light mode as in dark mode: home, section pages (both styles), search, saved sites, What's new, settings and all their pages, theme customization, the first-run setup, the browser start screen, web pages with the shield panel and the FMHY status popup open, the entry menu, dialogs and messages.
- Text has a contrast of at least 4.5 to 1 against its background, icons and the edges of buttons and switches at least 3 to 1.
- Selected and highlighted things use a clearly visible accent fill, not a pale tint.
- Cards and groups stand out from the background through a slightly different shade or a fine outline.
- Callout boxes (TIP and the others) are light and calm in light mode and never become large saturated blocks (see THM-02).
- The floating back-to-FMHY button has a solid background and a light shadow so it can be seen on any page.
- Light mode is checked with every palette and with Material You.

**Things to check when implementing:**

- Switching between light and dark while the app is open.
- System theme with "System" selected, and a very light or very dark wallpaper with Material You.
- Bright web pages under the browser bar, and the bar at the bottom.
- The home-screen widget and the app icons in light mode.

## Depends on / affects

- THM-02 (batch 10) provides the new palettes and callout colours.
- Screens built later (website style, settings re-sort, downloads) are checked in light mode by their own batches and QA-01 (batch 24).

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/theme/ColorSchemes.kt` — the light branch of the palette builder from batch 10 (`secondaryContainer`, `surfaceContainer*`)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRow.kt` — `hlColor`/`hlBackground` (~L177-178, primary at alpha 0.28), `parseHighlight` (~L60); used in `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` (~L748, ~L931)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/CalloutBox.kt` — `containerColor` (~L21)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BackBubble.kt` — `BackBubble` (~L64), `contentColor` (~L175)
- Selected/highlight tints: `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionItems.kt` (~L127), `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionScreen.kt` (~L620); other `secondaryContainer`/`primaryContainer` uses are scattered (find with `rg -n 'secondaryContainer|primaryContainer' app/src/main`)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.theme.ColorSchemesTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.theme.ContrastTest` (:app): WCAG ratios for the light pairs of every palette

## Done when

- [ ] THM-01: Every screen gets the same care in light mode as in dark mode: home, section pages (both styles), search, saved sites, What's new, settings and all their pages, theme customization, the first-run setup, the browser start screen, web pages with the shield panel and the FMHY status popup open, the entry menu, dialogs and messages.
- [ ] THM-01: Text has a contrast of at least 4.5 to 1 against its background, icons and the edges of buttons and switches at least 3 to 1.
- [ ] THM-01: Selected and highlighted things use a clearly visible accent fill, not a pale tint.
- [ ] THM-01: Cards and groups stand out from the background through a slightly different shade or a fine outline.
- [ ] THM-01: Callout boxes (TIP and the others) are light and calm in light mode and never become large saturated blocks (see THM-02).
- [ ] THM-01: The floating back-to-FMHY button has a solid background and a light shadow so it can be seen on any page.
- [ ] THM-01: Light mode is checked with every palette and with Material You.

## Out of scope

- New palettes (THM-02, batch 10).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-144006.png`
- `Screenshot_20261024-144012.png`
- `Screenshot_20261024-144019.png`
- `Screenshot_20261024-144028.png`
- `Screenshot_20261024-144037.png`
- `Screenshot_20261024-144056.png`
- `Screenshot_20261024-144101.png`
- `Screenshot_20261024-144105.png`
- `Screenshot_20261024-144108.png`

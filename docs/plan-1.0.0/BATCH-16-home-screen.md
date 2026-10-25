# Batch 16 — Home screen (HOME-01, HOME-02, HOME-03)

## Session prompt

```text
1.0.0 batch 16 (HOME-01, HOME-02, HOME-03): Home screen.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-16-home-screen.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `sonnet`), then verify and finish yourself.
```

## Goal

Random pick becomes one slim row, pinned sections become two equal columns of compact tiles with a hold menu instead of ×, and the search bars on Home and Search use the accent colour.

## Entries

### HOME-01: Slim Random pick row

**Problem:** The Random pick on the home screen is a large card (Screenshot_20261024-143616, -144019) with a header line, the site's name, its description and its category on separate lines. It pushes the pinned sections and the wiki list far down.

**Desired behavior:**

- Random pick becomes one slim row, about as high as a normal list row: the dice icon on the left in the accent color, the site's name in bold, its category in small grey text after it (cut off with "…" when too long), and the refresh button on the right.
- Tapping the row opens the site, holding it opens the entry menu, the same as an entry in the lists (UI-03). The refresh button picks a new site.
- The description is no longer shown on the home screen. It is in the entry menu.
- With large text sizes the row may wrap to two lines, never more.
- The "Show random pick" switch in settings stays.

**Things to check when implementing:** very long names, entries with several sites, the offline wiki not loaded yet, light, dark and pure black.

### HOME-02: Space-saving pinned sections

**Problem:** Pinned sections take one full row each, because each tile is as wide as its name (Screenshot_20261024-143732, -143738). With six pins, the pinned area fills more than half the screen. The tiles have different widths, the text sits higher than the icon, and every tile has its own remove button.

**Desired behavior:**

- Pinned sections are shown in two equal columns of compact tiles. All tiles have the same width and height.
- Each tile has the section icon on the left and the name on the right, both centered vertically.
- For a pinned subsection, the tile shows the subsection's name ("VPN") and, in smaller text below it, the main section ("Adblocking / Privacy"). A pinned main section only shows its name. Names that are too long are cut off with "…".
- Six pins take three rows instead of six.
- Tapping a tile opens the section. Holding a tile opens a small menu with "Unpin" and "Follow" or "Unfollow" (UI-05). The remove button on each tile goes away to save space.
- An odd number of pins leaves the last spot of the last row empty.

**Decided details**

- Removing a pin moves from the × on each tile to the hold menu (and the "Pinned" list in settings).

**Things to check when implementing:** one pin, many pins, very long names, the largest text size (the tiles may grow to two lines but stay in two columns), small screens, and landscape and tablets (more columns are fine there).

### HOME-03: More vibrant search bar

**Problem:** The search bar on the home screen and on the search screen uses a muted secondary color (Screenshot_20261024-143616, -143754). It looks a bit boring and does not stand out as the main way into the app.

**Desired behavior:**

- The search bar uses the primary (accent) color instead of the secondary color: a soft accent-tinted background, the search icon in the accent color, and a clear accent outline while it is in use.
- The placeholder text ("Search 21,827 sites", "Search FMHY…") stays easy to read.
- The home search bar and the search screen's bar look the same.
- It follows the chosen palette and Material You, in light, dark and pure black (see THM-01 and THM-02).

**Things to check when implementing:** contrast of the placeholder and the typed text in every palette, the clear button, and that the search bar does not look like a selected button.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-24 | Random pick becomes a slim one-line row | HOME-01 |
| 2026-10-24 | Pinned sections in two compact columns, icon left, name right | HOME-02 |
| 2026-10-24 | The search bar uses the primary color instead of the secondary color | HOME-03 |
| 2026-10-25 | Pins are removed by holding the tile, no × on the tiles | HOME-02 |

## Depends on / affects

- UI-03 (batch 14) defines tap = open, hold = menu.
- UI-05 (batch 13) provides the Follow/Unfollow and Unpin hold menu.
- THM-01/THM-02 (batches 10-11) provide the palettes the search bar follows.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeScreen.kt` — random pick (~L294-344, `item(key = "random-pick")` ~L296, reroll ~L338), pinned block (~L425-496, `pins-block` ~L455, `state.pins.forEach` ~L472, undo "Unpinned" ~L496), search pill (~L223-247)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeViewModel.kt` — `randomPick` (~L211), `refreshRandomPick` (~L342), pins state
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchBarField.kt` — `SearchBarField` (~L78, colours ~L105-145); used in `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` (~L274)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.home.HomeViewModelTest' --tests 'io.github.omeg4dev.fmhy.ui.home.PinOrderTest' 2>&1 | tail -40
```

## Done when

- [ ] HOME-01: Random pick becomes one slim row, about as high as a normal list row: the dice icon on the left in the accent color, the site's name in bold, its category in small grey text after it (cut off with "…" when too long), and the refresh button on the right.
- [ ] HOME-01: Tapping the row opens the site, holding it opens the entry menu, the same as an entry in the lists (UI-03).
- [ ] HOME-01: The description is no longer shown on the home screen.
- [ ] HOME-01: With large text sizes the row may wrap to two lines, never more.
- [ ] HOME-01: The "Show random pick" switch in settings stays.
- [ ] HOME-02: Pinned sections are shown in two equal columns of compact tiles.
- [ ] HOME-02: Each tile has the section icon on the left and the name on the right, both centered vertically.
- [ ] HOME-02: For a pinned subsection, the tile shows the subsection's name ("VPN") and, in smaller text below it, the main section ("Adblocking / Privacy").
- [ ] HOME-02: Six pins take three rows instead of six.
- [ ] HOME-02: Tapping a tile opens the section.
- [ ] HOME-02: An odd number of pins leaves the last spot of the last row empty.
- [ ] HOME-03: The search bar uses the primary (accent) color instead of the secondary color: a soft accent-tinted background, the search icon in the accent color, and a clear accent outline while it is in use.
- [ ] HOME-03: The placeholder text ("Search 21,827 sites", "Search FMHY…") stays easy to read.
- [ ] HOME-03: The home search bar and the search screen's bar look the same.
- [ ] HOME-03: It follows the chosen palette and Material You, in light, dark and pure black (see THM-01 and THM-02).

## Out of scope

- Settings re-sort of Home options (SET-01, batch 23).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143616.png`
- `Screenshot_20261024-143732.png`
- `Screenshot_20261024-143738.png`
- `Screenshot_20261024-143754.png`
- `Screenshot_20261024-144019.png`

# Batch 06 — Browser start screen (BUG-06, BRW-04)

## Session prompt

```text
1.0.0 batch 06 (BUG-06, BRW-04): Browser start screen.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-06-browser-start-screen.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

The address bar works on the start screen exactly as on a page. The start screen becomes a calm, FMHY-branded page with the logo, a real search field, saved-site tiles, a Random pick tile, a grouped Recent list and its own private-tab version.

## Entries

### BUG-06: Address bar cannot be tapped on the browser start screen

**Problem:** When the user opens the browser directly and sees its start screen (no website opened yet), tapping the address bar does nothing. It only works after a website has been opened. The bar shows "Search or type link", so it looks tappable and this feels broken. It is also the first thing a user tries.

**Desired behavior:**

- Tapping the address bar on the start screen works exactly as on a web page: the keyboard opens right away and the user can type a search or a web address.
- The large "Search or type a link" text in the middle of the start screen is tappable too and does the same (see BRW-04, where it becomes a real search field).
- Parts of the bar that need a page, such as the safety indicator and the ad-block status, are hidden or clearly greyed out on the start screen, so nothing looks tappable that does nothing.
- This is the same in normal and private tabs, and with the bar at the top or the bottom.

**Things to check when implementing:**

- Every way into the browser: tapping a link in the app, sharing a link to the app, the new-tab button, the home-screen shortcut, and going back to the start screen from a page.
- Switching between a tab with a page and a tab with the start screen.
- Typing with the keyboard open while the bar is at the bottom.

### BRW-04: New browser start screen

**Problem:** The screen the user sees when opening the browser without a page is plain and mostly empty. In the current version it shows one icon in the middle, the text "Search or type a link", and either "Sites you save show up here." or a simple "Recent" list with a "Clear" link. There is little to look at or tap, and the address bar on it does not even respond (see BUG-06).

**Desired behavior:**

- A calm, centered layout: the FMHY logo (see ONB-01 for the images) and below it a large search and address field. This field is the real address bar: tapping it opens the keyboard right away (see BUG-06). With the bar at the bottom, the field sits within thumb reach.
- Saved sites as a grid of tiles (site icon and name), the first eight, with "Show all". Tapping a tile opens the site.
- A "Random pick" tile sits next to the saved sites and opens a surprise site from FMHY, the same function as Random pick in the main app. It is also shown for new users with nothing saved, so the screen is never empty.
- Branded like FMHY: the FMHY logo and artwork from ONB-01 at the top, and small touches in the website's light blue, so the screen clearly feels like FMHY and not like a generic browser. Buttons and highlights still follow the user's theme and accent color.
- For new users with nothing saved, the dead hint "Sites you save show up here" is replaced by a friendly message and a button that leads back to the FMHY lists ("Find sites on FMHY").
- "Recent" is a short list grouped by site, so each site appears once, newest first, with its icon, name and how long ago. "Clear" asks for confirmation, and holding an entry removes just that one. In the current screenshot the same site name appears twice in "Recent"; if these are the same site, it should show once.
- Private tabs get their own start screen: a clear "Private tab" mark and one sentence on what is kept apart. No saved sites and no recent list.
- Works in light, dark and pure black and follows the accent color. Nothing big is left empty on tall or small screens, and nothing is cut off with large text.
- It opens instantly and needs no network, so it also works offline and with "Browse only on Wi-Fi" (see BRW-03).

**Things to check when implementing:**

- New users with nothing saved, users with many saved sites, and a long history with many entries from one site.
- Normal and private tabs.
- Bar at the top and at the bottom, small phones, large text.
- The start screen after going back from the first page of a tab.
- The "Random pick" tile when the offline wiki is not loaded yet.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | The start screen has the logo and search field, saved sites grid, recent list and a Random pick tile, branded in FMHY style. No "Check a link" tile | BRW-04 |

## Depends on / affects

- ONB-01 (batch 17) bundles more FMHY artwork; use the bundled `fmhy_logo_hero` for now.
- BRW-03 (batch 18) must never show its Wi-Fi message on this screen; keep it network-free.
- BRW-09 (batch 09) closes tabs that only show this screen.
- HOME-01 (batch 16) only restyles Random pick on Home; reuse the existing random-pick logic.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` — `isBlankTab` (~L310) and the blank-tab branch (~L312)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserEditMode.kt` — today's start screen is the edit-mode empty state: "Search or type a link" (~L278), "Sites you save show up here.", "Recent" (~L327), "Clear" (~L337)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserToolbar.kt` — `BrowserToolbar` (~L111), address field click handling
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeViewModel.kt` — `randomPick` (~L211), `refreshRandomPick` (~L342)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/UrlInput.kt` — `UrlInput.resolve` (~L9); logo: `res/drawable-nodpi/fmhy_logo_hero.png`

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.UrlInputTest' --tests 'io.github.omeg4dev.fmhy.ui.home.HomeViewModelTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.RecentGroupingTest` (:app) for grouping Recent by site

## Done when

- [ ] BUG-06: Tapping the address bar on the start screen works exactly as on a web page: the keyboard opens right away and the user can type a search or a web address.
- [ ] BUG-06: The large "Search or type a link" text in the middle of the start screen is tappable too and does the same (see BRW-04, where it becomes a real search field).
- [ ] BUG-06: Parts of the bar that need a page, such as the safety indicator and the ad-block status, are hidden or clearly greyed out on the start screen, so nothing looks tappable that does nothing.
- [ ] BUG-06: This is the same in normal and private tabs, and with the bar at the top or the bottom.
- [ ] BRW-04: A calm, centered layout: the FMHY logo (see ONB-01 for the images) and below it a large search and address field.
- [ ] BRW-04: Saved sites as a grid of tiles (site icon and name), the first eight, with "Show all".
- [ ] BRW-04: A "Random pick" tile sits next to the saved sites and opens a surprise site from FMHY, the same function as Random pick in the main app.
- [ ] BRW-04: Branded like FMHY: the FMHY logo and artwork from ONB-01 at the top, and small touches in the website's light blue, so the screen clearly feels like FMHY and not like a generic browser.
- [ ] BRW-04: For new users with nothing saved, the dead hint "Sites you save show up here" is replaced by a friendly message and a button that leads back to the FMHY lists ("Find sites on FMHY").
- [ ] BRW-04: "Recent" is a short list grouped by site, so each site appears once, newest first, with its icon, name and how long ago.
- [ ] BRW-04: Private tabs get their own start screen: a clear "Private tab" mark and one sentence on what is kept apart.
- [ ] BRW-04: Works in light, dark and pure black and follows the accent color.
- [ ] BRW-04: It opens instantly and needs no network, so it also works offline and with "Browse only on Wi-Fi" (see BRW-03).

## Out of scope

- Auto-closing empty tabs (BRW-09, batch 09).
- The Wi-Fi message page (BRW-03, batch 18).
- New artwork files (ONB-01, batch 17).

## Screenshots

None named in the plan for this batch.

# Batch 02 — FMHY status popup (BRW-05, BRW-07)

## Session prompt

```text
1.0.0 batch 02 (BRW-05, BRW-07): FMHY status popup.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-02-status-popup.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `sonnet`), then verify and finish yourself.
Plan first and wait for my approval before you change any code.
```

## Goal

Replace the drop-down behind the FMHY status icon with a small popup in the style of the shield panel: verdict band, entry name and path, "Show in section" as the main button and "Entry details". "Show in section" leaves the browser and opens the app at the entry, once per listing place.

## Entries

### BRW-05: "Show in section" from the FMHY status button

**Problem:** The FMHY status icon in the address bar (the star for starred sites, or the icon for listed sites) opens a popup with the site name, its category path (for example "Adblocking / Privacy > Adblocking") and a button that only says "FMHY entry". There is no way to see where the entry sits in the FMHY lists. The app's "Show in section" function is not offered here.

**Desired behavior:**

- The popup gets a clear "Show in section" button. Tapping it leaves the browser, opens the app at the right section and subsection page, scrolls to the entry and highlights it (see BUG-08).
- The button appears for starred sites and for sites that are only listed.
- The browser tab stays open, so the user can continue where they left off.
- The existing "FMHY entry" button stays and is renamed "Entry details". Today it opens the same entry sheet as holding an entry in the lists (open, copy, share, save, mirrors, follow).
- If the site is listed in more than one place, the popup shows each place with its own "Show in section" button.
- The look of the popup itself is redesigned in BRW-07.

**Things to check when implementing:**

- Pages on mirror addresses and pages deeper inside a listed site.
- An entry that was moved or removed since the last sync (see BUG-08).
- Private tabs, the bar at the bottom, small screens and landscape.
- How the user returns to the browser tab afterwards.

### BRW-07: Redesigned FMHY status popup

**Problem:** Tapping the small FMHY status icon at the left of the address (the star for starred sites, the check mark for listed sites, the warning for unsafe sites) opens a popup that does not look like the rest of the app (Screenshot_20261024-143307). It looks like a plain drop-down menu: small grey text, a lot of empty space, and a lone text button "FMHY entry" in the corner. The shield panel next to it (Screenshot_20261024-143328) and the entry menu in the lists (Screenshot_20261024-143553) use cards, round icons and clear buttons, so the popup feels like it belongs to another app.

**Desired behavior:**

- It stays a small popup that opens right under the address bar, pointing to the icon it came from. It does not cover the whole page.
- **Verdict at the top:** a clear band with the status icon and a short verdict in the matching color, for example "Starred on FMHY", "Listed on FMHY", "Official FMHY site", "Not on FMHY", "Potentially unsafe" or "Unsafe site". The color and icon are the same as in the address bar and the link checker, so the meaning is learned once. Unsafe states use the error color, "Not on FMHY" stays neutral.
- Under it: the entry's name in bold and its place in the lists ("Adblocking / Privacy › Adblocking") on one line.
- Clear buttons in the style of the entry menu: "Show in section" (BRW-05) as the main button and "Entry details" next to it.
- For "Not on FMHY" one plain sentence: "This site is not on the FMHY list. That does not mean it is unsafe." For unsafe states, the reason from the unsafe list, without raw links (same rule as BUG-04).
- Same corners, spacing, colors and fonts as the shield panel and the entry menu, in light, dark and pure black.
- Tapping outside closes it. It never opens by itself.

**Things to check when implementing:**

- Bar at the bottom: the popup then opens above the bar.
- Long entry names and long category paths, the largest text size, small screens and landscape.
- Sites listed in several places (BRW-05) and mirror addresses.
- Screen readers read the verdict first.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-24 | The FMHY status popup stays a small popup under the address bar, with the verdict at the top | BRW-07 |

## Depends on / affects

- BUG-08 (batch 12) makes the jump land exactly and adds the strong highlight; use the existing `SectionNavKey(sectionId, anchor, focusEntryId)` jump here.
- BUG-04 (batch 05) shows unsafe notes without raw links; build that link stripping here as a shared helper so batch 05 can reuse it.
- THM-01 (batch 11) checks this popup in light mode again.
- Parking lot (not for 1.0.0): a tappable category path.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` — status icon (~L604-620), `DropdownMenu` (~L626), "FMHY entry" button (~L668-670, sets `showDetailSheet`), `statusGlyphVector` (~L704), `statusToneColor` (~L713)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/SiteBarModel.kt` — `SiteBarKind` (~L7), `SiteBarModel.from` (~L25), `statusIconSpec` (~L115), `statusTitle` (~L126): verdict texts and colours
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/ShieldSheet.kt` — `ShieldSheet` (~L42) and `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryDetailSheet.kt` ("Show in section" row ~L319-341): visual references
- Jump pattern: `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeScreen.kt` (~L682-690) and `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` (~L578-588) resolve the anchor and push `SectionNavKey`
- Browser → app: `app/src/main/java/io/github/omeg4dev/fmhy/MainActivity.kt` `handleIntent` (~L80) and `app/src/main/java/io/github/omeg4dev/fmhy/Navigation.kt` (~L205-213, `EXTRA_OPEN`); `app/src/main/java/io/github/omeg4dev/fmhy/shortcuts/ShortcutIntent.kt` `shortcutDestination` (~L25). There is no "show entry" route from `BrowserActivity` yet (it only has `EXTRA_ENTRY_ID` ~L358)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.SiteBarModelTest' --tests 'io.github.omeg4dev.fmhy.shortcuts.ShortcutIntentTest' --tests 'io.github.omeg4dev.fmhy.nav.AppNavStateTest' 2>&1 | tail -40
```

## Done when

- [ ] BRW-05: The popup gets a clear "Show in section" button.
- [ ] BRW-05: The button appears for starred sites and for sites that are only listed.
- [ ] BRW-05: The browser tab stays open, so the user can continue where they left off.
- [ ] BRW-05: The existing "FMHY entry" button stays and is renamed "Entry details".
- [ ] BRW-05: If the site is listed in more than one place, the popup shows each place with its own "Show in section" button.
- [ ] BRW-05: The look of the popup itself is redesigned in BRW-07.
- [ ] BRW-07: It stays a small popup that opens right under the address bar, pointing to the icon it came from.
- [ ] BRW-07: **Verdict at the top:** a clear band with the status icon and a short verdict in the matching color, for example "Starred on FMHY", "Listed on FMHY", "Official FMHY site", "Not on FMHY", "Potentially unsafe" or "Unsafe site".
- [ ] BRW-07: Under it: the entry's name in bold and its place in the lists ("Adblocking / Privacy › Adblocking") on one line.
- [ ] BRW-07: Clear buttons in the style of the entry menu: "Show in section" (BRW-05) as the main button and "Entry details" next to it.
- [ ] BRW-07: For "Not on FMHY" one plain sentence: "This site is not on the FMHY list.
- [ ] BRW-07: Same corners, spacing, colors and fonts as the shield panel and the entry menu, in light, dark and pure black.
- [ ] BRW-07: Tapping outside closes it.

## Out of scope

- Exact landing and highlight (BUG-08, batch 12).
- A tappable category path (parking lot).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143307.png`
- `Screenshot_20261024-143328.png`
- `Screenshot_20261024-143553.png`

# Batch 09 — Tab groups and empty tabs (BRW-08, BRW-09)

## Session prompt

```text
1.0.0 batch 09 (BRW-08, BRW-09): Tab groups and empty tabs.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-09-tab-groups-empty-tabs.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `sonnet`), then verify and finish yourself.
Plan first and wait for my approval before you change any code.
```

## Goal

The list view of the tab overview groups two or more tabs of the same site into a folded group row. Tabs that only ever showed the start screen close by themselves when left, are reused for new tabs and never come back on restore.

## Entries

### BRW-08: Tabs from the same site are grouped

**Problem:** When many pages from one site are open (for example several streamed.pk or GitHub pages), the tab list gets long and it is hard to find a tab.

**Desired behavior:**

- In the list view of the tab overview, two or more tabs from the same site are put together in a group. A site with only one tab stays a normal row.
- A group is collapsed by default. Its row shows the site's icon, the site name and the number of tabs, for example "streamed.pk · 4 tabs", and a small arrow.
- Tapping the group unfolds it and shows its tabs below it, slightly indented. Tapping again folds it. Each tab inside works like a normal tab row (tap to open it, close button, swipe to close).
- The group that contains the tab currently on screen is marked the same way the current tab is marked today, so the user can see where they are even when it is folded.
- The group row has its own close button that closes all its tabs, with "Undo".
- Tabs belong to the same site when they share the site's main address. For example www.github.com and gist.github.com both count as github.com.
- Private tabs are grouped the same way, but only inside the private part of the list. Normal and private tabs are never mixed in one group.
- Searching the tabs shows matching tabs inside their groups, with those groups unfolded.
- When the last tabs of a group are closed and only one is left, the group turns back into a normal row.

**Decided details**

- Only the list view gets groups. The grid view stays as it is.
- Whether a group is folded or unfolded is remembered while the app is open. After a restart, all groups start folded.

**Things to check when implementing:** many groups, a group with very many tabs, closing tabs inside an unfolded group, "Close all tabs", "Restore tabs on start", and the tab limit (5, 10 or 20) counting every tab, not every group.

### BRW-09: Empty start-page tabs are closed automatically

**Problem:** Tabs that only show the browser start screen (no website opened) pile up over time, for example after opening a new tab and leaving it again. They fill the tab list and count against the tab limit.

**Desired behavior:**

- A tab that only shows the start screen is closed by itself as soon as the user leaves it: by switching to another tab, by going back to FMHY, or by closing the browser.
- The tab that is currently on screen is never closed, even if it shows the start screen.
- When the user opens a new tab while an empty one exists, the empty one is reused instead of making a second one.
- "Restore tabs on start" never brings back empty tabs.
- This applies to normal and private tabs.
- A tab counts as empty only if it never showed a website. A tab where the user went back from a page to the start screen still has history and stays.

**Things to check when implementing:** the last remaining tab being empty, opening the browser directly to the start screen, the tab count in the address bar updating right away, and the new-tab button.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-25 | Tabs from the same site are grouped in the list view, folded by default | BRW-08 |
| 2026-10-25 | Tabs that only show the start screen are closed automatically | BRW-09 |
| 2026-10-25 | Tab groups only in the list view, all folded after a restart | BRW-08 |

## Depends on / affects

- BUG-06/BRW-04 (batch 06) built the start screen that defines an "empty" tab.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabsLogic.kt` — `TabsLogic.group` (~L12, only normal vs private today), `domain` (~L16, a host, not the site's main address), `filter` (~L24)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabsSheet.kt` — `TabsRow` (~L84), `buildRows` (~L90), `TabsSheet` (~L103), `closeWithUndo` (~L142), `TabListRow` (~L348)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabModels.kt` — `TabState` (~L23), `SavedTab` (~L53): no "never showed a site" flag yet
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabManager.kt` — `restoreTabsIfNeeded` (~L337), `openTab` (~L392), `selectTab` (~L438), `closeTab` (~L501), tab-limit eviction (~L696)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabStorage.kt` — `saveTabsToStore` (~L94, filters only blank/about:blank), `selectLruVictim` (~L78)
- Tab limit / restore prefs: `app/src/main/java/io/github/omeg4dev/fmhy/data/Prefs.kt` (~L77-78), `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/BrowserPage.kt` (~L188-198)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.TabsLogicTest' --tests 'io.github.omeg4dev.fmhy.browser.TabManagerTest' --tests 'io.github.omeg4dev.fmhy.browser.TabLruTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.TabGroupingTest` (:app) for the site key (www.github.com and gist.github.com → github.com)
- `io.github.omeg4dev.fmhy.browser.EmptyTabPolicyTest` (:app) for close-on-leave, reuse and restore

## Done when

- [ ] BRW-08: In the list view of the tab overview, two or more tabs from the same site are put together in a group.
- [ ] BRW-08: A group is collapsed by default.
- [ ] BRW-08: Tapping the group unfolds it and shows its tabs below it, slightly indented.
- [ ] BRW-08: The group that contains the tab currently on screen is marked the same way the current tab is marked today, so the user can see where they are even when it is folded.
- [ ] BRW-08: The group row has its own close button that closes all its tabs, with "Undo".
- [ ] BRW-08: Tabs belong to the same site when they share the site's main address.
- [ ] BRW-08: Private tabs are grouped the same way, but only inside the private part of the list.
- [ ] BRW-08: Searching the tabs shows matching tabs inside their groups, with those groups unfolded.
- [ ] BRW-08: When the last tabs of a group are closed and only one is left, the group turns back into a normal row.
- [ ] BRW-09: A tab that only shows the start screen is closed by itself as soon as the user leaves it: by switching to another tab, by going back to FMHY, or by closing the browser.
- [ ] BRW-09: The tab that is currently on screen is never closed, even if it shows the start screen.
- [ ] BRW-09: When the user opens a new tab while an empty one exists, the empty one is reused instead of making a second one.
- [ ] BRW-09: "Restore tabs on start" never brings back empty tabs.
- [ ] BRW-09: This applies to normal and private tabs.
- [ ] BRW-09: A tab counts as empty only if it never showed a website.

## Out of scope

- Groups in the grid view (stays as it is).

## Screenshots

None named in the plan for this batch.

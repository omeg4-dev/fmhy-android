# Batch 14 — Swipe and tap gestures (UI-02, UI-03)

## Session prompt

```text
1.0.0 batch 14 (UI-02, UI-03): Swipe and tap gestures.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-14-gestures.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
```

## Goal

Swipe to bookmark becomes an opt-in switch; when off, swiping moves between subsection pages. Tap opens the site and hold opens the menu by default, with a "Swap tap and hold" switch and one-time hints for people who update.

## Entries

### UI-02: Swipe to bookmark becomes optional

**Problem:** Swiping an entry sideways saves it. This is the same gesture people expect for moving to the next or previous section, and feedback asked for swiping between sections.

**Desired behavior:**

- A new switch "Swipe to bookmark", off by default.
- **Off:** swiping left or right on a list moves between the pages of the section (each subsection is a page, the same as picking a subsection at the top, see UI-04). Saving is done from the entry's menu.
- **On:** swiping an entry saves it as before. Swiping between pages no longer works on lists.
- When the user switches it on, a notice appears: "Swipe to bookmark uses the left and right swipe, so swiping between sections stops working. You can still pick a subsection at the top." with "Turn on" and "Cancel".
- Every entry's menu always has "Save", whatever the setting.
- People who update start with the switch off, so swipe-to-save disappears at the update. A one-time hint on first start says that it is now optional and where to turn it on.

**Things to check when implementing:** sideways scrolling inside an entry (for example a long row of icons) still works, entries with icons, saving into folders.

### UI-03: Tap and hold behavior, with a swap switch

**Problem:** Feedback says the current behavior (tap shows a menu, hold opens the site) feels backwards.

**Desired behavior:**

- **Default:** tap opens the site, hold opens the menu (all sites of that entry with their mirrors and extra links, save, share, copy link).
- A new switch "Swap tap and hold". When on, tap opens the menu and hold opens the site, which is the previous behavior.
- Settings explain both behaviors in one short sentence each.
- When an entry lists several sites, a tap opens the first (main) site. Hold shows all of them.
- People who update get the new default. A one-time hint on first start says what changed and where the swap switch is.

**Things to check when implementing:** screen readers should offer both actions, and the first-run setup text about opening links should match the new default.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | A tap on an entry with several sites opens the first (main) site, hold shows all | UI-03          |

## Depends on / affects

- UI-04 (batch 13) made each subsection a page with a current-subsection line.
- HOME-01 (batch 16) uses the same tap/hold rule for Random pick.
- ONB-01 (batch 17) / the first-run text about opening links must match the new default.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/SwipeToSave.kt` — `SwipeToSave` (~L60), `SwipeActionRow` (~L112); used in `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionScreen.kt` (~L627), `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/CategoryScreen.kt` (~L501), `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` (~L572), `app/src/main/java/io/github/omeg4dev/fmhy/ui/whatsnew/WhatsNewSheets.kt` (~L257)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/SwipeDecision.kt` — `swipeThresholdPx` (~L7), `swipeActionOnRelease` (~L24)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRow.kt` — `combinedClickable` (~L300-310: tap = details, hold = open site today)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRowActions.kt` — `EntryPrimaryActionRow` (~L47), `EntrySiteActionRow` (~L156)
- Prefs: `app/src/main/java/io/github/omeg4dev/fmhy/data/Prefs.kt` `HomePrefs` (~L65), `app/src/main/java/io/github/omeg4dev/fmhy/data/SettingsRepository.kt` (keys ~L161-167); switches in `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/HomeListsPage.kt`; no one-time hint pref exists yet

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.common.SwipeDecisionTest' --tests 'io.github.omeg4dev.fmhy.data.SettingsRepositoryTest' --tests 'io.github.omeg4dev.fmhy.ui.settings.SettingsViewModelTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.ui.common.RowTapActionTest` (:app): tap/hold result for the swap flag and multi-site entries

## Done when

- [ ] UI-02: A new switch "Swipe to bookmark", off by default.
- [ ] UI-02: **Off:** swiping left or right on a list moves between the pages of the section (each subsection is a page, the same as picking a subsection at the top, see UI-04).
- [ ] UI-02: **On:** swiping an entry saves it as before.
- [ ] UI-02: When the user switches it on, a notice appears: "Swipe to bookmark uses the left and right swipe, so swiping between sections stops working.
- [ ] UI-02: Every entry's menu always has "Save", whatever the setting.
- [ ] UI-02: People who update start with the switch off, so swipe-to-save disappears at the update.
- [ ] UI-03: **Default:** tap opens the site, hold opens the menu (all sites of that entry with their mirrors and extra links, save, share, copy link).
- [ ] UI-03: A new switch "Swap tap and hold".
- [ ] UI-03: Settings explain both behaviors in one short sentence each.
- [ ] UI-03: When an entry lists several sites, a tap opens the first (main) site.
- [ ] UI-03: People who update get the new default.

## Out of scope

- Random pick row (HOME-01, batch 16).
- Settings descriptions and final placement (SET-01, batch 23).

## Screenshots

None named in the plan for this batch.

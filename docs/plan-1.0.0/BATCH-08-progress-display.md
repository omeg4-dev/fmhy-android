# Batch 08 — Progress display (BUG-05, BUG-07)

## Session prompt

```text
1.0.0 batch 08 (BUG-05, BUG-07): Progress display.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-08-progress-display.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Ad-block and wiki progress move to a thin line under the top bar that never covers the title. The percentage covers the whole job with named steps, never stalls silently, and settings show live progress, last update and "Update now".

## Entries

### BUG-05: Ad-block progress pill covers the title bar

**Problem:** While the ad-block lists download, a pill with "Downloading ad-block lists" and a percentage sits on top of the screen title in the top bar and hides it.

**Desired behavior:**

- The progress is shown below the bar, or as a thin progress line under it. It never covers the title or any button.
- It disappears when the download is finished.
- What the progress shows (percent, steps, what happens when it stalls) is described in BUG-07.

**Things to check when implementing:** bar at the bottom of the screen, small screens, large text sizes.

### BUG-07: Progress for ad-block and wiki updates is missing or stuck

**Problem:** When the app downloads the ad-block lists or syncs the FMHY wiki data, the progress is either not shown at all, or it gets stuck at around 80 or 82 percent (seen with the ad-block lists) and stays there. Users cannot tell whether the app is working, stuck or finished. Likely cause to check: the percentage only counts the download of the files, while the steps after it (unpacking, preparing the rules, saving) have no share in it and can take a long time, or one large list at the end is slow.

**Desired behavior:**

- The percentage covers the whole job from start to end: all lists or files, and also the steps after the download. It only reaches 100 percent when everything is really finished and usable, and it never goes backward.
- Each step has a name that is shown with the number, for example "Downloading ad-block lists (2 of 5)", "Preparing the rules", "Saving", and then "Done". Steps that cannot be measured show a moving indicator with the step name instead of a frozen number. Nothing ever stands still without saying what it is doing.
- The wiki sync shows what it is doing in the same way, for example "Updating the wiki (12 of 40 files)". The first download of a build without built-in wiki data also shows the size and the percentage.
- Short, quiet background updates may stay silent. Anything that takes longer than about a second, and anything the user starts by hand, shows its progress.
- If nothing moves for a while (for example 30 seconds), the message changes to "Taking longer than usual" with the buttons "Keep waiting" and "Cancel". When it really fails, the app says what failed and that it keeps using the data it already has, with the date of that data, and offers "Retry" and "Report this" (see APP-02).
- When it finishes, a short confirmation appears, for example "Ad-block lists updated", and then goes away. The shield panel and the settings show when each was last updated.
- Where it is shown: a thin progress line below the top bar that never covers the title (see BUG-05). Tapping it opens a small panel with the step names and "Cancel". The sync and ad-block rows in settings show the same live progress, the time of the last update and an "Update now" button.
- If an ad-block update and a wiki sync run at the same time, there is one combined progress line, and the panel shows both.
- The waiting states from BRW-03 use the same display, with the status "Waiting for Wi-Fi".
- The Download Manager (BRW-02) follows the same rules. If the size of a file is not known, it shows the amount downloaded so far and a moving bar instead of a made-up percentage.
- The progress can be read by screen readers and does not rely on color alone.

**Things to check when implementing:**

- Slow networks, very large lists, many lists at once, and a connection that drops and comes back in the middle.
- One list failing while the others succeed: the message must explain what was updated and what was not.
- Closing the app or locking the phone in the middle of an update, and what the progress shows on the next start.
- The first start of a build without built-in wiki data.
- That 100 percent is never shown while the app is still preparing or saving.
- Test with slow and unstable network settings in the bug hunt (see QA-01).

## Depends on / affects

- BRW-03 (batch 18) adds the "Waiting for Wi-Fi" state to this display.
- BRW-02 (batch 21a) follows the same progress rules for downloads.
- "Report this" comes with APP-02 (batch 19); leave it out.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/shell/StatusNoticeOverlay.kt` — `StatusNoticeOverlay` (~L39), ad-block pill (~L92-98)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/shell/StatusNotices.kt` — `AdblockSetupUiState.Downloading` (~L18), status mapping (~L49-50), `SyncBannerUiState` (~L24), `syncBannerFor` (~L31)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/adblock/AdblockService.kt` — `updateInternal` (~L107-125): progress is `0.9f * done / total` and nothing is reported after the download
- `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/adblock/FilterListUpdater.kt` — `update(..., onProgress)` (~L108-120, reports the index before each list)
- Wiki sync: `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/sync/FmhySync.kt`, `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/sync/FmhySyncIO.kt` (no progress callback yet), `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/SyncBanner.kt` `SyncBanner` (~L25), `app/src/main/java/io/github/omeg4dev/fmhy/FmhyApp.kt` launch sync (~L39-45)
- Settings rows: `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/BlockingPage.kt` (update button with percent ~L329-330), `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/DataPage.kt`

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.shell.StatusNoticesTest' --tests 'io.github.omeg4dev.fmhy.work.UpdateWorkerLogicTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.adblock.FilterListUpdaterTest' --tests 'io.github.omeg4dev.fmhy.data.sync.FmhySyncTest' 2>&1 | tail -40
```

New tests:
- A pure progress model (steps, weights, never backward, stall after 30 s), e.g. `io.github.omeg4dev.fmhy.ui.shell.JobProgressTest` (:app)

## Done when

- [ ] BUG-05: The progress is shown below the bar, or as a thin progress line under it.
- [ ] BUG-05: It disappears when the download is finished.
- [ ] BUG-05: What the progress shows (percent, steps, what happens when it stalls) is described in BUG-07.
- [ ] BUG-07: The percentage covers the whole job from start to end: all lists or files, and also the steps after the download.
- [ ] BUG-07: Each step has a name that is shown with the number, for example "Downloading ad-block lists (2 of 5)", "Preparing the rules", "Saving", and then "Done".
- [ ] BUG-07: The wiki sync shows what it is doing in the same way, for example "Updating the wiki (12 of 40 files)".
- [ ] BUG-07: Short, quiet background updates may stay silent.
- [ ] BUG-07: If nothing moves for a while (for example 30 seconds), the message changes to "Taking longer than usual" with the buttons "Keep waiting" and "Cancel".
- [ ] BUG-07: When it finishes, a short confirmation appears, for example "Ad-block lists updated", and then goes away.
- [ ] BUG-07: Where it is shown: a thin progress line below the top bar that never covers the title (see BUG-05).
- [ ] BUG-07: If an ad-block update and a wiki sync run at the same time, there is one combined progress line, and the panel shows both.
- [ ] BUG-07: The waiting states from BRW-03 use the same display, with the status "Waiting for Wi-Fi".
- [ ] BUG-07: The Download Manager (BRW-02) follows the same rules.
- [ ] BUG-07: The progress can be read by screen readers and does not rely on color alone.

## Out of scope

- "Waiting for Wi-Fi" (BRW-03, batch 18).
- Download progress (BRW-02, batch 21a).
- "Report this" (APP-02, batch 19).

## Screenshots

None named in the plan for this batch.

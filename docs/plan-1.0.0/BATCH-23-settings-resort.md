# Batch 23 — Settings re-sort (SET-01, SET-02)

## Session prompt

```text
1.0.0 batch 23 (SET-01, SET-02): Settings re-sort.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-23-settings-resort.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Re-sort the inside of every settings page to the table in SET-01, with one plain line under every setting, folded Advanced groups and a settings search that slides in on scroll-up. About gets a "GitHub repo" row.

## Entries

### SET-01: Re-sorted settings with search, descriptions and an Advanced group

**Problem:** The settings keep their basic structure, but what is inside the pages is not sorted well, and some settings are hard to find or appear twice (Screenshot_20261024-143842 to -143945):

- The Browser page links again to Video, Ad blocking & protection and Privacy, which already have their own buttons on the main settings screen.
- Swipe and tap behavior, the list look and haptic feedback are spread over "Appearance" and "Home & lists".
- History settings are under "Saved & history", but browsing data is under "Privacy".
- "Only on Wi-Fi" sits on the notifications page, although it will be part of the Wi-Fi options (BRW-03).
- Many settings have no explanation at all, for example "Block redirects without a tap", "Dead-site rescue" or "Show indexes".

**Desired behavior:**

- The structure stays exactly as it is now: headings on the main settings screen with buttons below them that open a page, and each button shows a short summary of the current state (as "Dark · Material You" does today). Only what is inside is sorted again.
- **New order** (pages marked "new" come with the entries named):

|Heading|Button|What is on the page|
|---|---|---|
|Look & feel|Appearance|Theme, pure black, accent colour (THM-02), text size, app icon|
|Look & feel|Home|Personal Home, show random pick (HOME-01), section order, pinned sections, hidden sections|
|Look & feel|Lists & gestures|Website style (UI-01), section layout, starred only, show indexes, NEW badges, swap tap and hold (UI-03), swipe to bookmark (UI-02), haptic feedback, icon legend|
|Browsing|Browser|Open links with, search engine, address bar position, back-to-FMHY button, FMHY status icon, dead-site rescue, maximum tabs, restore tabs on start, dark pages in dark theme, text size follows app, site settings|
|Browsing|Video|Unchanged: fullscreen orientation, double-tap to seek, keep screen on, picture-in-picture, autoplay, play in background, default speed|
|Browsing|Downloads (new, BRW-02, BRW-06)|Download folder, video button, security services and keys, hold until checked, automatic checks, FMHY status lines|
|Safety & privacy|Ad blocking|Block ads and trackers, Balanced, Strict or Custom, show blocked count, hide page elements, fix broken pages, update lists automatically, list status|
|Safety & privacy|Protection|Warnings for unsafe and potentially unsafe sites, Google Safe Browsing, block pop-ups, block redirects without a tap, block links that open apps, ask before downloads, warn about APK and EXE downloads|
|Safety & privacy|Privacy & history|Third-party cookies, cookie banners, ask sites not to track me, open new tabs as private, clear site data on exit, keep history of opened sites, clear history, clear browsing data|
|Updates|Following (new, UI-05)|The followed sections tree|
|Updates|Notifications|Notifications on or off, new sites in followed sections, changes to saved sites, new monthly posts|
|Updates|What's new|Open What's new|
|Updates|App updates (new, APP-01)|Automatic checks, check for updates, skipped version|
|Your data|Sync|Sync now, sync on launch, last synced, live progress (BUG-07)|
|Your data|Data usage (new, BRW-03)|The three Wi-Fi only switches|
|Your data|Backup & restore|Export backup, import backup|
|Help & about|Report a problem (new, APP-02)|The report form, offer to report crashes (APP-03)|
|Help & about|About|Everything it has today, plus the GitHub repo (SET-02)|

- "Saved & history" and "Data & sync" no longer exist as pages: history moves to "Privacy & history", backup gets its own button, and sync becomes "Sync".
- Nothing appears twice. The links from the Browser page to other pages are removed, and the per-site switches stay in the shield panel and in "Site settings".
- **One line under every setting:** each setting gets a short sentence that says what it does in plain words, for example "Block redirects without a tap: stops pages from sending you to another site unless you tapped something". For switches, the sentence describes what happens when it is on.
- **Advanced group:** settings that most people never touch move into a group "Advanced" at the bottom of their page, folded by default. Suggested: JavaScript and desktop site by default (Browser), filter lists, custom rules and allowed sites (Ad blocking), check interval (Notifications), detailed change history (What's new), "Forget per-site speeds" (Video).
- **Search field:** a search field at the top of the main settings screen that is hidden when settings open and only slides in when the user scrolls up (and hides again when scrolling down). It finds settings by their name, their one-line description and common words (for example "popup", "dark", "wifi"), including the ones in Advanced groups. Each result shows the setting's name and where it lives ("Safety & privacy › Protection"). Tapping a result opens that page, scrolls to the setting and highlights it the same way as BUG-08.

**Decided details**

- The Advanced group is a folded group at the bottom of each page, not a separate page on the main settings screen.
- "Hide cookie banners" goes to "Privacy & history", next to the other cookie setting, although Strict ad blocking also hides cookie notices.

**Things to check when implementing:**

- Every setting that exists today has a place in the new order. Nothing gets lost.
- Settings that are reset or moved keep their values after the update.
- The search with typos and with no results, and the highlight in light and dark mode.
- Links into settings from other places (for example "Open settings" on the Wi-Fi message page, the shield panel and the first-run setup) open the new page.

### SET-02: GitHub repo link in About

**Problem:** The About page (Screenshot_20261024-143945) has no direct way to the app's own project on GitHub, where the source code, releases and issues are.

**Desired behavior:**

- About keeps everything it shows today: "About FMHY", "Version", "Run setup again" and "Open fmhy.net links in the app".
- A new row "GitHub repo" with the GitHub icon and the summary "Source code, releases and issues". Tapping it opens github.com/omeg4-dev/fmhy-android.
- It opens with the user's choice from "Open links with" (in-app browser, custom tab or external browser).

**Things to check when implementing:** the F-Droid build shows the same row, and the row works offline with a clear message.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-24 | Settings keep their structure (headings with buttons), only the contents are re-sorted. Added: a search field that appears when scrolling up, a one-line description per setting, and an Advanced group | SET-01 |
| 2026-10-24 | About keeps everything it has and gets a GitHub repo row | SET-02 |
| 2026-10-25 | The Advanced group is folded at the bottom of each settings page. "Hide cookie banners" goes to Privacy & history | SET-01 |

## Depends on / affects

- Runs last so the settings from all earlier batches get sorted: website style (15), gestures (14), Following screen (13), Wi-Fi switches (18), app updates (20), downloads (21a/21b, 22), reports (19).
- BUG-08 (batch 12) highlight is reused for search results.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/SettingsHub.kt` — `SettingsHub` (~L46); sections "Look & feel" (~L117), "Browsing" (~L156), "Updates" (~L198), "Your data" (~L217), "About" (~L236)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/SettingsComponents.kt` — `SettingsSection` (~L86), `SwitchRow` (~L160), `NavRow` (~L207), `InfoRow` (~L259), `ExpandableSection` (~L326) for Advanced
- `app/src/main/java/io/github/omeg4dev/fmhy/NavigationKeys.kt` — `SettingsPage` (~L27), `SettingsPageNavKey` (~L40); routing `app/src/main/java/io/github/omeg4dev/fmhy/Navigation.kt` (~L498-551, `PROTECTION` also opens `BlockingPage` ~L520-521)
- Pages: `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/*Page.kt`; Browser page links to other pages (~L264-276 in `BrowserPage.kt`)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/AboutPage.kt` — rows (~L83-96) for SET-02
- Note: `ProtectionPage.kt` exists but nothing calls it, and "Ask before downloads" / "Warn about APK and EXE downloads" live in `BlockingPage.kt` today; check before moving them.

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.settings.SettingsViewModelTest' --tests 'io.github.omeg4dev.fmhy.data.SettingsRepositoryTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.ui.settings.SettingsSearchIndexTest` (:app): name, description and keyword matches, typos, Advanced items, result path

## Done when

- [ ] SET-01: The structure stays exactly as it is now: headings on the main settings screen with buttons below them that open a page, and each button shows a short summary of the current state (as "Dark · Material You" does today).
- [ ] SET-01: **New order** (pages marked "new" come with the entries named):
- [ ] SET-01: "Saved & history" and "Data & sync" no longer exist as pages: history moves to "Privacy & history", backup gets its own button, and sync becomes "Sync".
- [ ] SET-01: Nothing appears twice.
- [ ] SET-01: **One line under every setting:** each setting gets a short sentence that says what it does in plain words, for example "Block redirects without a tap: stops pages from sending you to another site unless you tapped something".
- [ ] SET-01: **Advanced group:** settings that most people never touch move into a group "Advanced" at the bottom of their page, folded by default.
- [ ] SET-01: **Search field:** a search field at the top of the main settings screen that is hidden when settings open and only slides in when the user scrolls up (and hides again when scrolling down).
- [ ] SET-02: About keeps everything it shows today: "About FMHY", "Version", "Run setup again" and "Open fmhy.net links in the app".
- [ ] SET-02: A new row "GitHub repo" with the GitHub icon and the summary "Source code, releases and issues".
- [ ] SET-02: It opens with the user's choice from "Open links with" (in-app browser, custom tab or external browser).

## Out of scope

- New features; this batch only moves, describes and searches settings.

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143842.png`
- `Screenshot_20261024-143847.png`
- `Screenshot_20261024-143853.png`
- `Screenshot_20261024-143900.png`
- `Screenshot_20261024-143906.png`
- `Screenshot_20261024-143910.png`
- `Screenshot_20261024-143913.png`
- `Screenshot_20261024-143918.png`
- `Screenshot_20261024-143923.png`
- `Screenshot_20261024-143927.png`
- `Screenshot_20261024-143940.png`
- `Screenshot_20261024-143945.png`

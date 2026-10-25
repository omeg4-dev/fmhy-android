# Batch 21b — Download Manager (video and checks) (BRW-02)

## Session prompt

```text
1.0.0 batch 21b (BRW-02): Download Manager (video and checks).
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-21b-download-manager-checks.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Add the video button for single-file videos, and the VirusTotal / Hybrid Analysis checks: link check before download, fingerprint-first file checks, optional hold-until-checked, automatic checks of risky types, and "Check a file from the phone".

## Entries

_Only the parts of BRW-02 that belong to this batch are copied._

### BRW-02: Built-in Download Manager
**Goal:** Files downloaded in the in-app browser are handled by the app itself instead of being handed to the system. Users get one place to see progress, control downloads and check files for malware. This fits the app's promise of no account, no tracking and no server of its own. The extras that tie it to FMHY (source status, checks of redirects) are described in BRW-06.

#### What counts as a download

- Any link the browser would normally offer as a file: APKs, zip files, PDFs, direct video files and so on.
- A "download this video" button for videos found on the page.
- Pasting a link directly into the download manager to download a file without opening the page first.

#### Starting a download

- Tapping a file link shows a short confirmation with file name, size (if known), source site and target folder. The user can confirm or cancel. This should be quick and not feel like a wall of dialogs.
- The confirmation also has a "Check link first" button. It looks up the web address with the chosen security service(s) and shows the result before any file is downloaded (see "Security checks" below). This works for pasted links too.
- Downloads carry over the browser's session information for that site (login state and the page it came from). Many sites refuse downloads without it.
- Private tabs: downloads started there are kept apart. They don't stay in the history after the app is closed, their notifications show a neutral text instead of the file name, and their check results are not kept either.

#### The video button

- A small icon appears in the address bar, next to the safety and ad-block indicator, only while the current page has a downloadable video. Otherwise it is hidden, so the bar stays clean.
- Tapping it lists the videos found (with quality or size if known) and lets the user pick one.
- Only video files that can be saved as a single file are supported. Streaming-style players that deliver video in many small pieces are not covered in this version. Players like the one on the anime site in the earlier screenshot may therefore show no button.
- The button can be turned off in the app's settings.

#### What each entry shows and does

- Name, source site, size, progress bar, speed and remaining time while active, and a clear status (waiting, waiting for Wi-Fi, downloading, paused, failed, being checked, waiting for your decision, finished).
- Active: pause and resume, cancel.
- Failed: retry. If the site doesn't allow resuming, retry starts the file again, and the entry says so.
- Finished: open, share, delete, and "Check file" (the user picks the service). For APK files there is also "Install".
- After a check, a small label stays on the entry showing service, date and verdict. It can be re-checked at any time, because results get old.

#### Security checks with VirusTotal and Hybrid Analysis

_Services and keys:_ In settings the user can turn on VirusTotal, Hybrid Analysis or both. Each needs a free key from that service, entered by the user. Without a key, the check button opens the service's own web page for that link or file in the in-app browser, so the feature still works, just not inside the sheet.

_Three moments for a check:_

1. **Before the download starts (link check).** The "Check link first" button on the start confirmation checks the web address only, before any file is downloaded. A link nobody has scanned yet shows as "unknown", which does not mean safe. Some links contain personal or one-time codes, so the web address is only sent after the user taps the button, with a short note saying so.
2. **After downloading, before the file is released (optional "hold until checked" mode).** This is a setting, off by default. When on, new files finish into a hidden holding area that other apps can't see. The app checks the file, shows the result, and the user taps "Keep" (the file moves to the chosen folder) or "Delete". If the service doesn't know the file, the user can still keep it or upload it for analysis.
3. **Any time later.** Every finished entry has a "Check file" button. A separate "Check a file from the phone" option opens the system file picker, for files downloaded earlier or in other apps.

_How a file check works:_ The app calculates the file's fingerprint on the phone and sends only that, not the file. If the service knows the file, the result appears right away. If not, the app asks whether to upload it for analysis. That prompt warns that uploaded files may be shared with the service's security partners and researchers, so private files should not be uploaded. New analyses can take a few minutes. The entry shows "analysis running" and a notification arrives when it's done. If a file is too big to upload, the app says so and offers to open the service's web page instead.

_Results:_ A plain-words verdict with four levels: no flags, suspicious, dangerous, unknown. Under it, one line per service with its own numbers (for example "0 of 70 engines flagged this"), the date of the scan and a link to the full report. A short reminder states that "no flags" is not a guarantee and "unknown" is not "safe".

_If a file is flagged as dangerous:_ The warning is shown in red and "Delete" is offered first. "Open" and "Install" stay available but need an extra confirmation. Nothing is ever deleted automatically.

_Automatic checks:_ A setting, on by default, runs a check automatically on risky file types (APKs, executables, scripts) after download, once a key is set up. Everything else is manual, because every check sends a fingerprint or web address to a third party. The settings page and the one-time explanation state exactly what gets sent, and the README must say so too, next to the no-tracking promise.

_Limits:_ Free keys have usage limits. When one is reached, or a key is wrong, the app says so in plain words and offers to open the service's web page instead.

#### Decided details

- Three downloads run at the same time. Extra ones wait in line.
- Checks send the file's fingerprint first. A file is only uploaded after the user was asked and warned.
- "Hold until checked" is off by default. Automatic checks of risky file types (APKs, executables, scripts) are **on** by default.
- Without a key, the automatic check cannot run. The first risky download explains this once and offers "Set up a key" and "Not now". Nothing is sent until a key is set up.
- The verdict has four levels: no flags, suspicious, dangerous, unknown.
- "Check a file from the phone" works for any file picked with the file picker, not only files downloaded by the app.
- Private-tab downloads and their check results are not kept after a restart. The files themselves stay in the folder.
- Clearing the list never deletes the files, and deleting a file always asks first.

#### Out of scope for now

- Capturing streamed video made of many small pieces.
- A separate downloads screen in the main app.
- Torrents.
- Scheduling downloads.

#### Things to check when implementing

- Large files, slow connections and sites that don't allow resuming.
- Downloads that start without a click (redirects, pop-ups). The ad blocker's pop-up protection must not be bypassed by them.
- The progress ring in the bar when the bar is moved to the bottom.
- The video button when several videos are on one page, and when the page changes without reloading.
- Whether the install permission and the VirusTotal and Hybrid Analysis lookups fit F-Droid's rules and the app's no-tracking claim in the README.
- Each service's terms for apps using its interface, and what happens when usage limits are hit.
- Very large files, for both fingerprinting and upload.
- That the hidden holding area really is invisible to other apps and gets cleaned up if the app is closed mid-check.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-25 | Three downloads at once, fingerprint first and upload only after asking, four-level verdict, any phone file can be checked, private downloads not kept, clearing the list keeps the files | BRW-02 |
| 2026-10-25 | Automatic checks of risky files are on by default (hold until checked stays off). Without a key, the app asks once to set one up and sends nothing until then | BRW-02 |

## Depends on / affects

- 21a built the downloader, the sheet and the entries; this batch adds to them.
- BRW-06 (batch 22) decides when the checks are highlighted, based on FMHY status.

## Where to start in the code

- Video detection JS: `app/src/main/java/io/github/omeg4dev/fmhy/browser/VideoJs.kt` `script(bridgeName)` (~L29); bridge `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserJsBridge.kt`; `app/src/main/java/io/github/omeg4dev/fmhy/browser/VideoPolicy.kt` has no detection
- Address bar indicators: `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserToolbar.kt` (~L111)
- The downloads sheet, entries and confirmation from batch 21a (find them with `rg -n 'Check link first|Finished' app/src/main`)
- Settings pages: the downloads settings from 21a; keys in `app/src/main/java/io/github/omeg4dev/fmhy/data/SettingsRepository.kt`
- Note: Check the services' terms and F-Droid rules (see "Things to check") before coding; report findings.

## Tests

New tests:
- `io.github.omeg4dev.fmhy.browser.download.ScanVerdictTest` (:app): four-level verdict from service numbers, unknown is not safe
- `io.github.omeg4dev.fmhy.browser.download.FileHashTest` (:app): fingerprint of large files in chunks

## Done when

- [ ] BRW-02: A small icon appears in the address bar, next to the safety and ad-block indicator, only while the current page has a downloadable video.
- [ ] BRW-02: Tapping it lists the videos found (with quality or size if known) and lets the user pick one.
- [ ] BRW-02: Only video files that can be saved as a single file are supported.
- [ ] BRW-02: The button can be turned off in the app's settings.
- [ ] BRW-02: _Services and keys:_ In settings the user can turn on VirusTotal, Hybrid Analysis or both.
- [ ] BRW-02: **Before the download starts (link check).** The "Check link first" button on the start confirmation checks the web address only, before any file is downloaded.
- [ ] BRW-02: **After downloading, before the file is released (optional "hold until checked" mode).** This is a setting, off by default.
- [ ] BRW-02: **Any time later.** Every finished entry has a "Check file" button.
- [ ] BRW-02: _How a file check works:_ The app calculates the file's fingerprint on the phone and sends only that, not the file.
- [ ] BRW-02: _Results:_ A plain-words verdict with four levels: no flags, suspicious, dangerous, unknown.
- [ ] BRW-02: _If a file is flagged as dangerous:_ The warning is shown in red and "Delete" is offered first.
- [ ] BRW-02: _Automatic checks:_ A setting, on by default, runs a check automatically on risky file types (APKs, executables, scripts) after download, once a key is set up.
- [ ] BRW-02: _Limits:_ Free keys have usage limits.

## Out of scope

- Streamed video made of many pieces.
- FMHY-based highlighting of checks (BRW-06, batch 22).

## Screenshots

None named in the plan for this batch.

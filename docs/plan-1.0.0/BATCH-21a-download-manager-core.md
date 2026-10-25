# Batch 21a — Download Manager (core) (BRW-02)

## Session prompt

```text
1.0.0 batch 21a (BRW-02): Download Manager (core).
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-21a-download-manager-core.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `opus`), then verify and finish yourself.
Plan first and wait for my approval before you change any code.
```

## Goal

Downloads are handled by the app: confirmation, a downloads sheet with Active/Finished, background downloads with a notification, resume, three at once, folder choice, risky-type and unsafe-source warnings, Install for APKs, and "Download only on Wi-Fi".

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

#### Opening the downloads

- The three-dot menu gets a "Downloads" entry. While something is downloading, a small progress ring appears in the address bar. Tapping it opens the same view. When nothing is active, the ring disappears.
- Downloads open in a bottom sheet on top of the page. There is no separate downloads screen in the main app.
- The sheet has two sections, "Active" and "Finished". At the top there is a field or button to paste a link, a "Check a file from the phone" option, and a way to clear the finished list.

#### What each entry shows and does

- Name, source site, size, progress bar, speed and remaining time while active, and a clear status (waiting, waiting for Wi-Fi, downloading, paused, failed, being checked, waiting for your decision, finished).
- Active: pause and resume, cancel.
- Failed: retry. If the site doesn't allow resuming, retry starts the file again, and the entry says so.
- Finished: open, share, delete, and "Check file" (the user picks the service). For APK files there is also "Install".
- After a check, a small label stays on the entry showing service, date and verdict. It can be re-checked at any time, because results get old.

#### Background behavior

- Downloads keep running when the user leaves the browser or the app, with a notification showing progress and offering pause and cancel.
- After a lost connection or a restart, downloads that can be resumed continue where they stopped.
- Several downloads can run at once. Extra ones wait in line.
- With "Download only on Wi-Fi" switched on (see BRW-03), downloads wait or pause while the phone is not on Wi-Fi and continue when it is.

#### Where files are saved

- By default in the phone's normal Downloads folder, so other apps and file managers see them.
- In settings the user can choose a different folder with the system folder picker.
- If the chosen folder is no longer available, the app falls back to the default and tells the user once.
- If a file with the same name exists, the new one gets a number added instead of overwriting.

#### Safety

- Risky file types (APKs, executables, scripts) need an extra confirmation before downloading.
- If the source site is on FMHY's unsafe-sites list, the user gets a warning before the download starts, in the same style as the existing warning before pages load. If the source is also blocked by a filter, only one combined warning is shown (see BUG-04).
- Finished APKs get an "Install" button that hands the file to the system installer. The first time, the app explains why the system asks for permission to install apps.

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

#### Carried over from BRW-03 (BRW-03 is owned by batch 18, which built the switch)

**Download only on Wi-Fi**

- With the switch on and no Wi-Fi, new downloads are accepted but wait, with the status "Waiting for Wi-Fi". They start by themselves when the phone connects to Wi-Fi.
- The download confirmation says so and offers "Download now using mobile data" for that one file.
- If the phone leaves Wi-Fi in the middle of a download, the download pauses ("Waiting for Wi-Fi") and continues when Wi-Fi returns. The notification shows the same status.
- A file the user allowed on mobile data keeps going even if the phone stays on mobile data.

**For all switches**

- Private tabs follow the browsing and download switches in the same way as normal tabs.


#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | Wi-Fi only has three switches: downloads, browsing, and app data and updates. The update check option moves out of APP-01 | BRW-03, APP-01 |
| 2026-10-25 | Three downloads at once, fingerprint first and upload only after asking, four-level verdict, any phone file can be checked, private downloads not kept, clearing the list keeps the files | BRW-02 |
| 2026-10-25 | Automatic checks of risky files are on by default (hold until checked stays off). Without a key, the app asks once to set one up and sends nothing until then | BRW-02 |
| 2026-10-25 | Metered Wi-Fi counts as mobile data, "this time" lasts for the current tab, manual actions ask once, the first wiki download asks | BRW-03 |

## Depends on / affects

- 21b adds the video button and the VirusTotal/Hybrid Analysis checks: leave out "Check link first", "Check file", check labels and "Check a file from the phone".
- BUG-04 (batch 05) built the single combined warning; reuse it for unsafe sources.
- APP-01 (batch 20) added the install permission, FileProvider and the install explanation; reuse them.
- BUG-07 (batch 08) progress rules; BRW-06 (batch 22) adds FMHY source status.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` — `onDownloadStart` (interface ~L130, impl ~L383-415, `pendingDownloadDialog` ~L166), `executeDownload` (~L433-460: system `DownloadManager`, Cookie and User-Agent, no Referer, `dm.enqueue` ~L457), `DownloadDialogState` (~L98)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserOverlays.kt` — `DownloadDialog` (~L169); shown from `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` (~L726-734)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/RiskyDownload.kt` — `extractExtension` (~L8), `isRiskyExtension` (~L18), `warningMessage` (~L23)
- Settings today: `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/BlockingPage.kt` "Ask before downloads" (~L193), "Warn about APK and EXE downloads" (~L201)
- Foreground service precedent: `app/src/main/java/io/github/omeg4dev/fmhy/browser/media/BackgroundMediaService.kt` (class ~L39, channel ~L230); manifest `<service>` entries
- Bar and menu: `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserToolbar.kt` `BrowserToolbar` (~L111), `app/src/main/java/io/github/omeg4dev/fmhy/ui/shell/BrowserMenuSheet.kt` (~L84)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.RiskyDownloadTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.download.DownloadRequestTest` (:app): headers, file name, numbered duplicates
- `io.github.omeg4dev.fmhy.browser.download.DownloadQueueTest` (:app): three at once, waiting, Wi-Fi pause/resume, retry without resume support

## Done when

- [ ] BRW-02: Any link the browser would normally offer as a file: APKs, zip files, PDFs, direct video files and so on.
- [ ] BRW-02: A "download this video" button for videos found on the page.
- [ ] BRW-02: Pasting a link directly into the download manager to download a file without opening the page first.
- [ ] BRW-02: Tapping a file link shows a short confirmation with file name, size (if known), source site and target folder.
- [ ] BRW-02: The confirmation also has a "Check link first" button.
- [ ] BRW-02: Downloads carry over the browser's session information for that site (login state and the page it came from).
- [ ] BRW-02: Private tabs: downloads started there are kept apart.
- [ ] BRW-02: The three-dot menu gets a "Downloads" entry.
- [ ] BRW-02: Downloads open in a bottom sheet on top of the page.
- [ ] BRW-02: The sheet has two sections, "Active" and "Finished".
- [ ] BRW-02: Name, source site, size, progress bar, speed and remaining time while active, and a clear status (waiting, waiting for Wi-Fi, downloading, paused, failed, being checked, waiting for your decision, finished).
- [ ] BRW-02: Active: pause and resume, cancel.
- [ ] BRW-02: Failed: retry.
- [ ] BRW-02: Finished: open, share, delete, and "Check file" (the user picks the service).
- [ ] BRW-02: After a check, a small label stays on the entry showing service, date and verdict.
- [ ] BRW-02: Downloads keep running when the user leaves the browser or the app, with a notification showing progress and offering pause and cancel.
- [ ] BRW-02: After a lost connection or a restart, downloads that can be resumed continue where they stopped.
- [ ] BRW-02: Several downloads can run at once.
- [ ] BRW-02: With "Download only on Wi-Fi" switched on (see BRW-03), downloads wait or pause while the phone is not on Wi-Fi and continue when it is.
- [ ] BRW-02: By default in the phone's normal Downloads folder, so other apps and file managers see them.
- [ ] BRW-02: In settings the user can choose a different folder with the system folder picker.
- [ ] BRW-02: If the chosen folder is no longer available, the app falls back to the default and tells the user once.
- [ ] BRW-02: If a file with the same name exists, the new one gets a number added instead of overwriting.
- [ ] BRW-02: Risky file types (APKs, executables, scripts) need an extra confirmation before downloading.
- [ ] BRW-02: If the source site is on FMHY's unsafe-sites list, the user gets a warning before the download starts, in the same style as the existing warning before pages load.
- [ ] BRW-02: Finished APKs get an "Install" button that hands the file to the system installer.
- [ ] BRW-03: With the switch on and no Wi-Fi, new downloads are accepted but wait, with the status "Waiting for Wi-Fi".
- [ ] BRW-03: The download confirmation says so and offers "Download now using mobile data" for that one file.
- [ ] BRW-03: If the phone leaves Wi-Fi in the middle of a download, the download pauses ("Waiting for Wi-Fi") and continues when Wi-Fi returns.
- [ ] BRW-03: A file the user allowed on mobile data keeps going even if the phone stays on mobile data.
- [ ] BRW-03: Private tabs follow the browsing and download switches in the same way as normal tabs.

## Out of scope

- Video button and security checks (21b).
- FMHY source status (BRW-06, batch 22).

## Screenshots

None named in the plan for this batch.

# Batch 20 — In-app updates (APP-01)

## Session prompt

```text
1.0.0 batch 20 (APP-01): In-app updates.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-20-in-app-updates.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

The release build checks GitHub releases once a day and on demand, shows a non-blocking prompt with plain release notes, downloads the APK with progress and hands it to the system installer. The F-Droid build has no updater.

## Entries

### APP-01: In-app updates

**Goal:** The app can update itself, so users don't have to hunt for the new file. The user stays in control: nothing is installed without a tap.

**Checking for updates**

- The app checks for a newer version when it starts (at most once a day) and whenever the user taps "Check for updates" in settings.
- Settings has a switch for automatic checking (on by default). Whether checks and update downloads may use mobile data is decided by the "App data and updates only on Wi-Fi" switch (see BRW-03).
- The check only asks the project's release page whether a newer version exists. It sends nothing about the user, and settings say so.

**The update prompt**

- A small prompt that does not block the app: "Version X is available" with the buttons "Install", "What's new in this version" and "Later". A "Skip this version" option is also available.
- "What's new in this version" shows the key changes in a few short, plain sentences, not the technical change list. The name is chosen so it is not confused with the wiki's own "What's new" page.
- "Later" hides the prompt for a few days. A small marker stays visible in settings until the user updates.

**Installing**

- "Install" downloads the new version with visible progress (it can reuse the Download Manager's downloader once BRW-02 exists) and then hands it to the system installer. The user confirms in the system dialog.
- If "App data and updates only on Wi-Fi" is on and the phone is on mobile data, the prompt says that the update will download on Wi-Fi and offers "Download now using mobile data".
- The first time, the app explains why the system asks for permission to install apps. Use the same explanation as for APK files in BRW-02.
- If the download fails or is interrupted, the user can retry. The installed version keeps working in the meantime.
- If the system refuses the update because the file does not match the installed app, the app says so in plain words and points to the release page. Builds older than 0.4.0 were signed with a different key and cannot be updated this way.

**Store builds:** The F-Droid build is made without the updater, because the store handles updates there. The build from the project's release page includes it.

**Things to check when implementing:**

- Slow connections, and the app being closed in the middle of the download.
- An update appearing while a normal download is running.
- Every release needs a short, plain-language list of key changes for the prompt (see "Release notes (draft)").
- Whether the install permission is allowed by the store rules.

#### Carried over from BRW-03 (BRW-03 is owned by batch 18, which built the switch and the sync/ad-block part)

**App data and updates only on Wi-Fi**

- Covers the app's own background data: the wiki sync, the ad-block list updates, the check for a new app version and the download of an update (see APP-01).
- With the switch on and the phone on mobile data, these wait quietly and run when Wi-Fi is available. The app keeps working with the data it already has.
- Wherever the user can see their status, they say "Waiting for Wi-Fi" (for example in the update prompt and in the sync and ad-block status in settings).
- A manual action always works. If the user taps "Check for updates" or a manual sync on mobile data, the app asks once whether to use mobile data this time.
- The very first wiki download of a build that has no wiki built in must not wait forever. The app explains that it needs the data to work and asks to allow mobile data once.
- This switch replaces the separate "check only on Wi-Fi" option that was planned in APP-01. It also replaces today's "Only on Wi-Fi" switch on the "What's new & notifications" page, so there is only one place for it (see SET-01).


#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | The updater is hidden in F-Droid builds                                         | APP-01         |
| 2026-10-23 | Wi-Fi only has three switches: downloads, browsing, and app data and updates. The update check option moves out of APP-01 | BRW-03, APP-01 |
| 2026-10-25 | Metered Wi-Fi counts as mobile data, "this time" lasts for the current tab, manual actions ask once, the first wiki download asks | BRW-03 |

## Depends on / affects

- BRW-03 (batch 18) built the "App data and updates only on Wi-Fi" switch; apply it to the update check and download here.
- BRW-02 (batch 21a) comes later: use a small downloader of its own now, and add the install permission, FileProvider and the install explanation so 21a can reuse them.
- BUG-07 (batch 08) progress display for the download.
- SET-01 (batch 23) places "App updates" under Updates.

## Where to start in the code

- `app/build.gradle.kts` — `versionName` (~L13-17), `buildTypes` (~L37), `buildConfig = true` (~L57); no `productFlavors` yet (needed for the F-Droid build without updater)
- `app/src/main/AndroidManifest.xml` — permissions (~L4-8): no `REQUEST_INSTALL_PACKAGES`, no `<provider>`
- `app/src/main/java/io/github/omeg4dev/fmhy/MainActivity.kt` — `onCreate` (~L29), `handleIntent` (~L80); `app/src/main/java/io/github/omeg4dev/fmhy/FmhyApp.kt` launch work (~L39-66)
- `app/src/main/java/io/github/omeg4dev/fmhy/work/UpdateWorker.kt` (`doWork` ~L27) is the wiki-change worker; use it as the pattern only
- `app/src/main/java/io/github/omeg4dev/fmhy/data/SettingsRepository.kt` keys (~L161-167); `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/AboutPage.kt` Version row (~L90)

## Tests

New tests:
- `io.github.omeg4dev.fmhy.update.ReleaseParserTest` (:app): release JSON, version compare, skipped version
- `io.github.omeg4dev.fmhy.update.UpdateCheckPolicyTest` (:app): once a day, Later, Wi-Fi rule

## Done when

- [ ] APP-01: The app checks for a newer version when it starts (at most once a day) and whenever the user taps "Check for updates" in settings.
- [ ] APP-01: Settings has a switch for automatic checking (on by default).
- [ ] APP-01: The check only asks the project's release page whether a newer version exists.
- [ ] APP-01: A small prompt that does not block the app: "Version X is available" with the buttons "Install", "What's new in this version" and "Later".
- [ ] APP-01: "What's new in this version" shows the key changes in a few short, plain sentences, not the technical change list.
- [ ] APP-01: "Later" hides the prompt for a few days.
- [ ] APP-01: "Install" downloads the new version with visible progress (it can reuse the Download Manager's downloader once BRW-02 exists) and then hands it to the system installer.
- [ ] APP-01: If "App data and updates only on Wi-Fi" is on and the phone is on mobile data, the prompt says that the update will download on Wi-Fi and offers "Download now using mobile data".
- [ ] APP-01: The first time, the app explains why the system asks for permission to install apps.
- [ ] APP-01: If the download fails or is interrupted, the user can retry.
- [ ] APP-01: If the system refuses the update because the file does not match the installed app, the app says so in plain words and points to the release page.
- [ ] BRW-03: Covers the app's own background data: the wiki sync, the ad-block list updates, the check for a new app version and the download of an update (see APP-01).
- [ ] BRW-03: With the switch on and the phone on mobile data, these wait quietly and run when Wi-Fi is available.
- [ ] BRW-03: Wherever the user can see their status, they say "Waiting for Wi-Fi" (for example in the update prompt and in the sync and ad-block status in settings).
- [ ] BRW-03: A manual action always works.
- [ ] BRW-03: The very first wiki download of a build that has no wiki built in must not wait forever.
- [ ] BRW-03: This switch replaces the separate "check only on Wi-Fi" option that was planned in APP-01.

## Out of scope

- The Download Manager (BRW-02, batch 21a).

## Screenshots

None named in the plan for this batch.

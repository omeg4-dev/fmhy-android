# Batch 19 — Problem and crash reports (APP-02, APP-03)

## Session prompt

```text
1.0.0 batch 19 (APP-02, APP-03): Problem and crash reports.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-19-reports.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
```

## Goal

Add the report form (settings, browser menu and every error screen) that hands a previewed report to a prefilled GitHub issue, Discord or the clipboard, and a next-start card that offers to report a crash. The app itself sends nothing.

## Entries

### APP-02: Report a problem from inside the app

**Goal:** Reporting a problem takes under a minute and needs no technical knowledge.

**Where to find it**

- Settings: "Report a problem".
- Three-dot menu in the browser: "Report a problem with this page".
- Error screens and "page did not load" screens: a "Report this" button (see BUG-01, BUG-02, BUG-04).

**The report form**

- A short text box, "What went wrong?", and a choice of area (page does not load, lists, search, downloads, update, other).
- A switch "Include technical details" with app version, Android version and device model.
- For problems with a page, a separate switch "Include the web address of this page". It is off by default and never filled in automatically in private tabs.
- A preview shows exactly what will be put into the report. Before GitHub opens, a notice says that reports on GitHub are public and should not contain private information.
- The app itself sends nothing. The user submits the report on GitHub or sends it on Discord.

**Where the report goes**

There are two ways to send a report, and the user picks one after seeing the preview:

- **GitHub:** opens a prefilled issue on the app's own GitHub project in the user's browser. The user reads the text on GitHub and submits it there, which needs a GitHub account. The issue uses fixed headings (what happened, what was expected, technical details) so all reports look the same.
- **Discord:** copies the report text and opens the developer's Discord profile (https://discord.com/users/626069774002159619), so the user can send it as a direct message without a GitHub account.

The app sends nothing itself in either case. A plain "Copy report" button is also available for passing the text on another way.

**Things to check when implementing:** no personal data in the technical details, very long text (prefilled links have a length limit, so long reports are shortened or copied instead), no internet connection, private tabs, and that the developer's Discord settings allow direct messages from people who are not friends yet (Discord may block them otherwise).

### APP-03: Offer to report crashes (GitHub or Discord)

**Goal:** Make it easy to report crashes that users would otherwise never report, without breaking the app's privacy promise. The app never sends anything by itself. Reports are handed over in the same ways as in APP-02: a prefilled GitHub issue or a message on Discord.

**Not anonymous:** GitHub shows the account name of whoever submits an issue, so reports made this way are not anonymous. Fully automatic, anonymous reports would need a destination other than GitHub and are not part of 1.0.0 (decided on 2026-10-23, see the parking lot).

**Desired behavior:**

- After a crash, the next start shows a small card: "The app closed unexpectedly. Report it?" with the buttons "Report on GitHub", "Message on Discord" and "Dismiss".
- Both report buttons use the report form from APP-02 with the crash details already filled in, including the preview. On GitHub the notice that reports are public applies. On Discord the text is copied first, then the developer's profile opens.
- A setting "Offer to report crashes" is on by default. When it is off, the app does not even keep the crash details.
- **Included in the report:** app version, Android version, device model, and the technical details of the crash (where in the app it happened and what kind of error it was).
- **Never included:** web addresses, search words, saved sites, folder or file names, downloaded files, or anything that identifies the user.
- Private tabs add nothing beyond the technical crash details.
- Crash details stay on the phone only until the user reports or dismisses them, then they are deleted.
- Errors that keep happening without a crash (for example a download that keeps failing) are not covered here. The "Report this" buttons on error screens (APP-02) handle them.

**Things to check when implementing:**

- The README and the settings text must stay true ("no tracking", "no server of ours"). The app sends nothing by itself.
- Prefilled links have a length limit, so long technical details are shortened or copied instead.
- Users without a GitHub account can use the Discord option or copy the report.
- Several crashes in a row: only offer the latest one.
- A crash while the app is in the background, or a restart of the phone.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | Problem reports go through a prefilled GitHub issue                             | APP-02, APP-03 |
| 2026-10-23 | Automatic anonymous crash reports are not part of 1.0.0 | APP-03 |
| 2026-10-23 | Reports can also be sent as a Discord message to the developer, next to the GitHub issue | APP-02, APP-03 |
| 2026-10-25 | Reports get a "Copy report" button, "Offer to report crashes" is on by default | APP-02, APP-03 |

## Depends on / affects

- Wire "Report this" into the screens built earlier: the failed-load message (BUG-01, batch 04), the rescue screen (BUG-02, batch 01), the warning screen (BUG-04, batch 05) and failed ad-block/wiki updates (BUG-07, batch 08).
- SET-01 (batch 23) places "Report a problem" under "Help & about".

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/shell/BrowserMenuSheet.kt` — `BrowserMenuSheet` (~L84), `ActionRow` (~L252)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserOverlays.kt` — `RescueOverlay` (~L38), `InterstitialOverlay` (~L117); called from `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` (~L663, ~L680); state in `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabModels.kt` (~L9, ~L17)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/LinkOpener.kt` — `LinkOpener` (~L53), `openUrl` (~L87) to open the issue URL
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/AboutPage.kt` — Version (~L90, `versionName` ~L42-51), "Run setup again" (~L95); `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/SettingsHub.kt` About section (~L236)
- Crashes: `app/src/main/java/io/github/omeg4dev/fmhy/FmhyApp.kt` `onCreate` (~L22, no uncaught-exception handler yet); next-start card in `app/src/main/java/io/github/omeg4dev/fmhy/MainActivity.kt` `onCreate` (~L29) / Home

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.shell.BrowserMenuSheetTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.report.IssueUrlBuilderTest` (:app): headings, length limit, no URL in private tabs
- `io.github.omeg4dev.fmhy.report.CrashReportStoreTest` (:app): keep latest only, delete after report/dismiss, nothing kept when the setting is off

## Done when

- [ ] APP-02: Settings: "Report a problem".
- [ ] APP-02: Three-dot menu in the browser: "Report a problem with this page".
- [ ] APP-02: Error screens and "page did not load" screens: a "Report this" button (see BUG-01, BUG-02, BUG-04).
- [ ] APP-02: A short text box, "What went wrong?", and a choice of area (page does not load, lists, search, downloads, update, other).
- [ ] APP-02: A switch "Include technical details" with app version, Android version and device model.
- [ ] APP-02: For problems with a page, a separate switch "Include the web address of this page".
- [ ] APP-02: A preview shows exactly what will be put into the report.
- [ ] APP-02: The app itself sends nothing.
- [ ] APP-02: There are two ways to send a report, and the user picks one after seeing the preview:
- [ ] APP-02: **GitHub:** opens a prefilled issue on the app's own GitHub project in the user's browser.
- [ ] APP-02: **Discord:** copies the report text and opens the developer's Discord profile (https://discord.com/users/626069774002159619), so the user can send it as a direct message without a GitHub account.
- [ ] APP-02: The app sends nothing itself in either case.
- [ ] APP-03: After a crash, the next start shows a small card: "The app closed unexpectedly.
- [ ] APP-03: Both report buttons use the report form from APP-02 with the crash details already filled in, including the preview.
- [ ] APP-03: A setting "Offer to report crashes" is on by default.
- [ ] APP-03: **Included in the report:** app version, Android version, device model, and the technical details of the crash (where in the app it happened and what kind of error it was).
- [ ] APP-03: **Never included:** web addresses, search words, saved sites, folder or file names, downloaded files, or anything that identifies the user.
- [ ] APP-03: Private tabs add nothing beyond the technical crash details.
- [ ] APP-03: Crash details stay on the phone only until the user reports or dismisses them, then they are deleted.
- [ ] APP-03: Errors that keep happening without a crash (for example a download that keeps failing) are not covered here.

## Out of scope

- Automatic anonymous crash reports (parking lot).

## Screenshots

None named in the plan for this batch.

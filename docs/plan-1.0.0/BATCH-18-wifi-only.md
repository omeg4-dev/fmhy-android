# Batch 18 — Wi-Fi only options (BRW-03)

## Session prompt

```text
1.0.0 batch 18 (BRW-03): Wi-Fi only options.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-18-wifi-only.md, nothing else from docs/plan-1.0.0/.
Delegate the coding to agy agents as COMMON.md says (agy model: `sonnet`), then verify and finish yourself.
Plan first and wait for my approval before you change any code.
```

## Goal

Add the "Data usage" page with the three Wi-Fi only switches. This batch builds the browsing switch and the wiki-sync and ad-block part of "App data and updates only on Wi-Fi"; downloads and app updates use the switches in their own batches.

## Entries

### BRW-03: Wi-Fi only options (downloads, browsing, app data and updates)

**Goal:** People with limited mobile data can stop the app from using it by accident. There are three separate options, so each kind of data use can be controlled on its own.

**Settings:** A new page "Data usage" in settings (under "Your data", see SET-01) with three switches, all off by default:

- "Download only on Wi-Fi"
- "Browse only on Wi-Fi"
- "App data and updates only on Wi-Fi"

**Download only on Wi-Fi**

- With the switch on and no Wi-Fi, new downloads are accepted but wait, with the status "Waiting for Wi-Fi". They start by themselves when the phone connects to Wi-Fi.
- The download confirmation says so and offers "Download now using mobile data" for that one file.
- If the phone leaves Wi-Fi in the middle of a download, the download pauses ("Waiting for Wi-Fi") and continues when Wi-Fi returns. The notification shows the same status.
- A file the user allowed on mobile data keeps going even if the phone stays on mobile data.

**Browse only on Wi-Fi**

- With the switch on and the phone on mobile data, opening or reloading a page shows a message page from the app instead of a blank page: "Browsing is set to Wi-Fi only." with the buttons "Use mobile data this time" and "Open settings".
- "Use mobile data this time" applies to the current tab only, until it is closed.
- Pages that are already on the screen stay visible. Anything that needs new data from the web waits and shows the same message.
- Searching from the address bar needs the web too, so it shows the same message.
- The browser start screen and saved sites need no network, so they keep working.

**App data and updates only on Wi-Fi**

- Covers the app's own background data: the wiki sync, the ad-block list updates, the check for a new app version and the download of an update (see APP-01).
- With the switch on and the phone on mobile data, these wait quietly and run when Wi-Fi is available. The app keeps working with the data it already has.
- Wherever the user can see their status, they say "Waiting for Wi-Fi" (for example in the update prompt and in the sync and ad-block status in settings).
- A manual action always works. If the user taps "Check for updates" or a manual sync on mobile data, the app asks once whether to use mobile data this time.
- The very first wiki download of a build that has no wiki built in must not wait forever. The app explains that it needs the data to work and asks to allow mobile data once.
- This switch replaces the separate "check only on Wi-Fi" option that was planned in APP-01. It also replaces today's "Only on Wi-Fi" switch on the "What's new & notifications" page, so there is only one place for it (see SET-01).

**For all switches**

- Private tabs follow the browsing and download switches in the same way as normal tabs.

**Decided details**

- A Wi-Fi network that Android marks as metered (for example a phone hotspot) counts as mobile data.
- "Use mobile data this time" in the browser lasts only for the current tab.
- A manual update check or sync on mobile data asks once, then runs, instead of being blocked.
- The first wiki download of a build without built-in data explains why it is needed and asks to use mobile data once, instead of waiting.

**Out of scope for now**

- Limits by amount of data (for example a monthly cap).

**Things to check when implementing:**

- Switching between Wi-Fi and mobile data in the middle of a page load or a download.
- Wi-Fi without internet, VPN, USB tethering, ethernet and Android's own Data Saver mode.
- Video that is already playing when the phone leaves Wi-Fi.
- The message page in light, dark and pure black, and that it never appears on the start screen.
- The wording "Waiting for Wi-Fi" is the same everywhere.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | Wi-Fi only has three switches: downloads, browsing, and app data and updates. The update check option moves out of APP-01 | BRW-03, APP-01 |
| 2026-10-25 | Metered Wi-Fi counts as mobile data, "this time" lasts for the current tab, manual actions ask once, the first wiki download asks | BRW-03 |

## Depends on / affects

- The download part ("Download only on Wi-Fi") is built with BRW-02 in batch 21a.
- The update part (check and download of app updates) is built with APP-01 in batch 20.
- BUG-07 (batch 08) provides the progress display that shows "Waiting for Wi-Fi".
- BRW-04 (batch 06): the start screen never shows the Wi-Fi message.
- SET-01 (batch 23) sorts the page under "Your data".

## Where to start in the code

- Existing switch to replace: `app/src/main/java/io/github/omeg4dev/fmhy/data/SettingsRepository.kt` `KEY_NOTIFICATIONS_WIFI_ONLY` (~L167, read ~L620, written ~L631); `app/src/main/java/io/github/omeg4dev/fmhy/data/Prefs.kt` `NotificationPrefs.wifiOnly` (~L156); `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/NotificationsPage.kt` "Only on Wi-Fi" (~L159-162)
- `app/src/main/java/io/github/omeg4dev/fmhy/work/UpdateScheduler.kt` — `apply` (~L15-45, `NetworkType.UNMETERED` ~L22-26): only the notifications worker uses it today
- Wiki sync: `app/src/main/java/io/github/omeg4dev/fmhy/FmhyApp.kt` launch sync (~L39-45, no network check, not scheduled); ad-block: `app/src/main/java/io/github/omeg4dev/fmhy/browser/adblock/AdblockService.kt` `updateNow` (~L103), `updateInternal` (~L107)
- New page: `app/src/main/java/io/github/omeg4dev/fmhy/NavigationKeys.kt` `SettingsPage` (~L27-38), `app/src/main/java/io/github/omeg4dev/fmhy/Navigation.kt` routing (~L498-551), `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/SettingsHub.kt` ("Your data" ~L217)
- Browse-only message page: no connectivity code exists (no `ConnectivityManager`); hook into the load path in `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` and the overlays in `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` (~L660-734)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.settings.SettingsViewModelTest' --tests 'io.github.omeg4dev.fmhy.data.SettingsRepositoryTest' --tests 'io.github.omeg4dev.fmhy.work.UpdateWorkerLogicTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.adblock.FilterListUpdaterTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.work.NetworkPolicyTest` (:app): metered Wi-Fi counts as mobile, "this time" per tab, manual actions ask once, first wiki download asks

## Done when

- [ ] BRW-03: A new page "Data usage" in settings (under "Your data", see SET-01) with three switches, all off by default: "Download only on Wi-Fi", "Browse only on Wi-Fi", "App data and updates only on Wi-Fi"
- [ ] BRW-03: With the switch on and the phone on mobile data, opening or reloading a page shows a message page from the app instead of a blank page: "Browsing is set to Wi-Fi only." with the buttons "Use mobile data this time" and "Open settings".
- [ ] BRW-03: "Use mobile data this time" applies to the current tab only, until it is closed.
- [ ] BRW-03: Pages that are already on the screen stay visible.
- [ ] BRW-03: Searching from the address bar needs the web too, so it shows the same message.
- [ ] BRW-03: The browser start screen and saved sites need no network, so they keep working.
- [ ] BRW-03: Covers the app's own background data: the wiki sync, the ad-block list updates, the check for a new app version and the download of an update (see APP-01).
- [ ] BRW-03: With the switch on and the phone on mobile data, these wait quietly and run when Wi-Fi is available.
- [ ] BRW-03: Wherever the user can see their status, they say "Waiting for Wi-Fi" (for example in the update prompt and in the sync and ad-block status in settings).
- [ ] BRW-03: A manual action always works.
- [ ] BRW-03: The very first wiki download of a build that has no wiki built in must not wait forever.
- [ ] BRW-03: This switch replaces the separate "check only on Wi-Fi" option that was planned in APP-01.
- [ ] BRW-03: Private tabs follow the browsing and download switches in the same way as normal tabs.

## Out of scope

- "Download only on Wi-Fi" (batch 21a).
- Update check/download on Wi-Fi (batch 20).
- Limits by amount of data.

## Screenshots

None named in the plan for this batch.

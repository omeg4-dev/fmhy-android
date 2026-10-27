# QA-01 findings (batch 24)

Device: `fmhy36` (API 36, emulator-5554), app 0.5.4 `github` debug, fresh install (`pm clear`), 2026-10-26.
Walkthrough finished on 2026-10-27; rows still marked Partial are listed under "Needs the phone / later".

## Summary

- Findings: 1 Major (SEC-2, fixed), 1 Minor (APP-01 "up to date" card stayed on every screen until OK, fixed), 0 Blocker, 1 Cosmetic (browser history keeps the Cloudflare title "Just a moment..." for w3.org, later). No `FATAL EXCEPTION` / `ANR` in the log.
- Covered: First run, Home, Wiki lists (hold/tap, section menu, website style), Gestures (swipe to bookmark), Jumps, Search, What's new, Settings, Themes, bottom bar style (Rounded top corners, Floating bubble), Tabs (grouping, empty tab reuse), Downloads (happy path, video button), Browser panels, App (update check, Report a problem), SEC-1 / SEC-2.
- Needs the phone / later: pinch zoom and scroll feel, open/leave animation, reopened tabs, false 503 and blocked-page icon, progress bars, VirusTotal / Hybrid Analysis checks (need free API keys), crash-offer prompt (wording "The app closed unexpectedly. Report it?" read in `HomeScreen.kt`, not triggered: the emulator now has the signed 1.0.0 release, not the debug build), pastebin decode (`PasteDecoderTest` 10/10, not driven in the UI), bottom bar Classic.

## Checklist

| Area | Status | Notes |
|---|---|---|
| First run | Pass | FMHY logo on setup steps 1-3; Back and Next work; "Saving the ad-block rules" finished in under ~20 s; no "NEW" labels in lists after setup. Last page uses a check mark, not the logo (by design?). |
| Home | Pass | Slim Random pick with heading, vibrant search bar; Pinned chip appears with Personal Home on. Bottom bar style setting not checked yet. |
| Wiki lists | Partial | Follow on subsection present; "Pin to Home" in the section menu. Website style and bubble row not checked. |
| Gestures | Partial | Default: tap opens site, hold opens menu (hold needs a long press, about 2 s in `adb input swipe`). Swap switch and swipe-to-bookmark not checked. |
| Jumps | Pass | Search → AdGuard Home → "Show in section" lands on the entry with a visible outline; the "Free DNS Resolvers" section link lands on its heading, outlined. |
| Search | Pass | Results stay while typing more characters. Pastebin decode not checked. |
| What's new | Pass | "Since last visit" chip (labelled "New") shows the empty state with "Show this week" on a fresh install. Reset after viewing not checked. |
| Themes | Pass | Light + Red, Dark + Violet switch live; Red is vibrant and readable; bottom bar "Floating bubble" applies on Home. |
| Settings | Pass | Re-sorted hub, search finds Wi-Fi settings, GitHub repo row opens the in-app browser, three Wi-Fi-only switches present (Data usage: Off). |
| Tabs | Pass | Same-site tabs grouped ("github.com · 7 tabs"); "New tab" from an empty start-page tab reuses it (count stays 1); `EmptyTabPolicyTest` 23/23. |
| Downloads | Partial | LICENSE.txt from raw.githubusercontent.com: dialog with size/host/FMHY status, saved to Downloads, manager shows Finished + "Not listed". w3.org dummy.pdf failed with HTTP 403 (Cloudflare bot check, the WebView passed it, OkHttp did not): observation, not fixed. Video button appears in the address bar for a direct MP4 and lists "176p · MP4". VirusTotal / Hybrid Analysis settings exist ("Needs a free key", not set), live checks not run. |
| Browser basics | Partial | w3.org Cloudflare check passed in the WebView and handed off to the download dialog; en.wikipedia.org loads. False 503, blocked-page icon, empty start page not checked. |
| Browser bars and zoom | Partial | Bar hides on scroll and returns on scroll up; gfxinfo over 3 fast scrolls: 143 frames, 1.40% janky (emulator). Zoom control present in the menu (100 %); pinch zoom needs the phone. |
| Browser navigation | Partial | Menu has a separate "Back to FMHY"; link hold menu shows Open in new tab / background tab / private tab / Copy link / Share link. Open/leave animation and reopened tabs not checked. |
| Browser panels | Pass | FMHY status popup ("Starred on FMHY", Show in section, Entry details); compact "2 blocked on this page" sheet and clean list (host, ×2, Allow). Progress bars not checked. |
| Wiki lists / Gestures (website style, swipe) | Pass | Website style on opens a section without a crash; swipe to bookmark saves an entry ("Saved"). Swap tap/hold switch present. |
| App | Partial | Check for updates: "You have the latest version"; Report a problem form with preview, technical details, GitHub and Discord buttons; crash-offer switch present. Crash-offer prompt wording not triggered. |

## Findings

### SEC-2 (Major): https download could be redirected to http — fixed

- Steps: code review of the hop loop in `DownloadController` (redirects are followed by hand, `RedirectPath.nextUrl` accepted any http/https target).
- Expected: an https to http redirect fails the download with "The site tried to switch this download to an insecure connection"; http to https stays allowed.
- Actual: the downgrade was followed.
- Decision: fixed (`RedirectPath.isInsecureDowngrade`, check in the hop loop, 2 unit tests, `RedirectPathTest` 10/10). Not run end to end on the emulator (needs a server that redirects).

### Observation (later): Referer is sent on every redirect hop

`DownloadRequest.headers` sends the original Referer on all hops, also cross-site. Minor, not changed.

## Carry-over

- SEC-1: already fixed in the code. The hop loop reads `CookieManager.getCookie(currentUrlString)` for every hop and builds the headers per hop, so a cookie from host A never reaches host B. The batch text describes the older code. Private-tab cookie handling (`isPrivate`) is unchanged: later decision.
- SEC-2: fixed, see above. The plan's `.followSslRedirects(true)` is `false` in the code; redirects are manual.
- MockWebServer test for SEC-1 not written: the cookie read goes through the Android `CookieManager`, which unit tests cannot call.

### APP-01 (Minor): "You have the latest version" card stayed on every screen — fixed

- Steps: Settings → App updates → Check for updates; leave the page.
- Expected: the message goes away by itself.
- Actual: the card stayed on every screen (seen for 3+ minutes) until OK was tapped.
- Decision: fixed (`UpdatePrompt` dismisses a manual up-to-date result after 4 s); verified on the emulator: card at 3 s, gone at 8 s.

### Observation (later): history title "Just a moment..."

- Recent on the browser start screen lists w3.org as "Just a moment..." (the Cloudflare interstitial title).

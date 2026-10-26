# QA-01 findings (batch 24)

Device: `fmhy36` (API 36, emulator-5554), app 0.5.4 `github` debug, fresh install (`pm clear`), 2026-10-26.
Walkthrough in progress; this file is updated as areas are completed.

## Summary

- Findings so far: 1 Major (SEC-2, fixed), 0 Blocker, 0 Minor, 0 Cosmetic. No `FATAL EXCEPTION` / `ANR` in the log so far.
- Covered: First run, Home, Wiki lists (hold/tap, section menu), Search, What's new, Settings, Themes, bottom bar style, Tabs (grouping), Downloads (happy path), SEC-1 / SEC-2.
- Not covered: pastebin decode, empty-tab auto close, reopened tabs, false 503 / blocked-page icon, progress bars, video button and VirusTotal checks, crash-offer prompt, bottom bar Classic/Rounded, What's new reset. Batch 24 stays open until these are done or moved to the phone list.
- Needs the phone: scroll/zoom feel, animations, notifications, video.

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
| Tabs | Partial | Same-site tabs grouped ("github.com · 7 tabs"). Empty start-page tabs not checked. |
| Downloads | Partial | LICENSE.txt from raw.githubusercontent.com: dialog with size/host/FMHY status, saved to Downloads, manager shows Finished + "Not listed". w3.org dummy.pdf failed with HTTP 403 (Cloudflare bot check, the WebView passed it, OkHttp did not): observation, not fixed. VirusTotal / Hybrid Analysis and video button not checked. |
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

# QA-01 findings (batch 24)

Device: `fmhy36` (API 36, emulator-5554), app 0.5.4 `github` debug, fresh install (`pm clear`), 2026-10-26.
Walkthrough in progress; this file is updated as areas are completed.

## Summary

- Findings so far: 1 Major (SEC-2, fixed), 0 Blocker, 0 Minor, 0 Cosmetic. No `FATAL EXCEPTION` / `ANR` in the log so far.
- Covered: First run, Home, Wiki lists (hold/tap, section menu), Search, What's new, Settings, Themes, bottom bar style, Tabs (grouping), Downloads (happy path), SEC-1 / SEC-2.
- Not covered: Jumps, Browser bars/zoom/navigation/panels, difficult sites, App (update check, Report a problem), website style, swipe gestures, pastebin decode, empty-tab auto close, video button and VirusTotal checks. Batch 24 stays open until these are done.
- Needs the phone: scroll/zoom feel, animations, notifications, video.

## Checklist

| Area | Status | Notes |
|---|---|---|
| First run | Pass | FMHY logo on setup steps 1-3; Back and Next work; "Saving the ad-block rules" finished in under ~20 s; no "NEW" labels in lists after setup. Last page uses a check mark, not the logo (by design?). |
| Home | Pass | Slim Random pick with heading, vibrant search bar; Pinned chip appears with Personal Home on. Bottom bar style setting not checked yet. |
| Wiki lists | Partial | Follow on subsection present; "Pin to Home" in the section menu. Website style and bubble row not checked. |
| Gestures | Partial | Default: tap opens site, hold opens menu (hold needs a long press, about 2 s in `adb input swipe`). Swap switch and swipe-to-bookmark not checked. |
| Jumps | Not covered | |
| Search | Pass | Results stay while typing more characters. Pastebin decode not checked. |
| What's new | Pass | "Since last visit" chip (labelled "New") shows the empty state with "Show this week" on a fresh install. Reset after viewing not checked. |
| Themes | Pass | Light + Red, Dark + Violet switch live; Red is vibrant and readable; bottom bar "Floating bubble" applies on Home. |
| Settings | Pass | Re-sorted hub, search finds Wi-Fi settings, GitHub repo row opens the in-app browser, three Wi-Fi-only switches present (Data usage: Off). |
| Tabs | Partial | Same-site tabs grouped ("github.com · 7 tabs"). Empty start-page tabs not checked. |
| Downloads | Partial | LICENSE.txt from raw.githubusercontent.com: dialog with size/host/FMHY status, saved to Downloads, manager shows Finished + "Not listed". w3.org dummy.pdf failed with HTTP 403 (Cloudflare bot check, the WebView passed it, OkHttp did not): observation, not fixed. VirusTotal / Hybrid Analysis and video button not checked. |
| Browser basics, bars and zoom, navigation, panels, App, Jumps | Not covered | |

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

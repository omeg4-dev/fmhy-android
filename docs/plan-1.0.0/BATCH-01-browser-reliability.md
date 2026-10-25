# Batch 01 — Browser reliability (BUG-02, BUG-09, BUG-03)

## Session prompt

```text
1.0.0 batch 01 (BUG-02, BUG-09, BUG-03): Browser reliability.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-01-browser-reliability.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Stop the dead-site rescue screen from appearing for side requests or pages that already show content, and make it block touches without covering the bar. Make the address bar hide and show during the scroll without flicking, and show a favicon only for the page that is displayed.

## Entries

### BUG-02: False 503 errors

**Problem:** Users see "503" (service unavailable) errors even though the content loads or the service is fine. The error appears in the browser, as an overlay page that the app shows on top of the web page. It is not a page from the website itself. One tester reported that after pressing retry, the app seemed to tap an entry on its own. The video did load, but errors kept appearing while scrolling.

**Seen in the recordings (2026-10-24/25):**

- On streamed.pk, full-screen 503 overlays appear at random while scrolling or using the page, although the site works.
- On virgil.samidy.com (recording screen-20261025-020549.mp4), the page is loaded and works. Tapping the page's own "Games", "Software" and "Android" buttons makes the full-screen "This site isn't loading · Server error 503" screen appear and disappear several times within a few seconds. The page underneath keeps working and changes category, so the content was never gone.
- The overlay covers the whole screen including the address bar, so while it is shown the user cannot even see or use the bar.
- After a tap on "Retry" the page underneath reacted as well (the search field switched to "Search for an Android app"). This matches the tester's report: the touch goes through the overlay to the page.

**Likely cause to check:** The overlay is the "Dead-site rescue" screen. It reacts to every server error (500 to 599) that is reported for the main page, even when the page on screen has already loaded and is working. Sites like these send such errors for background loads of the same page (in-page navigation, a page reloading part of itself, a request that the site retries by itself), and each one opens the full rescue screen. Side requests (single images, scripts, trackers, video pieces) should also be ruled out as a trigger.

**Desired behavior:**

- The error overlay is only for the main page failing to load. A failed side request never produces a full-page error. Requests that the ad blocker blocks on purpose never count as errors.
- Show an error only when the content really failed to load. If the content arrives anyway, the overlay disappears by itself.
- Retry quietly in the background a couple of times, with a short wait in between, before showing the overlay.
- The overlay never appears over a page that has already loaded, for example while the user scrolls.
- Pressing "Retry" does only that. The overlay takes the touch, so nothing underneath is tapped or opened, and it is fully gone before touches reach the page again.
- When an error is real, say what failed (for example "the site did not answer") instead of only showing a number, and offer "Retry" and "Report this" (see APP-02).
- The rescue screen only appears for the address the user actually opened, and only while nothing of that page is on the screen yet. Once a page has shown its content, later errors never replace it with the rescue screen. If something on a loaded page really fails, a small, dismissable notice under the address bar is enough.
- The rescue screen never covers the address bar or the tab button. The user can always type a new address, switch tabs or go back to FMHY while it is shown.
- It never flashes on and off. Once shown, it stays until the user acts or the page really loads, and then it fades out once.

**Things to check when implementing:**

- Test sites: streamed.pk (scrolling a live-events page) and virgil.samidy.com (tapping its "Games", "Software" and "Android" buttons several times). Both must work without a single rescue screen. Add both to the list of difficult sites (BUG-01, QA-01).
- Reproduce by scrolling pages and video pages quickly, and on slow or unstable connections.
- Log which request triggered the overlay each time it appears, to confirm whether it is the main page or a side request.
- Sites that really send a 503 for a short time (busy servers) and recover a moment later.
- If many requests at once cause the errors on the server side, spread the requests out.

### BUG-09: Browser bar flicks up and down after letting go

**Problem:** When the user scrolls a web page and lifts the finger, the address bar jumps up and down once or several times. The recording screen-20261024-143047.mp4 (streamed.pk) shows it: the bar hides, comes back and hides again during a single scroll, it sometimes stops half hidden and cut off, and the page content jumps along with it. Likely cause to check: while the finger is down, the app only notes whether the bar should hide or show and moves it after the finger is lifted; the page keeps gliding after the finger is lifted and the moving bar shifts the page, which is counted as scrolling again and flips the bar back.

**Desired behavior:**

- Scrolling down hides the bar, scrolling up shows it. This applies to the bar at the top and the bar at the bottom.
- The bar reacts during the scroll, not after it. Lifting the finger never moves the bar by itself.
- While the page keeps gliding after the finger is lifted, the bar only moves if the direction is clearly the same as before. It never flips back and forth.
- The bar is always either fully shown or fully hidden when the page comes to rest. It never stays half hidden or cut off.
- The page content does not jump when the bar hides or shows.
- At the very top of a page, the bar is always shown.
- Small wiggles of the finger do nothing.

**Things to check when implementing:**

- Slow drags, fast flicks, a flick followed by a tap to stop it, and scrolling up and down quickly in a row.
- Pages that scroll inside a box instead of as a whole page, pages with endless scrolling, and pages that change their own height while scrolling (ads, images loading).
- Bar at the top and at the bottom, with the keyboard open, in fullscreen video and in find-in-page mode.
- Pull to refresh at the top of the page still works.

### BUG-03: Wrong icon on blocked pages

**Problem:** While a blocked page is shown, the address bar still shows the icon of the previously visited site. This was seen with the anime site's icon on a blocked page of a different site. It also happens without blocking: in Screenshot_20261024-143242 the address bar shows steamunlocked.org with the DuckDuckGo icon, left over from the search page before it.

**Desired behavior:**

- A blocked page shows a neutral warning icon (or none).
- An icon is only ever shown for the page that is actually displayed.
- When a new page is opened, the old icon disappears right away. The new icon appears once the new page has loaded.

**Things to check when implementing:** going back and forward, redirects, switching between tabs, private tabs.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | The 503 errors appear in the browser, as an overlay page shown by the app       | BUG-02         |
| 2026-10-24 | The 503 errors also appear on streamed.pk and virgil.samidy.com and are handled in BUG-02 | BUG-02 |
| 2026-10-24 | The browser bar hides on scroll down, shows on scroll up, and does not move when the finger is lifted (top and bottom bar) | BUG-09 |

## Depends on / affects

- BUG-01 (batch 04) adds the "page did not load" message on top of the rescue screen fixed here.
- BUG-04 (batch 05) reworks `InterstitialOverlay`, the blocked-page screen whose icon BUG-03 fixes.
- "Report this" (BUG-02) comes with APP-02 in batch 19; leave it out.
- BUG-01/QA-01 difficult-sites list: create `docs/difficult-sites.md` if missing and add streamed.pk and virgil.samidy.com with what to test on each.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` — `onMainFrameError` (~L475), `onMainFrameHttpError` (~L484), `buildRescueState` (~L493): add the "nothing of the page shown yet" and "same address the user opened" guards, background retries, no flashing
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserWebViewFactory.kt` — `onReceivedError` (~L183), `onReceivedHttpError` (~L190), `onPageStarted` (~L137), `onReceivedIcon` (~L217): the `isForMainFrame` checks and the icon feed
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/RescueReason.kt` — `RescueReason.shouldTrigger` (~L20), `shouldTriggerHttp` (~L39), `fromHttpStatus` (~L57): pure error-to-reason mapping; plain-words reasons
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserOverlays.kt` — `RescueOverlay` (~L38): touch consumption, fade out once
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` — overlay host (~L660-672, `!isBlankTab`), `BarScrollTracker(...)` (~L168), scroll listener and touch handling (~L401-495, `ACTION_UP` ~L460)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BarScrollTracker.kt` — `BarScrollTracker.onScroll` (~L15), `reset` (~L26), `BarVisibilityState` (~L30)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabManager.kt` — `updateTabUrlAndTitle` (~L802, clears `rescueState`), `setRescueState` (~L820), `updateTabFavicon` (~L1014); `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabModels.kt` `RescueState` (~L9), `favicon` (~L27)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserToolbar.kt` — favicon drawing (~L407-410)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.RescueReasonTest' --tests 'io.github.omeg4dev.fmhy.browser.BarScrollTrackerTest' --tests 'io.github.omeg4dev.fmhy.browser.TabManagerTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.RescueDecisionTest` (:app) for a pure "show rescue?" decision (main frame, same URL, content shown, retry count)

## Done when

- [ ] BUG-02: The error overlay is only for the main page failing to load.
- [ ] BUG-02: Show an error only when the content really failed to load.
- [ ] BUG-02: Retry quietly in the background a couple of times, with a short wait in between, before showing the overlay.
- [ ] BUG-02: The overlay never appears over a page that has already loaded, for example while the user scrolls.
- [ ] BUG-02: Pressing "Retry" does only that.
- [ ] BUG-02: When an error is real, say what failed (for example "the site did not answer") instead of only showing a number, and offer "Retry" and "Report this" (see APP-02). _("Report this" itself: batch 19)_
- [ ] BUG-02: The rescue screen only appears for the address the user actually opened, and only while nothing of that page is on the screen yet.
- [ ] BUG-02: The rescue screen never covers the address bar or the tab button.
- [ ] BUG-02: It never flashes on and off.
- [ ] BUG-09: Scrolling down hides the bar, scrolling up shows it.
- [ ] BUG-09: The bar reacts during the scroll, not after it.
- [ ] BUG-09: While the page keeps gliding after the finger is lifted, the bar only moves if the direction is clearly the same as before.
- [ ] BUG-09: The bar is always either fully shown or fully hidden when the page comes to rest.
- [ ] BUG-09: The page content does not jump when the bar hides or shows.
- [ ] BUG-09: At the very top of a page, the bar is always shown.
- [ ] BUG-09: Small wiggles of the finger do nothing.
- [ ] BUG-03: A blocked page shows a neutral warning icon (or none).
- [ ] BUG-03: An icon is only ever shown for the page that is actually displayed.
- [ ] BUG-03: When a new page is opened, the old icon disappears right away.

## Out of scope

- The failed-load message with reasons and "Reload without blocking" (BUG-01, batch 04).
- "Report this" buttons (APP-02, batch 19).
- The combined blocked-page warning (BUG-04, batch 05) beyond its icon.

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143242.png`
- `screen-20261024-143047.mp4`
- `screen-20261025-020549.mp4`

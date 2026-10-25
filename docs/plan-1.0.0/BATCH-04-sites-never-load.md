# Batch 04 — Sites that never load (BUG-01)

## Session prompt

```text
1.0.0 batch 04 (BUG-01): Sites that never load.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-04-sites-never-load.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Find out why chat.z.ai and chat.qwen.ai never load and fix the causes, so blocking never breaks a site's own essential content and verification checks can finish. Add a clear failed-load message, a "blocked on this page" list with a one-tap allow per site in the shield panel, and the difficult-sites list.

## Entries

### BUG-01: Some sites never load (chat.z.ai, chat.qwen.ai)

**Problem:** Certain sites stay blank or never finish loading in the in-app browser. The worst known cases are chat.z.ai and chat.qwen.ai. Changing the options in the shield panel (ad blocking, pop-up blocking, JavaScript, desktop site, dark page) does not help. Since turning everything off does not fix it, ad blocking is probably not the only cause.

**Desired behavior:**

- Find out for each failing site what actually breaks, and fix the cause. Likely causes to check:
    - Something the site needs for its own chat or login is being blocked by the filter lists.
    - The site does not accept the way the in-app browser identifies itself, or treats it as a bot and shows a verification check that never finishes.
    - The site needs stored login or session information that the in-app browser blocks or clears.
    - The forced dark page or the pop-up blocker interferes with how the page starts.
- Blocking must never break a page's own essential content. Anything that comes from the site itself and is needed to show the main content, log in or run a chat must be let through, even if a filter would normally block it.
- Human-verification checks ("are you a person" pages) must be able to finish.
- When a page does not load, show a clear message instead of a blank page. It says what seems to have happened (blocked by a filter, no connection, site refused, took too long) and what to try, with the buttons "Reload without blocking" (one time only) and "Report this" (see APP-02).
- The shield panel shows what was blocked on the current page and lets the user allow the whole site with one tap. The choice is remembered per site.
- Keep a list of difficult sites that is tested before every release. Start with chat.z.ai, chat.qwen.ai and the video player on the anime site from the earlier screenshot.

**Things to check when implementing:**

- Compare each failing site with a normal phone browser.
- Try each site with desktop site on and off, in a private tab, and after clearing the site's data.
- Try with freshly updated filter lists and with the filter lists turned off.
- Ask the tester who reported glitchy browsing which sites and what kind of glitch (scrolling, video, rendering).

## Depends on / affects

- BUG-02 (batch 01) already limits the rescue screen to real main-page failures; the failed-load message builds on it.
- "Report this" comes with APP-02 (batch 19); leave it out.
- Difficult-sites list: `docs/difficult-sites.md` (batch 01 may have created it); add chat.z.ai, chat.qwen.ai and the anime site's video player (ask the user for its address).
- Diagnosing needs the real sites: ask the user to test on the phone, or use the emulator only if the user agrees.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/adblock/AdblockService.kt` — `shouldInterceptRequest` (~L180), block response (~L236-245), cached prefs (~L58)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserWebViewFactory.kt` — user agent setup (~L45, ~L77-81, ~L253), forced dark (~L68, ~L263), `setAcceptThirdPartyCookies` (~L107)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/DesktopUa.kt` — `DesktopUa.transform` (~L6); `app/src/main/java/io/github/omeg4dev/fmhy/browser/PopupPolicy.kt` (pop-up blocker)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/ShieldSheet.kt` — `ShieldSheet` (~L42), blocked-count text (~L114)
- Per-site settings: `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/SiteSettingsScreen.kt` (~L173) and `SitePrefs` in `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/user/UserModels.kt` (~L52)
- Failed-load path: `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` `onMainFrameError` (~L475) and `RescueOverlay` in `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserOverlays.kt` (~L38)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.DesktopUaTest' --tests 'io.github.omeg4dev.fmhy.browser.PopupPolicyTest' --tests 'io.github.omeg4dev.fmhy.browser.adblock.AdblockServiceTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.adblock.FilterEngineTest' 2>&1 | tail -40
```

New tests:
- A test for the "first-party essential request is never blocked" rule in `AdblockServiceTest` or `FilterEngineTest`

## Done when

- [ ] BUG-01: Find out for each failing site what actually breaks, and fix the cause.
- [ ] BUG-01: Blocking must never break a page's own essential content.
- [ ] BUG-01: Human-verification checks ("are you a person" pages) must be able to finish.
- [ ] BUG-01: When a page does not load, show a clear message instead of a blank page.
- [ ] BUG-01: The shield panel shows what was blocked on the current page and lets the user allow the whole site with one tap.
- [ ] BUG-01: Keep a list of difficult sites that is tested before every release.

## Out of scope

- "Report this" (APP-02, batch 19).
- Release testing of the whole list (QA-01, batch 24).

## Screenshots

None named in the plan for this batch.

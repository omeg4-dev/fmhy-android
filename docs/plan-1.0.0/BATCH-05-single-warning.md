# Batch 05 — One warning screen (BUG-04)

## Session prompt

```text
1.0.0 batch 05 (BUG-04): One warning screen.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-05-single-warning.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
```

## Goal

A page that is both on FMHY's unsafe list and blocked by a filter shows one warning with every reason in plain sentences, "Go back" as the main button and one "Continue anyway" that lets it through both checks. Raw filter rules move to a hidden "Details" part; notes show no raw links.

## Entries

### BUG-04: Two warnings for one blocked site

**Problem:** A site that is on FMHY's unsafe-sites list and also matches an ad filter rule shows two warning screens one after the other. The first says "FMHY lists this site as unsafe" with "Continue anyway". The second says "Blocked by FMHY's filters" with "Open anyway". Users have to confirm twice and do not know why. The second screen also shows a raw filter rule that means nothing to most people, and the first shows a raw image link inside the site's note.

**Desired behavior:**

- One warning screen per blocked page, combining all reasons. Each reason is one plain sentence, for example "FMHY lists this site as unsafe" and "An ad-blocking filter blocks this site".
- The main button is "Go back". "Continue anyway" is secondary, and one confirmation lets the page through both checks.
- The raw filter rule moves to an optional "Details" part that is hidden by default.
- The site's note from the unsafe list is shown without raw links.
- The same single-warning rule applies to downloads (see BRW-02).

**Things to check when implementing:** which reason is shown first when both apply, how long "Continue anyway" is remembered (suggestion: only for the current tab session), private tabs.

## Depends on / affects

- BRW-07 (batch 02) built a helper that strips raw links from unsafe notes; reuse it.
- BRW-02 (batch 21a) applies the same single warning to downloads.
- BUG-03 (batch 01) already fixed the icon on blocked pages.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserHooks.kt` — unsafe-list interstitial decision (~L550-567, `InterstitialState` from the `SafeGuard` verdict), cleared at ~L536
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/adblock/AdblockService.kt` — main-frame `MatchResult.Block` (~L236-245) and `buildBlockPageHtml` (~L290-305): the second, HTML warning
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserOverlays.kt` — `InterstitialOverlay` (~L117); host in `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` (~L678-688, `onContinue = allowHost`)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabModels.kt` `InterstitialState` (~L17); `app/src/main/java/io/github/omeg4dev/fmhy/browser/TabManager.kt` `setInterstitialState` (~L832), `allowHost`; `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserJsBridge.kt` ("Open anyway" from the HTML page)
- Note: The two warnings come from different layers (a Compose overlay and an HTML page served from `shouldInterceptRequest`); combining them means one of them must carry the other's reason.

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.adblock.AdblockServiceTest' --tests 'io.github.omeg4dev.fmhy.ui.common.MarkdownBlocksTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.safety.SafeGuardTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.InterstitialMergeTest` (:app) for a pure combiner of the unsafe verdict and the filter block

## Done when

- [ ] BUG-04: One warning screen per blocked page, combining all reasons.
- [ ] BUG-04: The main button is "Go back".
- [ ] BUG-04: The raw filter rule moves to an optional "Details" part that is hidden by default.
- [ ] BUG-04: The site's note from the unsafe list is shown without raw links.
- [ ] BUG-04: The same single-warning rule applies to downloads (see BRW-02).

## Out of scope

- Download warnings (BRW-02, batch 21a).
- "Report this" (APP-02, batch 19).

## Screenshots

None named in the plan for this batch.

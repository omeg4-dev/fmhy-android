# Batch 07 — Back to FMHY and forward (BRW-01)

## Session prompt

```text
1.0.0 batch 07 (BRW-01): Back to FMHY and forward.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-07-back-to-fmhy.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
```

## Goal

The left slot of the browser bar always returns to FMHY, system back walks web history first, a forward button appears only when there is a page to go forward to, and the three-dot menu starts with "Back to FMHY".

## Entries

### BRW-01: Separate "back to FMHY" from web navigation

**Problem:** The in-app browser currently has a back arrow at the left of the top bar that goes back in the website's history. The floating button, which can be moved around and hidden with a long press, also shows a back arrow and returns the user to the FMHY app. Two identical arrows with different meanings are confusing. If the floating button is hidden, the only way back to FMHY is pressing back repeatedly through the whole page history. There is also no forward button except buried in the three-dot menu.

**Desired behavior:**

The left slot of the top bar becomes a dedicated "return to FMHY" button. It should use the FMHY logo or a close-style icon, not a plain back arrow, so it can't be confused with web back. Pressing it always leaves the in-app browser and returns to the app, no matter how many pages deep the user is. This works the same whether the bar is at the top or the bottom.

Web back is handled by the system back gesture or button. If the page has earlier history, back goes to the previous page. Only when there is no earlier page does back return the user to FMHY. This keeps the hide dialog's promise that "Back still returns to FMHY" true.

A forward button appears next to the left slot only when there is a page to go forward to. When there is none it is hidden completely, so the bar stays clean and the address field doesn't jump around. Fade or slide it in and out so the layout change feels smooth.

Reload is available through pull-to-refresh on the page, and the existing entry in the three-dot menu stays.

The three-dot menu gets a new first entry, "Back to FMHY". This makes hiding the floating button safe, because the return path is then always available in two places in the bar itself.

**Alternative to consider if the extra button feels too crowded:** one left-slot button that shows a back arrow when there is page history and switches to the FMHY icon on the first page. The risk is that the changing icon feels unpredictable, and getting back to FMHY from a deep page takes many taps. To soften this, a long press could show the page history list with FMHY as the last entry.

**Rejected:** A permanent bottom toolbar with back, forward, reload and FMHY buttons. It is easy to discover but uses vertical space and conflicts with the option to move the bar to the bottom.

**Things to check when implementing:**

- The hide dialog text and the settings label for the floating button should match the new behavior.
- The back gesture must not exit the browser while there is still web history.
- The forward button's visibility must update after every page navigation, including redirects and single-page-app route changes.
- The new left-slot icon should be clearly different from the web back icon in both light and dark themes.

## Depends on / affects

- ONB-01 (batch 17) lists FMHY icons; use a bundled FMHY logo or a close-style icon now.
- THM-01 (batch 11) restyles the floating button.
- SET-01 (batch 23) moves the floating-button setting; only update its label text here.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserToolbar.kt` — back button (~L175-180, `enabled = tab?.canGoBack`), `onBack` (~L253-255, ~L657, ~L695), `canGoForward` near it
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BrowserScreen.kt` — `BackHandler(enabled = true)` (~L250), floating button slot (~L622)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/shell/BrowserMenuSheet.kt` — `BrowserMenuSheet` (~L84)
- `app/src/main/java/io/github/omeg4dev/fmhy/browser/BackBubble.kt` — `BackBubble` (~L64), hide dialog text (~L198); `app/src/main/java/io/github/omeg4dev/fmhy/browser/BubbleLogic.kt` `backBubbleVisible` (~L34)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/BrowserPage.kt` — "Back-to-FMHY button" row (~L146)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.shell.BrowserMenuSheetTest' --tests 'io.github.omeg4dev.fmhy.ui.shell.BrowserBarItemTest' --tests 'io.github.omeg4dev.fmhy.browser.BubbleLogicTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.BackActionTest` (:app) for the pure back decision (web back while there is history, then FMHY)

## Done when

- [ ] BRW-01: The left slot of the top bar becomes a dedicated "return to FMHY" button.
- [ ] BRW-01: Web back is handled by the system back gesture or button.
- [ ] BRW-01: A forward button appears next to the left slot only when there is a page to go forward to.
- [ ] BRW-01: Reload is available through pull-to-refresh on the page, and the existing entry in the three-dot menu stays.
- [ ] BRW-01: The three-dot menu gets a new first entry, "Back to FMHY".

## Out of scope

- The floating button's look (THM-01, batch 11).

## Screenshots

None named in the plan for this batch.

# W1 browser progress
- [x] 1. B1 flash-and-close (restored flow, queue atomic, new-tab extras, tests, coldstart before/after)
  - TabManager.restored StateFlow, shouldFinishWhenEmpty, BrowserLinkQueue.completeRestore (atomic), EXTRA_NEW_TAB_PAGE/PRIVATE handled, extras deferred until restored. Unit tests ShouldFinishWhenEmptyTest (5 pass).
  - Emulator: race NOT reproducible (before 10/10 + 10/10 resumed, after 10/10 + 10/10; .work-scripts/coldstart-*.txt; BrowserActivity not exported -> script uses adb root). Cause confirmed by code reading only. New-tab extra: cold = 1 blank tab (no duplicate), warm private = 2nd tab private (shots/newtab-*.png).
  - NOTE: tools/install.sh deadlocks inside emu.sh (same flock file); use .work-scripts/install-nolock.sh inside emu.sh.
- [x] 2. B2 status icon in address pill (siteStatusIcon hook, popover DropdownMenu, StatusIconSpecTest; shots/b2-top-*.png; settings label renamed)
  B2 FMHY status icon in address pill (remove site bar row, popover, label rename, test)
- [x] 3. B1b (TOP: after B2, 1 hide / 0 shows after lift for slow drag and fling on a long local page; bars-after-top.txt; flicker not reproducible once site bar row is gone; also keyed force-show effect on URL minus fragment. BOTTOM run pending in final check)
  B1b bars reshow on release (TOP)
- [x] 4. B3 back-arrow bubble (BackBubble.kt, settings switch, BackBubbleLogicTest 3 pass; emulator TOP: default/dragged right/hidden with bar/hold menu/dialog/tap->FMHY ok; shots/b3-top-*.png; BOTTOM shots pending final check)
- [ ] 5. B4 tab overview redesign (TabsSheet.kt)
- [ ] 6. Checks + evidence

## Requests for Claude
(none)

# W1 browser progress
- [x] 1. B1 flash-and-close (restored flow, queue atomic, new-tab extras, tests, coldstart before/after)
  - TabManager.restored StateFlow, shouldFinishWhenEmpty, BrowserLinkQueue.completeRestore (atomic), EXTRA_NEW_TAB_PAGE/PRIVATE handled, extras deferred until restored. Unit tests ShouldFinishWhenEmptyTest (5 pass).
  - Emulator: race NOT reproducible (before 10/10 + 10/10 resumed, after 10/10 + 10/10; .work-scripts/coldstart-*.txt; BrowserActivity not exported -> script uses adb root). Cause confirmed by code reading only. New-tab extra: cold = 1 blank tab (no duplicate), warm private = 2nd tab private (shots/newtab-*.png).
  - NOTE: tools/install.sh deadlocks inside emu.sh (same flock file); use .work-scripts/install-nolock.sh inside emu.sh.
- [ ] 2. B2 (code done: siteStatusIcon hook, pill icon, FMHY pill removed, settings label+back switch; emulator check pending)
  B2 FMHY status icon in address pill (remove site bar row, popover, label rename, test)
- [ ] 3. B1b bars reshow on release (TOP)
- [ ] 4. B3 back-arrow bubble (BackBubble.kt, settings, tests)
- [ ] 5. B4 tab overview redesign (TabsSheet.kt)
- [ ] 6. Checks + evidence

## Requests for Claude
(none)

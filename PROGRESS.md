# p8-browser progress

- [x] 1. Bar hopping: reproduce, fix, before/after
- [ ] 2. Thinner site bar (28 dp)
- [ ] 3. "‹ FMHY" pill + fullscreen check
- [ ] 4. Floating browser bubble (+ settings, prefs, tests)
- [ ] 5. Checks (haptics rg, compile, tests, evidence)
- [ ] 6. Final report

## Haptics call sites changed
(file:line | old | new/removed)

## Log
- T1: BrowserScreen.kt now defers all bar/webView translation while a pointer is down (applied on UP/CANCEL), uses accumulated scroll (48dp hide / 32dp show, BarScrollTracker.kt + unit test), re-snaps hidden target when bar block height changes, uses rememberUpdatedState for listeners. Debug logger tag FmhyBars. Reproduced: old code slow drag TOP = 92 webView translation writes (all with finger down) and 26 oscillating target flips; fast swipe = 3-6 writes while down. After: slow drag = 0 writes while down, 1 hide animation after UP (12 frames), fast up/down = exactly 1 hide + 1 show, 0 writes while down. BOTTOM: 1 hide (site bar hides with toolbar, screenshot after-bottom-after-swipe-up.png) + 1 show. Evidence: .work-scripts/bars.sh, bars-before.txt, bars-after.txt, bars-after-bottom.txt, shots/. Note: emulator has the release-signed app, so .work-scripts/install.sh re-signs the debug APK with the release key. Emulator was found with toolbar position BOTTOM (brief said TOP); currently BOTTOM, will set TOP at the end.

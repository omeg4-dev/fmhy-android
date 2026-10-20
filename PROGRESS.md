# p8-browser progress

- [x] 1. Bar hopping: reproduce, fix, before/after
- [x] 2. Thinner site bar (28 dp)
- [x] 3. "‹ FMHY" pill + fullscreen check
- [ ] 4. Floating browser bubble (+ settings, prefs, tests)
- [ ] 5. Checks (haptics rg, compile, tests, evidence)
- [ ] 6. Final report

## Haptics call sites changed
(file:line | old | new/removed) -- line numbers are pre-edit; browser/** only
TabsSheet.kt:268 | haptics.click() | haptics.remove()
TabsSheet.kt:227 | haptics.click() | haptics.remove()
TabsSheet.kt:223 | haptics.tick() | removed
TabsSheet.kt:191 | haptics.click() | haptics.remove()
TabsSheet.kt:152 | haptics.tick() | removed
TabsSheet.kt:138 | haptics.tick() | removed
TabsSheet.kt:127 | haptics.tick() | removed
ContinueBrowsingCard.kt:77 | haptics.click() | removed
ContinueBrowsingCard.kt:65 | haptics.click() | removed
BrowserSwitch.kt:70 | haptics.click() | removed
BrowserToolbar.kt:789 | haptics.tick() | if (isSaved) remove() else success()
BrowserToolbar.kt:893 | haptics.tick() | removed
BrowserToolbar.kt:884 | haptics.tick() | removed
BrowserToolbar.kt:875 | haptics.tick() | removed
BrowserToolbar.kt:865 | haptics.tick() | removed
BrowserToolbar.kt:855 | haptics.tick() | haptics.toggle(!it)
BrowserToolbar.kt:830 | haptics.tick() | haptics.toggle(tab?.desktopSite != true)
BrowserToolbar.kt:820 | haptics.tick() | removed
BrowserToolbar.kt:811 | haptics.tick() | removed
BrowserToolbar.kt:802 | haptics.tick() | removed
BrowserToolbar.kt:778 | haptics.tick() | removed
BrowserToolbar.kt:767 | haptics.tick() | removed
BrowserToolbar.kt:758 | haptics.tick() | removed
BrowserToolbar.kt:747 | haptics.tick() | removed
BrowserToolbar.kt:736 | haptics.tick() | removed
BrowserToolbar.kt:547 | haptics.tick() | removed
BrowserToolbar.kt:417 | haptics.tick() | removed
BrowserToolbar.kt:414 | haptics.tick() | removed
BrowserToolbar.kt:264 | haptics.tick() | removed
BrowserToolbar.kt:238 | haptics.tick() | removed
BrowserToolbar.kt:177 | haptics.tick() | removed
BrowserScreen.kt double-tap-seek tick() and pull-to-refresh tick() kept.
BrowserToolbar.kt Back-to-FMHY click: removed (replaced by pill).

## Log
- T1: BrowserScreen.kt now defers all bar/webView translation while a pointer is down (applied on UP/CANCEL), uses accumulated scroll (48dp hide / 32dp show, BarScrollTracker.kt + unit test), re-snaps hidden target when bar block height changes, uses rememberUpdatedState for listeners. Debug logger tag FmhyBars. Reproduced: old code slow drag TOP = 92 webView translation writes (all with finger down) and 26 oscillating target flips; fast swipe = 3-6 writes while down. After: slow drag = 0 writes while down, 1 hide animation after UP (12 frames), fast up/down = exactly 1 hide + 1 show, 0 writes while down. BOTTOM: 1 hide (site bar hides with toolbar, screenshot after-bottom-after-swipe-up.png) + 1 show. Evidence: .work-scripts/bars.sh, bars-before.txt, bars-after.txt, bars-after-bottom.txt, shots/. Note: emulator has the release-signed app, so .work-scripts/install.sh re-signs the debug APK with the release key. Emulator was found with toolbar position BOTTOM (brief said TOP); currently BOTTOM, will set TOP at the end.
- T2: site bar now 28 dp (Row height, labelSmall, 14dp icons, 28dp Box dismiss). Evidence shots/pill-sitebar-{top,bottom}.png (bar ~74px tall at 2.625 density).
- T3: 'FMHY' pill (primaryContainer, 32dp, chevron + bold label, 48dp touch). Fits at 1080px wide emulator (not tested at 360dp). Fullscreen: shots/fullscreen.png shows no toolbar/pill (served via adb reverse + http://10.0.2.2:8123/fs.html; localhost blocked by app).
- Haptics: all click()/confirm() removed from browser/** (rg check clean).

# W2 shell progress
- [x] 1 N3 random pick stays put — stablePick() in HomeViewModel, test testRandomPickStaysPutUntilRefresh passes (13/13 HomeViewModelTest)
- [x] 2 N2 remove Continue browsing — HomeScreen item + ContinueBrowsingCard + test deleted; rg 'continue ?browsing' in app/src clean
- [x] 3 N1 Browser bar item + menu sheet — verified on emulator (4 items, badge, live menu, close-all dialog): .work-scripts/shots/n1-*.png
- [x] 4 N4 notices top overlay — verified: ad-block + sync pills over Home, Search field y=389 unchanged (n4-notice-2.png, n4-home-*.txt)
- [x] 5 N5 enterStagger fix — fling strip shows no blank bands (shots/n5-section-strip.png; script's blank% metric is unreliable on dark bg)
- [x] 6 N6 swipe commit on release — swipe-check 5/5, release->DB 27ms/12ms (save/unsave, swipe-save.out); Saved remove 10ms (swipe-remove.out)
- [x] 7 checks — compile OK, unit tests 356/0 failed, no crash log

## Requests for Claude

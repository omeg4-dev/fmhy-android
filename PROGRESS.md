# p8-swipe progress
- [x] 1 SwipeDecision + tests
- [x] 2 Rewrite SwipeToSave
- [x] 3 Saved screen row
- [x] 4 Idempotent save/unsave
- [x] 5 Haptics audit
- [x] 6 Checks
- [x] 7 Final commit/report

## Haptics call-site table
file:line | old | new | why
ui/home/FmhyWordmark.kt:104 | click | tick | easter-egg tap keeps a light tick
ui/settings/SettingsComponents.kt:199 | tick | toggle(it) | switch row
ui/settings/SettingsComponents.kt:253,301,591 | click | removed | plain taps (button/open dialog)
ui/settings/SettingsComponents.kt:373 | tick | removed | expand/collapse tap
ui/check/CheckLinkScreen.kt:169 | confirm | success | safe verdict
ui/check/CheckLinkScreen.kt:171 | longPress | error | unsafe verdict
ui/home/HomeScreen.kt:326,407,453,486,534,625,715,804 | click | removed | plain taps/navigation/refresh/show more
ui/home/HomeScreen.kt:643 | click | remove | unpin
ui/onboarding/OnboardingScreen.kt:158,168,174 | click | removed | skip/back/next navigation
ui/onboarding/OnboardingScreen.kt:807,987,1026 | tick | toggle(new) | switches
ui/onboarding/OnboardingScreen.kt:934 | tick | toggle(!isSelected) | filter chip
ui/settings/AppearancePage.kt:389 | tick | removed | opens custom colour dialog
ui/settings/AppearancePage.kt:651 | confirm | success | icon applied
ui/onboarding/OnboardingComponents.kt:262 | confirm | success | done check animation
ui/whatsnew/WhatsNewScreen.kt:159,217 | tick | removed | opens sheet
ui/whatsnew/WhatsNewScreen.kt:170,259 | click | removed | refresh / reset filters taps
ui/whatsnew/WhatsNewScreen.kt:205 | tick | remove | clear type filter
ui/whatsnew/WhatsNewScreen.kt:339 | tick | toggle(sectionId !in selected) | section checkbox
ui/section/SectionItems.kt:453,463 | tick | removed | selecting a chip/page
ui/posts/PostBody.kt:123 | click | removed | open
ui/section/CategoryScreen.kt:312,334,404 | click | remove | unfollow/unpin
ui/section/SectionScreen.kt:313,334,420,745,767 | click | remove | unfollow/unpin
ui/posts/PostsScreen.kt:95,227 | click | removed | refresh / open post (declaration+import removed)
ui/search/SearchScreen.kt:423 | tick | removed | suggestion tap (declaration+import removed)
ui/search/SearchScreen.kt:499 | click | removed | navigate
ui/saved/SavedScreen.kt:442,479,549,581,696,725 | click | remove | remove bookmark / delete folder / clear history
ui/common/EntryRow.kt:205 | click | remove | unfollow
ui/common/EntryRow.kt:347,404,713,749,953,975,1052,1088 | click | removed | open sheet / open / share taps
ui/common/EntryRow.kt:731,964,1070 | confirm | success | copy link
ui/common/Bookmarks.kt | confirm/click | success/remove | save/unsave (idempotent)
Navigation.kt ticks: not touched (off-limits per brief).

## Checks
- rg click|confirm outside browser: no output
- compileDebugKotlin: BUILD SUCCESSFUL, no w:/e: lines
- testDebugUnitTest: 336 tests, 0 failures, 0 errors
- assembleDebug: BUILD SUCCESSFUL

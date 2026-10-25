# Batch 24 — Bug hunt (QA-01)

## Session prompt

```text
1.0.0 batch 24 (QA-01): Bug hunt.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-24-bug-hunt.md, nothing else from docs/plan-1.0.0/.
Run the bug hunt exactly as that batch file says; I do the visual checks on the phone.
```

## Goal

A cheap crash hunt: random-input runs on the emulator plus a log grep for crashes, freezes and StrictMode hits, written up as findings. The user does the visual and feel checks on the phone.

## Entries

### QA-01: Bug hunt with the Android emulator

**Goal:** Use the app extensively, the way real users do and in ways they do not expect, and find as many unexpected behaviors as possible before release. The result is a list of findings that is precise enough to fix each one without asking questions. The work is done on an Android emulator, driven through adb, the tool that lets a computer tap, type, swipe and press keys on the emulated phone, install the app, read the app's log and take screenshots and screen recordings. The tester can be a person or an AI assistant working through adb.

**Setting up**

- Several emulated phones: the oldest supported one (Android 8.0), a current Android version, a tall modern phone and a tablet (and a foldable if available). Optionally one with a large font and display size.
- Each one starts from a saved clean state, so any test can be started again from scratch.
- Two kinds of installs: a fresh install (the first-run setup is shown) and an update from the previous version. The update checks that old data and settings survive, and that the one-time hints from UI-02 and UI-03 appear.
- Two builds: the release-like build that users get and, when needed, a debug build with more detailed logging.
- Everything is recorded: a screen recording per session, a screenshot at every problem, and the app's log from shortly before the problem until after it.
- Safety rules: the emulator has no personal accounts, passwords or private files. Downloads use harmless test files only. Sites from FMHY's unsafe list are opened only to test the warnings, and nothing is installed from them.

**What to cover**

All areas of the app, old and new. For each one, try the normal way first, then the unusual ways.

- The wiki lists: sections, pages, long entries, entries with many icons, tap and hold, swiping, website style on and off, text size, and jumping to a section or entry from every place that offers it (see BUG-08 and BRW-05).
- Search: typing, typing mistakes, the search filters, empty results, very long input, emojis, pasted text.
- Saved sites: saving, folders, sharing a list, removing, a very large number of saved sites.
- Link checker: sharing a link from another app, pasting one, links that are listed, starred, fake lookalikes and broken.
- Changes and follows: "What's new", following sections, notifications, saved sites that were removed or moved.
- Settings and look: themes, light, dark and pure black, accent colors, switching them while the app is open, app icons, widget, launcher shortcuts, Random pick.
- First-run setup (ONB-01): every step, going back, closing the app in the middle, being offline.
- Browser: opening it in every possible way, tabs and private tabs (also a very large number of tabs), bar at the top and at the bottom, the shield panel switches, ad blocking, pop-ups and forced redirects, unsafe-site warnings, mirror and Wayback suggestions, video in picture-in-picture, playback speed, double-tap to seek, the floating button, the bar hiding and showing while scrolling (BUG-09), tab groups (BRW-08) and empty tabs closing by themselves (BRW-09).
- The 1.0.0 changes: every entry in this plan that is not yet Done is tested against its "Desired behavior" and "Things to check" lists.

**Ways to provoke unexpected behavior**

- Rotate the phone, change font size and display size, switch between dark and light, change the language (also a right-to-left language), all in the middle of an action.
- Send the app to the background and bring it back later. Stop the app's process while a download or page load is running. Restart the phone.
- Turn airplane mode on and off, use a slow or unstable network with high delay and lost data, switch between Wi-Fi and mobile data, use no network at all. The Wi-Fi only switches from BRW-03 are tested here too, and so is the progress of ad-block and wiki updates (BUG-07).
- Fill the storage, deny permissions, turn on battery saver and data saver, change the date, time and time zone.
- Use the back gesture, the back button and three-button navigation, split screen and a small window.
- Tap fast and repeatedly, tap two things at once, hold everywhere, swipe and drag in the wrong places, open and close screens quickly.
- Unusual input: very long text, empty text, only spaces, emojis, unusual web addresses (unfinished, with odd characters, local addresses, other kinds of links), links opened from other apps.
- Random input runs: let a tool tap and swipe at random for a long time, then check the log for crashes and freezes.
- Repeat the most important flows many times in a row, to find slowdowns, leaks and crashes after long use.

**Test sites**

- Keep and extend the list of difficult sites from BUG-01 (chat.z.ai, chat.qwen.ai, the anime site's video player) and BUG-02 (streamed.pk, virgil.samidy.com).
- Add a mix: sites with login, video, endless scrolling, many ads, redirects, pop-ups, human-verification checks, and downloads of every kind (a direct file, a redirect to a file, a file created by the page itself).
- Open each site again with the shield switches changed one at a time.

**What counts as unexpected behavior**

Not only crashes. Also: freezes and slowness, wrong or missing text, things that look tappable but do nothing, wrong or leftover icons, overlapping or cut-off elements, a tap that does something other than expected, errors that appear even though the content loads (like BUG-02), state that is lost or wrong after rotation or restart, settings that do not apply, wrong or missing notifications, and anything that contradicts this plan, the README or the app's own texts.

**Reporting the findings**

- Each finding is its own entry in a findings note next to this plan, with: a short title, the steps to reproduce it, what was expected, what happened, how often it happens, the device, Android version, app version and relevant settings, the evidence (screenshot, recording, log excerpt), a severity and the area of the app.
- Severity levels: Blocker (crash, data loss, cannot continue), Major (a feature does not work and there is no easy way around it), Minor (works with a workaround or has a small effect), Cosmetic (only looks wrong).
- Before adding a finding, check whether it is already an entry in this plan (for example BUG-01 to BUG-11) and link to that ID instead of creating a duplicate. Findings that are new and worth fixing become new entries in this plan, using the template at the end.
- The findings note starts with a short summary (the number of findings per severity and per area) and a list of what was covered and what was not.
- Fixed findings are tested again on the same device and with the same settings where they appeared, and marked as verified.

**Done when**

- Every area under "What to cover" was exercised on at least two emulated phones, one of them the oldest supported Android version.
- Every item under "Ways to provoke unexpected behavior" was tried at least once in the browser and in the lists.
- No Blocker is open, and every finding has a severity and a decision (fix now, later, or will not fix).
- The findings note lists what could not be tested on an emulator.

**Limits of the emulator** (to be checked later on a real phone)

- Real mobile networks and how carriers behave.
- Hardware video decoding and protected video, and picture-in-picture on phone makers' versions of Android.
- Battery and background limits added by phone makers, and notifications on those versions.
- The feel of touch and scrolling, and performance on low-end phones.
- File access and the install permission on phone makers' versions.

**Things to check when implementing:**

- Run it twice: once when most features have landed, and again on the release candidate.
- Keep the list of emulated phones and the list of test sites with this plan, so the next run starts the same way.

## Depends on / affects

- Runs after most batches have landed, and again on the release candidate.
- No agy here: run the commands below yourself (agy agents must not use the emulator).
- Read `docs/difficult-sites.md` (from batches 01 and 04) for the sites to open by hand.

## Where to start in the code

- Package `io.github.omeg4dev.fmhy`, launcher aliases start at `.LauncherDefault`; main activity `.MainActivity`, browser `.browser.BrowserActivity`, checker `.CheckLinkActivity`
- Log tags: `FmhyApp`, `FmhySync`, `FmhyAdblock`, `TabManager`, `AppContainer`, `FmhyWidget`, `TrackerSync`, `FmhyBars`; debug builds log StrictMode (`app/src/main/java/io/github/omeg4dev/fmhy/FmhyApp.kt` ~L27-32)
- Existing instrumented smoke test: `app/src/androidTest/java/io/github/omeg4dev/fmhy/AppSmokeTest.kt`

How to run it (emulator only, keep it cheap):

```bash
./gw -q :app:assembleDebug 2>&1 | tail -20
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
# 3 seeds x 5000 events; raise -v for more detail. Package-locked, no system keys.
for s in 1 2 3; do adb shell monkey -p io.github.omeg4dev.fmhy -s $s --throttle 120 --pct-syskeys 0 \
  --ignore-crashes --ignore-timeouts -v 5000 > /tmp/monkey-$s.txt; done
adb logcat -d -b crash,main > /tmp/fmhy-logcat.txt
rg -n 'FATAL EXCEPTION|ANR in|AndroidRuntime|StrictMode|E (FmhyApp|FmhySync|FmhyAdblock|TabManager|AppContainer)' /tmp/fmhy-logcat.txt
rg -n 'CRASH|NOT RESPONDING' /tmp/monkey-*.txt
```

## Tests

No unit tests; the run above is the check.

## Done when

- [ ] Three monkey seeds ran on one emulator (ask the user which AVD; start it only after they agree).
- [ ] Every crash, ANR and StrictMode hit from the log is grouped into one finding each, with the stack top, the monkey seed and event count, and a severity (Blocker, Major, Minor, Cosmetic).
- [ ] Each finding is checked against the 1.0.0 IDs and linked to one when it matches.
- [ ] Findings are written to `docs/plan-1.0.0/qa-findings.md` (summary by severity and area first, then what was and was not covered). The user copies them into the Obsidian note if wanted.
- [ ] A short list of visual checks for the user, taken from "What to cover" and the difficult-sites list.

## Out of scope

- Everything in QA-01 beyond the random-input run and the log check; the user covers visual checks.

## Screenshots

None named in the plan for this batch.

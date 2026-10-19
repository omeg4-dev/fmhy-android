# FMHY Android: current status

As of **2026-10-19**, version **0.2.0** (versionCode 2), `main` at `2de4b57`. Nothing pushed; the repo
has no remote yet.

Unofficial native Android client for [FMHY](https://fmhy.net). It needs no account and no server of
ours: data comes straight from FMHY's public sources and is parsed on the device.

## Install

- APK: `dist/fmhy-0.2.0.apk` (11.6 MB, R8-shrunk release build, **signed with the debug key**, so
  it can only be sideloaded).
- `adb install -r dist/fmhy-0.2.0.apk` upgrades 0.1.0 in place and keeps data.
- Android 8.0+ (minSdk 26), target/compile SDK 36. Material You needs Android 12+.

## What the app does

### Data and sync
- Sources: `raw.githubusercontent.com/fmhy/edit` (jsDelivr as automatic fallback),
  `rentry.co/FMHYB64` (base64 links), FMHYFilterlist (unsafe sites).
- A snapshot (21,665 sites, 23 sections) ships in the APK, so the app works offline from the first
  launch.
- Background sync on launch. Each of the 27 source files is checked with an ETag; only changed
  files are downloaded and re-parsed. Up to date: 0.3–0.6 s. Full refresh: ~2.5 s. A sync runs at
  most every 10 min unless you tap *Sync now*.
- Base64 links are decoded automatically (120). Sites on FMHY's unsafe list (328 domains) show a
  warning before opening.
- Links open in a Chrome Custom Tab, or in the external browser (setting).

### Navigation
- Bottom bar **Browse / Search / Saved**. Each tab keeps its own back stack and scroll position.
  Tapping the current tab returns to its root, then scrolls to the top. Back on the Search or Saved
  root goes to Browse, and back on Browse's root exits.
- The bar slides away while you scroll down and comes back when you scroll up. It is hidden on
  Settings.
- Predictive back is on. Pages slide in and fade (220 ms); tabs cross-fade.
- **In-app cross-links:** FMHY links to its own pages (old reddit wiki and fmhy.net URLs, e.g.
  "Spotify Adblockers", or headings like `[Android Adblocking](…)`) open the target section inside
  the app, scroll to the heading and highlight it briefly. 484 of 493 anchored links resolve on the
  bundled data. The other 9 point to headings FMHY no longer has, so they open the section top or
  the browser.

### Screens
- **Browse (Home):** search pill ("Search 21,665 sites"), sync banner, random pick, "N new since
  your last visit" row, and a list of the 23 sections with icon and site count.
- **Section:** small pinned top bar and an *Outline* sheet (category → sub-heading tree). Two
  layouts, chosen in Settings:
  - *Continuous* (default): one scroll, with a sticky **category chip rail** that follows the
    scroll and jumps on tap.
  - *Categories:* a list of categories (site count + first sub-headings). Tap one to open a page
    with just that category.
- **Entry rows:** name, small ★ (starred by FMHY) or "Top" pill, key icon for base64 links,
  bookmark icon when saved, and an arrow plus "Opens in the app" for cross-links. Description
  (2 lines) and inline mirror links.
  - Long-press (with haptic) opens a sheet with Open · Copy · Share · Save, plus mirrors, extra
    links and the original base64 link.
- **Notes** (FMHY's callouts) are collapsed to 3 lines; tap to expand.
- **Search:** pill field and a **Filters** sheet (Starred by FMHY, Hidden/base64 links, sections),
  with active filters shown as removable chips. Results are grouped by section, with a heading
  breadcrumb per result.
  - Empty state: recent searches and example queries.
  - Query syntax: `in:<section>`, `is:starred`, `is:top`, `is:b64`, `site:<host>`, plus a typo and
    substring fallback.
- **Saved:** local bookmarks grouped by section, newest first. A site FMHY has removed stays listed
  as "No longer listed on FMHY" until you remove it. Bookmarks live in their own DataStore, so syncs
  never touch them.
- **Settings:**
  - Appearance: Theme (System / Light / Dark), Colours (FMHY / Material You, with swatch previews),
    Pure black (dark mode only), Section layout.
  - Links: open in external browser.
  - Data: sites count, last synced, Sync now.
  - About.

### Look
- Own palette generated from one seed colour (FMHY violet `#7C5CFF`) with
  `com.materialkolor:material-color-utilities` 4.1.1, covering all M3 colour roles. A unit test
  checks the WCAG contrast of each scheme.
- Material You takes its colours from the wallpaper. Pure black sets the backgrounds to #000 and
  keeps containers slightly lifted.
- Status and navigation bar icons follow the chosen theme, even when it differs from the system
  one.
- Full type scale and shapes in `theme/`. A splash screen in palette colours, and a monochrome
  layer so Android 13 can show a themed launcher icon.

## Architecture

| Module / folder | Role |
|---|---|
| `core/data` | Pure JVM. SQLite (`BundledSQLiteDriver`, FTS5 word + trigram search), Kotlin FMHY parser, sync, search, cross-links |
| `core/data/.../parse/FmhyParser.kt` | Markdown → sections, headings, entries, notes |
| `core/data/.../DatasetStore.kt` | All reads; `sectionItems()` builds a section in document order |
| `core/data/.../sync/FmhySync.kt` | ETag sync with fallback mirror |
| `core/data/.../link/CrossLinks.kt` | Maps FMHY-internal URLs to `LinkTarget(sectionId, headingId)` |
| `app/.../nav/AppNavState.kt` | Tabs + per-tab back stacks (pure, unit-tested) |
| `app/.../Navigation.kt` | Shell: bottom bar, `NavDisplay` per tab, routes |
| `app/.../data/` | `SettingsRepository` (+ `AppearancePrefs`), `BookmarksRepository` (DataStore) |
| `app/.../theme/` | `FMHYTheme(appearance)`, seed colour schemes, type, shapes |
| `app/.../ui/{home,section,search,saved,settings,common}` | Screens and shared rows/sheets |
| `pipeline/` | Python **reference parser only** (parity checks); no hosted pipeline |
| `schema/dataset.sql` | Database schema shared by both parsers |

**Data gotcha:** heading `ord` and entry/note `ord` are *separate* counters. Never merge them by
`ord`; `sectionItems()` places every item under its own `heading_id`. (Merging by `ord` was the
0.1.0 bug that shuffled headings and entries.)

## Build and checks

Build only with `./gw` (offline Gradle with a repo-local Gradle home). Use `GW_ONLINE=1 ./gw …` once
when a new dependency has to be downloaded.

```bash
./gw :core:data:test :app:testDebugUnitTest   # 102 unit tests (68 core + 34 app), all passing
./gw :core:data:parityTest                    # Kotlin vs Python parser: 0 mismatches
./gw :app:assembleRelease                     # → app/build/outputs/apk/release/app-release.apk
```

Emulator: AVD `fmhy36`, start it with `ANDROID_AVD_HOME=~/.config/.android/avd`.

Verified for 0.2.0 on the emulator (light, dark, pure black):
- Section order in all 23 sections, also checked by a test against the bundled snapshot.
- Cross-link jump: "Spotify Adblockers" → Music / Podcasts / Radio › Spotify Tools.
- Tabs keep their place; back behaviour.
- A bookmark survives a reinstall and *Sync now*.
- Theme, colour source and pure black apply instantly and persist after a restart.
- The Categories flow.
- A release-build click-through with no crashes in logcat.

## Known issues and open work

1. **Search caps results at 200.** The count shows "200 results" even when more match
   (`SearchEngine` default `limit = 200`). Paging or a "200+" label is needed.
2. **"Recently added" screen** is still missing. The data (added/removed per sync) is recorded, and
   only the "new since last visit" row uses it.
3. **Release signing:** the APK uses the debug key. A real keystore and a GitHub/F-Droid release
   still need a decision; there is no git remote yet.
4. **Custom accent colour:** the seed is already a preference (`accentSeed`) and `ColorSource` is an
   enum, so a colour picker is a UI-only addition.
5. Small polish:
   - The bottom bar stays hidden after a chip-rail jump until you scroll up.
   - Rows with mirrors are taller (48 dp touch target).
   - The *Outline* button on the Categories list repeats the list.
   - 9 cross-links don't resolve (stale FMHY anchors).
6. `README.md` is outdated: it still says `pipeline/` builds the dataset.
7. Old Python history/delta code in `pipeline/` is unused (kept as the reference parser).

## History

| Version | Date | Summary |
|---|---|---|
| 0.1.0 | 2026-10-19 (night) | Direct sync from fmhy/edit, Kotlin parser, bundled snapshot, Home/Section/Search/Settings |
| 0.2.0 | 2026-10-19 | Fixed section order, in-app cross-links, bottom-bar navigation, Saved, themes (System/Light/Dark, FMHY/Material You, pure black), Categories layout, calmer rows, search filters, splash |

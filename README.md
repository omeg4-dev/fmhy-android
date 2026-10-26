<div align="center">

<img src="app/src/main/res/drawable-nodpi/fmhy_logo_hero.png" width="120" alt="FMHY logo">

# FMHY for Android

[![Kotlin](https://forthebadge.com/api/badges/generate?panels=2&primaryLabel=MADE+WITH&secondaryLabel=KOTLIN&primaryBGColor=%23a530f3&secondaryBGColor=%237f39d5&tertiaryBGColor=%23FFFFFF&primaryTextColor=%23FFFFFF&primaryFontSize=12&primaryFontWeight=600&primaryLetterSpacing=2&primaryFontFamily=Roboto&primaryTextTransform=uppercase&secondaryTextColor=%23FFFFFF&secondaryFontSize=15&secondaryFontWeight=900&secondaryLetterSpacing=2&secondaryFontFamily=Montserrat&secondaryTextTransform=uppercase&borderRadius=10&scale=0.60&secondaryIcon=kotlin&secondaryIconColor=%23FFFFFF&secondaryIconSize=16&secondaryIconPosition=left)](https://kotlinlang.org/)
[![Release](https://img.shields.io/github/v/release/omeg4-dev/fmhy-android)](https://github.com/omeg4-dev/fmhy-android/releases/latest)
[![Last Commit](https://img.shields.io/github/last-commit/omeg4-dev/fmhy-android)](https://github.com/omeg4-dev/fmhy-android/commits/main)
[![Downloads](https://img.shields.io/github/downloads/omeg4-dev/fmhy-android/total)](https://github.com/omeg4-dev/fmhy-android/releases)
[![Stars](https://img.shields.io/github/stars/omeg4-dev/fmhy-android?style=flat)](https://github.com/omeg4-dev/fmhy-android/stargazers)
[![License](https://img.shields.io/github/license/omeg4-dev/fmhy-android)](https://github.com/omeg4-dev/fmhy-android/blob/main/LICENSE)


**The whole FMHY wiki in your pocket: searchable, offline, with an ad-blocking browser and a link checker.**

[**⬇ Download the APK**](https://github.com/omeg4-dev/fmhy-android/releases/latest) · Android 8.0+ · free & open source · no account · no ads · no tracking

</div>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250" alt="The whole wiki, in your pocket">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250" alt="Find anything in a second">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="250" alt="Check a link before you click">
</p>

## Highlights

- ⚡ **Instant offline search** over 21,000+ sites: typo-proof, with filters like `in:video anime`, `is:starred adblock`, `site:github.com`.
- 🛡️ **Built-in browser with real ad blocking** (uBlock Origin and EasyList lists), pop-up and redirect shield, unsafe-site warnings, and a mirror or Wayback one tap away when a site is down.
- 🔗 **Check any link before you open it**: paste or share from any app and see if it is on FMHY, starred, or a fake lookalike.
- 🔔 **What's new**: every site added, starred, removed or moved, plus "Since last visit". Follow sections and get one quiet notification.
- ⬇️ **Download manager** with pause and resume, and optional VirusTotal / Hybrid Analysis checks (your own free key, off by default).
- 🎨 **Make it yours**: light, dark or pure black, ten accent palettes or Material You, pins on Home, a widget, saved folders you can share.
- 🔒 **Private**: no account, no ads, no tracking, no server of ours. In-app updates, and nothing installs without a tap.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250" alt="The best sites, starred">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="250" alt="Never miss what changed">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="250" alt="Make it yours">
</p>

## More

<details>
<summary><b>Search and reading</b></summary>

- Fuzzy search finds `stremio` when you type `stremo`; switch to exact any time. The 3 best-fitting categories come first, starred picks rank higher, matches are highlighted.
- Every section in fmhy.net's order with the same stars and icons. One row per wiki line: tap for all its mirrors and extra links, hold to open.
- The beginner's guide, notes, FAQ and FMHY's posts are readable in the app. Hidden base64 links are decoded for you.
</details>

<details>
<summary><b>Browser</b></summary>

- A small icon in the address bar shows whether the page is starred or listed on FMHY.
- Tabs in list or grid, private tabs kept apart, tab groups, picture-in-picture video, playback speed, double-tap to seek, smooth scrolling and pinch zoom.
- Wi-Fi only options for downloads, browsing and updates; "Report a problem" never sends anything by itself.
</details>

<details>
<summary><b>Privacy details</b></summary>

- The app reads the wiki straight from FMHY's own repository. Release APKs ship with a copy, so they work offline from the first launch and updates only download what changed.
- If you add a VirusTotal or Hybrid Analysis key, the app sends a link's web address when you tap "Check link first" and a file's SHA-256 for file checks. A file is only uploaded after you agree. Nothing is sent until you add a key.
</details>

## Install

Get the APK from [**Releases**](https://github.com/omeg4-dev/fmhy-android/releases/latest). A short setup on first start picks your look, how links open and which sections to follow. If you installed a build older than 0.4.0, uninstall it first: those used a different signing key.

## How it works

No server in between: the app downloads the wiki's markdown from [fmhy/edit](https://github.com/fmhy/edit) (jsDelivr as fallback), parses it on your phone and syncs only changed files. Change history comes from fmhy/edit and [fmhy-tracker](https://fmhy-tracker.pages.dev/); filter lists from uBlock Origin and EasyList; link checks use the same lists as [FMHY SafeGuard](https://github.com/fmhy/FMHY-SafeGuard). Builds made straight from this repo download the wiki on first start.

## Building

```bash
./gradlew :app:assembleRelease      # unsigned unless a release keystore is configured
./gradlew :core:data:test :app:testDebugUnitTest
```

`app/` Android app (Kotlin, Compose) · `core/data/` data layer (parser, SQLite, sync, search, ad-block) · `schema/` · `pipeline/` Python reference parser for parity tests · `tools/` icon and store-frame scripts. Third-party assets: `THIRD_PARTY.md`; architecture notes: `STATUS.md`.

## Not affiliated with FMHY

A fan project, not made by the FMHY team. Which sites get listed is up to them: suggestions go to [fmhy.net](https://fmhy.net/other/contributing), app problems to the [issue tracker](https://github.com/omeg4-dev/fmhy-android/issues). License: GPL-3.0-or-later.

Built with the help of AI coding assistants: I decide what the app does and review the changes, but a large part of the code was written by AI. If you find a bug, please open an issue.

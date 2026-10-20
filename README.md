# FMHY for Android

An unofficial Android app for [FMHY](https://fmhy.net) (FreeMediaHeckYeah), the big community-run
list of free stuff on the internet: streaming, downloading, adblocking, reading, software and a lot more.

fmhy.net works on a phone, but it's a big website. This app puts the same list into a native app
that's quick to scroll and search and keeps working without a connection.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="Home screen">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="A section with stars and icons">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="Details for one line of the wiki">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="200" alt="Search">
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="200" alt="Outline of the Non-English section">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="200" alt="Settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="200" alt="Icon legend">
</p>

## Download

Grab the APK from [Releases](https://github.com/omeg4-dev/fmhy-android/releases/latest). Android 8.0 or newer.

If you installed a build older than 0.4.0, uninstall it first: those were signed with a different key.

## Why an app?

The wiki works fine in a browser. The app is for the things a browser can't do because it doesn't
know FMHY:

- Follow a section or category and get one notification when sites are added or starred there.
- Your saved sites are watched: you hear about it when one is starred, removed or moves, and removed
  ones show replacements from the same category.
- What's new: every addition, star and removal from the last month, by day and section.
- A built-in browser that blocks ads with uBlock Origin's own filter lists and FMHY's unsafe-sites
  list, stops pop-ups and forced redirects, and shows where a site is listed on FMHY.
- When a site is down: one tap to a mirror, the Wayback Machine or an alternative from the same category.
- Check any link against FMHY SafeGuard's lists, from any app, through the share menu.
- My FMHY: sort saved sites into folders and share them as a list.

## What it does

- Every section in the same order as fmhy.net, with the same stars and icons.
- One row per line of the wiki. Tap a row to see every site on that line with its mirrors and extra
  links; hold it to open the site.
- Search across all ~21,000 sites, with filters for section, starred sites and more.
- Bookmarks that stay put when the wiki changes.
- Links that FMHY hides behind base64 are decoded for you.
- A warning before you open a site that's on FMHY's unsafe list.
- Light and dark theme, pure black, Material You or your own accent colour, adjustable text size.
- Notes, the beginner's guide, FAQ and FMHY's posts readable in the app.
- A first-start setup, a home-screen widget, launcher shortcuts and three app icons.

## How it works

There's no server of ours. The app downloads the wiki's markdown straight from
[fmhy/edit](https://github.com/fmhy/edit) (jsDelivr as a fallback) and parses it on the phone.
When you open the app it checks which files changed and only downloads those. Everything is
stored locally, so after the first sync browsing and search work offline. No account, no ads, no tracking.

Change history comes from fmhy/edit itself and, for the last month, from
[fmhy-tracker](https://fmhy-tracker.pages.dev/). Filter lists for the browser are downloaded from
uBlock Origin's and EasyList's own servers; link verdicts use the same lists as
[FMHY SafeGuard](https://github.com/fmhy/FMHY-SafeGuard).

The APKs on the Releases page come with a copy of the wiki built in, so they work offline from the
first launch. Builds made straight from this repo don't, and download the wiki the first time they start.

## Building

```bash
./gradlew :app:assembleRelease      # unsigned unless a release keystore is configured
./gradlew :core:data:test :app:testDebugUnitTest
```

- `app/`: the Android app (Kotlin, Jetpack Compose)
- `core/data/`: pure-JVM data layer (Kotlin parser, SQLite, sync, search)
- `schema/`: dataset schema
- `pipeline/`: Python reference parser, only used for parity tests
- `tools/`: icon conversion (`svg2vd.py`); third-party assets are listed in `THIRD_PARTY.md`

More detail on the architecture and build is in `STATUS.md`.

## Not affiliated with FMHY

This is a fan project, not made by the FMHY team. Which sites get listed is up to them: suggestions
for the wiki go to [fmhy.net](https://fmhy.net/other/contributing), problems with the app go to the
[issue tracker](https://github.com/omeg4-dev/fmhy-android/issues).

License: GPL-3.0-or-later.

# FMHY for Android

Native, offline-first Android client for [FMHY](https://fmhy.net): fast search, the wiki's own
icons and structure, local bookmarks and automatic base64 link decoding. Unofficial.

No server of ours: the app syncs straight from [fmhy/edit](https://github.com/fmhy/edit) and parses
the Markdown on the device. A snapshot ships in the APK, so it works offline from the first launch.

**Download:** the APK is on [Releases](https://github.com/omeg4-dev/fmhy-android/releases/latest)
(Android 8.0+).

- `app/`: Android app (Kotlin, Jetpack Compose)
- `core/data/`: pure-JVM data layer (Kotlin parser, SQLite, sync, search)
- `schema/`: dataset schema
- `pipeline/`: Python reference parser, used only for parity tests
- `tools/`: icon conversion (`svg2vd.py`); third-party assets are listed in `THIRD_PARTY.md`

Build with `./gw` (see `STATUS.md`). License: GPL-3.0-or-later.

# Batch 03 — Pastebin decoding and search glitch (BUG-10, BUG-11)

## Session prompt

```text
1.0.0 batch 03 (BUG-10, BUG-11): Pastebin decoding and search glitch.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-03-paste-decoding-search.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
```

## Goal

Hidden pastebin links retry quietly, show "Decoding hidden link…" and never open or copy the undecoded paste silently. The search "Categories" block always matches its count and never empties out while typing.

## Entries

### BUG-10: Hidden pastebin links sometimes open undecoded on the first try

**Problem:** Some FMHY links are hidden on a pastebin page whose text is encoded, and the app decodes them by itself. On the first attempt this sometimes fails, and the app opens the undecoded pastebin page instead of the real site. On the second or third attempt it works. Likely cause to check: if loading the paste fails or takes too long once (slow first connection, a short block by pastebin), the app gives up at once and quietly opens the paste itself. It does not try again and does not say that decoding failed.

**Desired behavior:**

- The app tries again quietly a couple of times, with a short wait in between, before it gives up.
- While it decodes, a short message says so ("Decoding hidden link…"). It stays until decoding is finished or has failed, so the user knows something is happening.
- If decoding really fails, the app does not open the undecoded paste silently. It says "Could not decode this link" with the buttons "Try again" and "Open the paste anyway".
- The same applies to Copy and Share from the entry menu: they never copy or share the undecoded paste address without saying so.
- Once a link has been decoded, the result is remembered, as it is today, so it opens instantly next time and offline.

**Things to check when implementing:**

- The first attempt after a fresh install, after the app was closed for a long time, and on a slow connection.
- No connection at all, and pastebin being down or blocking the request.
- Pastes that hide several links (the chooser), and links encoded several times.
- The link checker and the browser address bar, if they decode pastebin links too.

### BUG-11: Category results in search sometimes disappear

**Problem:** While typing a search, the "Categories" part of the results is sometimes empty, but "View all 15 categories" is still shown under it (Screenshot_20261024-143808; a moment earlier, Screenshot_20261024-143805 shows the same search with three categories). Seen once so far.

**Desired behavior:**

- The category results always match the count shown under them. If there are none, the "Categories" heading and the "View all" button are hidden.
- The results do not flicker or empty out while the user is typing or scrolling.

**Things to check when implementing:** typing fast, deleting letters, scrolling the results while they update, turning "Fuzzy" on and off, and the filters.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-25 | The failing pastebin decoding is handled as a bug | BUG-10 |

## Depends on / affects

- None. BUG-10's link checker and address-bar cases: no `PasteResolver` use was found there; confirm and note it in the report.

## Where to start in the code

- `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/link/PasteLinks.kt` — `PasteLinks.rawUrl` (~L19), `decode` (~L23), `PasteResolver.resolve` (~L61, returns null on any failure, no retry)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/LinkOpener.kt` — paste branch (~L99-109, falls back to `openChecked(url)`), `pasteResolverFor` (~L282)
- Copy/Share callers that fall back to the raw URL: `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRowActions.kt` (~L464 `resolvedLinkText`), `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRow.kt` (~L407), `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeScreen.kt` (~L697, ~L706), `app/src/main/java/io/github/omeg4dev/fmhy/ui/whatsnew/WhatsNewScreen.kt` (~L438, ~L447)
- `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/search/SearchEngine.kt` — `searchCategories` (~L142), `categoryIndexFor` (~L101), caller (~L83)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchViewModel.kt` (~L61, ~L202) and `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` — "Categories" header (~L479-486), `shownCategories` (~L493), "View all" (~L508-523), `categoriesExpanded` (~L169)

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.search.SearchViewModelTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.link.PasteLinksTest' --tests 'io.github.omeg4dev.fmhy.data.search.SearchCategoryTest' 2>&1 | tail -40
```

## Done when

- [ ] BUG-10: The app tries again quietly a couple of times, with a short wait in between, before it gives up.
- [ ] BUG-10: While it decodes, a short message says so ("Decoding hidden link…").
- [ ] BUG-10: If decoding really fails, the app does not open the undecoded paste silently.
- [ ] BUG-10: The same applies to Copy and Share from the entry menu: they never copy or share the undecoded paste address without saying so.
- [ ] BUG-10: Once a link has been decoded, the result is remembered, as it is today, so it opens instantly next time and offline.
- [ ] BUG-11: The category results always match the count shown under them.
- [ ] BUG-11: The results do not flicker or empty out while the user is typing or scrolling.

## Out of scope

- Other search changes (HOME-03, batch 16).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143805.png`
- `Screenshot_20261024-143808.png`

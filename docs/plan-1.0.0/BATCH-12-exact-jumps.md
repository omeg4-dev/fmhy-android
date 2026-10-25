# Batch 12 — Exact jumps and highlight (BUG-08)

## Session prompt

```text
1.0.0 batch 12 (BUG-08): Exact jumps and highlight.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-12-exact-jumps.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Every jump to an entry or section lands on the right subsection page with the target in the upper part of the screen, from every entry point and in every layout. The target gets a strong, pulsing highlight with an outline, and missing targets say so.

## Entries

### BUG-08: Section links and "Show in section" land in the wrong place, and the target is hard to see

**Problem:** Section links and the "Show in section" button often do not move the user to the right place in the lists. The list ends up somewhere else in the section, at the top, or on another subsection page. When it does land in the right place, the entry is hard to spot, because the highlight is weak. Likely causes to check: entries have different heights (long text, many icons, text size, website style), so the scroll position is worked out wrongly; the scroll happens before the list has finished loading or measuring; the wrong subsection page is selected; or the layout changes after the scroll (for example icons loading) and pushes the target away.

**Desired behavior:**

- Every jump ends in the right place: the correct section, the correct subsection page (the current subsection shown at the top matches, see UI-04), and the target entry visible in the upper part of the screen. It is never hidden behind the top bar or at the very bottom.
- It works from every place that can jump to an entry or section: the "Show in section" button, section links in the wiki text, the link checker, search results, "What's new", saved sites, and the FMHY status button in the browser (see BRW-05).
- It works when the app was closed and is just starting, and when it is already open and the user is somewhere else.
- It works with every text size, with website style on and off (see UI-01), after rotating the phone, and while the list is still loading. The jump waits until the content is ready and then lands exactly. The position does not jump again when icons or images finish loading.
- A link to a whole section (not a single entry) lands at the start of that section, with the section heading visible.
- If the target cannot be found any more (it was moved or removed since the last sync), the app says so in plain words, for example "This entry was moved or removed", and brings the user to its section instead of silently landing somewhere else. Saved sites with a known replacement offer it, as the app already does for moved and removed saved sites.
- The target is highlighted clearly: a strong tint in the accent color with a visible outline or side bar, shown as soon as the jump ends, with a short pulse to catch the eye. It stays until the user touches or scrolls the list, then fades out gently. The outline means it never depends on color alone. It works in light, dark and pure black and in both list styles.
- Going back after "Show in section" returns the user to where they came from.

**Things to check when implementing:**

- Entries at the top, in the middle and at the very end of a long section, and the first and last entry of each subsection page.
- Very long entries, entries with many icons, the largest text size, website style on and off.
- Starting cold from a shared link or a notification, and a jump while another jump is still running.
- Tapping "Show in section" twice quickly.
- Contrast of the highlight in light, dark and pure black, and with different accent colors.
- Test it in the bug hunt (see QA-01) on small and tall phones and on a tablet.

## Depends on / affects

- BRW-05 (batch 02) added the browser's "Show in section" entry point.
- UI-04 (batch 13) adds the current-subsection line that must match after a jump.
- UI-01 (batch 15) adds website style; keep the jump logic independent of row height.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionScreen.kt` — `SectionScreen` (~L86), `highlightedEntryId` (~L106), target scroll effect (~L189-226, `scrollToItem(entryIdx)` ~L198), highlight tint (~L619-624)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/CategoryScreen.kt` — same pattern: effect (~L175-210), highlight (~L493-498)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionItems.kt` — heading `isHighlighted` (~L115, ~L127)
- `app/src/main/java/io/github/omeg4dev/fmhy/NavigationKeys.kt` — `SectionNavKey(sectionId, anchor, focusEntryId)` (~L10) and the key with `focusHeadingId`/`focusEntryId` (~L16-17)
- Callers: `app/src/main/java/io/github/omeg4dev/fmhy/Navigation.kt` (~L244), `app/src/main/java/io/github/omeg4dev/fmhy/ui/search/SearchScreen.kt` (~L586), `app/src/main/java/io/github/omeg4dev/fmhy/ui/whatsnew/WhatsNewScreen.kt` (~L425), `app/src/main/java/io/github/omeg4dev/fmhy/ui/check/CheckLinkScreen.kt` (~L266), `app/src/main/java/io/github/omeg4dev/fmhy/ui/saved/SavedScreen.kt` (~L120-128), `app/src/main/java/io/github/omeg4dev/fmhy/ui/home/HomeScreen.kt` (~L391, ~L689)
- Wiki section links: `app/src/main/java/io/github/omeg4dev/fmhy/nav/FmhyLinkResolver.kt` `resolveFmhyUri` (~L14), `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/link/CrossLinks.kt` (~L12); saved-site moved/removed texts: `app/src/main/java/io/github/omeg4dev/fmhy/ui/saved/SavedScreen.kt` (~L988-990)
- Note: All scroll logic lives in the composables today (`SectionViewModel` has none).

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.nav.FmhyLinkResolverTest' --tests 'io.github.omeg4dev.fmhy.nav.AppNavStateTest' --tests 'io.github.omeg4dev.fmhy.ui.section.SectionStructureTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.link.CrossLinksTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.ui.section.ScrollTargetTest` (:app) for a pure target-index resolution extracted from the scroll effect (page, index, found/missing)

## Done when

- [ ] BUG-08: Every jump ends in the right place: the correct section, the correct subsection page (the current subsection shown at the top matches, see UI-04), and the target entry visible in the upper part of the screen.
- [ ] BUG-08: It works from every place that can jump to an entry or section: the "Show in section" button, section links in the wiki text, the link checker, search results, "What's new", saved sites, and the FMHY status button in the browser (see BRW-05).
- [ ] BUG-08: It works when the app was closed and is just starting, and when it is already open and the user is somewhere else.
- [ ] BUG-08: It works with every text size, with website style on and off (see UI-01), after rotating the phone, and while the list is still loading.
- [ ] BUG-08: A link to a whole section (not a single entry) lands at the start of that section, with the section heading visible.
- [ ] BUG-08: If the target cannot be found any more (it was moved or removed since the last sync), the app says so in plain words, for example "This entry was moved or removed", and brings the user to its section instead of silently landing somewhere else.
- [ ] BUG-08: The target is highlighted clearly: a strong tint in the accent color with a visible outline or side bar, shown as soon as the jump ends, with a short pulse to catch the eye.
- [ ] BUG-08: Going back after "Show in section" returns the user to where they came from.

## Out of scope

- The current-subsection line (UI-04, batch 13).
- Settings search highlight (SET-01, batch 23) reuses this highlight later.

## Screenshots

None named in the plan for this batch.

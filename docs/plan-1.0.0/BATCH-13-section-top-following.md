# Batch 13 — Section page top and following (UI-04, UI-05)

## Session prompt

```text
1.0.0 batch 13 (UI-04, UI-05): Section page top and following.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-13-section-top-following.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

In app style, the subsection bubble row becomes a current-subsection line with a drop-down and a subtle bell. Following gets one clear model: bell, hold menu (Follow / Pin to Home), entry-menu row, a "Following" tree screen, and main sections that follow all their subsections.

## Entries

### UI-04: Cleaner top of section pages (no more bubble row)

**Problem:** At the top of a section page (Screenshot_20261024-143417, -143439, -143452, -143457) there is a row of outlined bubbles, one per subsection, under the title. It scrolls sideways, so most subsections are hidden off the screen, and the selected bubble is hard to tell apart. Together with the three buttons in the title bar and the large subsection card under it, the top of the page looks messy and does not match the calm look of the rest of the app. Following a subsection from there is also hidden (see UI-05).

**Desired behavior in app style (the current, phone-like look):**

- The row of bubbles is removed. The top of the page takes one line less.
- The title stays as it is now (the main section, for example "Adblocking / Privacy"). The back arrow and the buttons in the title bar (filter, outline, three-dot menu) stay where they are now.
- Under the title, a smaller line shows the current subsection, for example "Adblocking", with a small arrow pointing down. It changes as the user scrolls through the section or swipes to another page (UI-02), so it always says where the user is.
- Tapping that line opens a drop-down list of all subsections of the section, each with its number of sites. The current one is marked. Picking one jumps there (and lands exactly, see BUG-08).
- Holding a subsection in the drop-down opens the follow and pin options (UI-05).
- A small, subtle bell sits next to the subsection line (UI-05).
- The subsection card in the list ("Adblocking · 55 sites") stays at the start of each subsection.

**Desired behavior in website style (fmhy.net look, see UI-01):**

- Under the title bar there is a slim row like on fmhy.net on a phone: "☰ Menu" on the left and "On this page ›" on the right, separated from the list by a thin line. No bubbles, no cards.
- "Menu" opens a panel from the left with all main sections of the wiki, like the website's sidebar. The current section is highlighted. Picking one opens it.
- "On this page" opens a panel with the headings of the current section (subsections and the headings inside them), indented like the website's outline. The current one is highlighted. Picking one jumps there.
- The current subsection's name is shown in the row next to "On this page", in smaller text, so the user always knows where they are. The bell from UI-05 sits next to it.
- In website style, the filter and outline buttons move from the title bar into the three-dot menu, because "On this page" replaces the outline. The title bar then only has the back arrow, the title and the three-dot menu, like the website's header.

**Decided details**

- The drop-down in app style lists only the subsections, not the headings inside them. The outline button keeps showing the full outline.

**Things to check when implementing:**

- Sections with very many subsections (the drop-down scrolls), very long subsection names, the largest text size.
- "Continuous" and "Categories" section layouts (today under Settings › Home & lists, see SET-01).
- Jumps from "Show in section", search and links (BUG-08): the subsection line must show the right subsection right after the jump.
- Light, dark and pure black, both styles.

### UI-05: Easier following of subsections

**Problem:** Following works differently in each place, and following a subsection (not a main section) is hard to find:

- The three-dot menu on a section page has "Follow this section", which follows the whole main section.
- The entry menu (hold an entry) has a row "Following Adblocking" at the bottom, which follows the entry's subsection (Screenshot_20261024-143553).
- Holding a subsection bubble opens the pin dialog "Remove this category from Home pins?" with the buttons "Unfollow", "Cancel" and "Unpin" mixed together (Screenshot_20261024-143711). When Personal Home is off, holding a bubble follows or unfollows silently, with only a short message.
- The list of followed sections only exists in settings, as a flat list with a remove button (Screenshot_20261024-143923).
- "New sites in followed categories" is off there, so following has no visible effect for many users.

**Desired behavior:**

- **Bell next to the current subsection** (UI-04): in app style next to the small subsection line under the title, in website style next to the subsection name in the "On this page" row. It is small and subtle (outlined, in a quiet color) so it does not draw attention, and becomes a filled bell in the accent color when the subsection is followed. Tapping it follows or unfollows the current subsection, with a short message and "Undo".
- **Hold a subsection** in the drop-down list (app style) or the "On this page" panel (website style): a small menu with two clearly separate actions, "Follow" or "Unfollow", and "Pin to Home" or "Unpin". This replaces the mixed pin dialog. Holding never follows or unfollows anything without showing the menu.
- **Entry menu:** the follow row is named after the subsection, for example "Follow Adblocking" or "Following Adblocking", with the same bell icon.
- The three-dot menu on a section page keeps "Follow this section" for the whole main section.
- **Following screen:** a new screen "Following", reachable from settings (see SET-01) and from the "Following" filter in What's new. It shows the full tree: every main section, unfoldable to its subsections, each with a switch, and at the top how many are followed.
- **Main sections and subsections:** following a main section follows all its subsections. Each subsection can then be switched off on its own. A main section with some subsections switched off shows a "partly followed" state. Subsections that FMHY adds later to a followed main section are followed automatically.
- The first-run step "Follow sections" uses the same tree and the same rule.
- The first time the user follows something while notifications for followed sections are off, a one-time prompt asks whether to turn them on ("Get notified about new sites in sections you follow?" with "Turn on" and "Not now"). Followed sections always show up in What's new's "Following" filter, with or without notifications.
- The wording is the same everywhere: "section" for main sections and "subsection" for the parts inside them (today the notification setting says "categories").

**Things to check when implementing:**

- Existing follows keep working after the update, including follows of whole main sections.
- Following and unfollowing quickly, Undo, and following from the entry menu while the subsection's bell is visible (both must update at once).
- Screen readers announce the bell as "Follow Adblocking" or "Unfollow Adblocking".
- Notifications for a main section with some subsections switched off.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-24 | App style: the bubble row is replaced by a drop-down, opened from a small line under the title that shows the current subsection. The title bar buttons stay where they are | UI-04 |
| 2026-10-24 | Website style: a "Menu" and "On this page" row like fmhy.net | UI-04 |
| 2026-10-24 | Follow from a subtle bell next to the current subsection, by holding a subsection, from a "Following" screen with the full tree, and from the entry menu | UI-05 |
| 2026-10-24 | Following a main section follows all its subsections, each can be switched off. The same applies to the first-run step | UI-05 |
| 2026-10-25 | The app-style drop-down lists only subsections; the outline button keeps the full outline | UI-04 |

## Depends on / affects

- BUG-08 (batch 12) provides exact landing; picking a subsection uses it.
- UI-01 (batch 15) builds website style and with it the website-style half of UI-04 and UI-05 ("Menu" / "On this page" row, bell there).
- UI-02 (batch 14) adds swiping between subsection pages; the line must already follow the visible page.
- HOME-02 (batch 16) reuses the hold menu (Unpin, Follow/Unfollow) for Home tiles.
- SET-01 (batch 23) places "Following" under Updates; link it from the notifications page for now.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionItems.kt` — `CategoryChipRail` (~L401): the bubble row
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionScreen.kt` — chip rail use (~L375-410, `onSelectChip` ~L395, `onLongClickChip` ~L409), pin dialog "Remove this category from Home pins?" (~L732)
- Follow UI: `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/FollowState.kt` (~L3), `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRow.kt` (~L123-165, undo "Unfollowed …"), `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryDetailSheet.kt` (~L352-364, "Following …" row)
- Follow data: `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/user/UserModels.kt` `Follow` (~L36), `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/user/UserStore.kt` `follows` (~L424), `follow` (~L442), `unfollow` (~L458), `restoreFollow` (~L467); empty `headingId` = whole section
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/NotificationsPage.kt` — "New sites in followed categories" (~L115), followed list (~L170-185)
- First-run step: `app/src/main/java/io/github/omeg4dev/fmhy/ui/onboarding/OnboardingScreen.kt` `FollowPageContent` (~L561), `app/src/main/java/io/github/omeg4dev/fmhy/ui/onboarding/OnboardingModel.kt` `toggleSectionFollowed` (~L181)
- Note: A follow is `(sectionId, headingId)`; there is no tree or "partly followed" model yet. Existing follows must keep working (see UI-05 "Things to check").

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.work.UpdateWorkerLogicTest' --tests 'io.github.omeg4dev.fmhy.ui.onboarding.OnboardingModelTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.user.UserStoreTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.ui.common.FollowTreeTest` (:app): main section vs subsection, partly followed, new subsections of a followed section

## Done when

- [ ] UI-04: The row of bubbles is removed.
- [ ] UI-04: The title stays as it is now (the main section, for example "Adblocking / Privacy").
- [ ] UI-04: Under the title, a smaller line shows the current subsection, for example "Adblocking", with a small arrow pointing down.
- [ ] UI-04: Tapping that line opens a drop-down list of all subsections of the section, each with its number of sites.
- [ ] UI-04: Holding a subsection in the drop-down opens the follow and pin options (UI-05).
- [ ] UI-04: A small, subtle bell sits next to the subsection line (UI-05).
- [ ] UI-04: The subsection card in the list ("Adblocking · 55 sites") stays at the start of each subsection.
- [ ] UI-05: **Bell next to the current subsection** (UI-04): in app style next to the small subsection line under the title, in website style next to the subsection name in the "On this page" row.
- [ ] UI-05: **Hold a subsection** in the drop-down list (app style) or the "On this page" panel (website style): a small menu with two clearly separate actions, "Follow" or "Unfollow", and "Pin to Home" or "Unpin".
- [ ] UI-05: **Entry menu:** the follow row is named after the subsection, for example "Follow Adblocking" or "Following Adblocking", with the same bell icon.
- [ ] UI-05: The three-dot menu on a section page keeps "Follow this section" for the whole main section.
- [ ] UI-05: **Following screen:** a new screen "Following", reachable from settings (see SET-01) and from the "Following" filter in What's new.
- [ ] UI-05: **Main sections and subsections:** following a main section follows all its subsections.
- [ ] UI-05: The first-run step "Follow sections" uses the same tree and the same rule.
- [ ] UI-05: The first time the user follows something while notifications for followed sections are off, a one-time prompt asks whether to turn them on ("Get notified about new sites in sections you follow?" with "Turn on" and "Not now").
- [ ] UI-05: The wording is the same everywhere: "section" for main sections and "subsection" for the parts inside them (today the notification setting says "categories").

## Out of scope

- Website-style row, panels and bell (batch 15).
- Swiping between pages (UI-02, batch 14).
- Home tile hold menu (HOME-02, batch 16).

## Screenshots

In `/home/omega/Documents/Obsidian/Weiters/FMHY APP/Images 24.10.2026/` (the user can attach them if needed):
- `Screenshot_20261024-143417.png`
- `Screenshot_20261024-143439.png`
- `Screenshot_20261024-143452.png`
- `Screenshot_20261024-143457.png`
- `Screenshot_20261024-143553.png`
- `Screenshot_20261024-143711.png`
- `Screenshot_20261024-143923.png`

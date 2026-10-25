# Batch 15 — Website style (UI-01)

## Session prompt

```text
1.0.0 batch 15 (UI-01): Website style.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-15-website-style.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Add the "Website style" switch: entries render like fmhy.net (bold link-coloured name, description on the same line, inline icons, tight spacing), with a side-by-side choice in the first-run appearance step. In website style, section pages get the "Menu" / "On this page" row from UI-04 and the bell from UI-05.

## Entries

### UI-01: "Website style" for lists

**Problem:** Feedback says the lists in the app look different from fmhy.net and are harder to scan. On the website, site names are bold and in link color and the description is plain text, so the eye finds the link at once. In the app, names and descriptions are close in weight and color, and changing the app's theme did not fix it.

**Desired behavior:**

- A new switch "Website style" in settings under Appearance. When on, entries look and feel like fmhy.net: a bullet-style list, the site name in bold and clear link color, and the description on the same line, separated by dashes and slashes as on the website. Links inside the description use the link color. The social icons sit inline and the star sits in front of the name. Spacing is tight, like the website.
- The same choice appears in the appearance step of the first-run setup, with a small side-by-side preview of both styles.
- It works with light, dark and pure black, and with the text size setting.
- The switch changes looks only. Tapping, holding, searching, stars and swiping work the same (see UI-02 and UI-03).
- New installs start with the current app style. In website style, links use the user's accent color so the app's themes keep working. The bold name is what makes names easy to find.
- The top of a section page also differs between the two styles: website style gets the "Menu" and "On this page" row of fmhy.net, app style gets the current subsection under the title (see UI-04).
- Website style is not built yet. Everything about it in this plan is a description of what to build.

**Things to check when implementing:**

- Very long entries and entries with many icons wrap cleanly.
- Contrast in light, dark and pure black.
- Entries still feel easy to hit with a thumb in the denser layout.

#### Carried over from UI-04 (UI-04 is owned by batch 13, which built the app-style half)

**Desired behavior in website style (fmhy.net look, see UI-01):**

- Under the title bar there is a slim row like on fmhy.net on a phone: "☰ Menu" on the left and "On this page ›" on the right, separated from the list by a thin line. No bubbles, no cards.
- "Menu" opens a panel from the left with all main sections of the wiki, like the website's sidebar. The current section is highlighted. Picking one opens it.
- "On this page" opens a panel with the headings of the current section (subsections and the headings inside them), indented like the website's outline. The current one is highlighted. Picking one jumps there.
- The current subsection's name is shown in the row next to "On this page", in smaller text, so the user always knows where they are. The bell from UI-05 sits next to it.
- In website style, the filter and outline buttons move from the title bar into the three-dot menu, because "On this page" replaces the outline. The title bar then only has the back arrow, the title and the three-dot menu, like the website's header.


#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | New installs keep the current app style by default                              | UI-01          |
| 2026-10-23 | Website style uses the user's accent color for links                            | UI-01          |
| 2026-10-24 | App style: the bubble row is replaced by a drop-down, opened from a small line under the title that shows the current subsection. The title bar buttons stay where they are | UI-04 |
| 2026-10-24 | Website style: a "Menu" and "On this page" row like fmhy.net | UI-04 |
| 2026-10-25 | The app-style drop-down lists only subsections; the outline button keeps the full outline | UI-04 |

## Depends on / affects

- UI-04/UI-05 (batch 13) built the app-style top, the hold menu and the bell; reuse them in the website-style row and the "On this page" panel.
- BUG-08 (batch 12): jumps must stay exact with website style on.
- ONB-01 (batch 17) restyles the first-run preview with FMHY artwork.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/EntryRow.kt` — `EntryRow` (~L86), rendering (~L177-200), `parseHighlight` (~L60)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/common/MarkdownInline.kt` — `parse` (~L29), `parseRange` (~L46) for descriptions
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/section/SectionScreen.kt` — `TopAppBar` (~L266), legend/options icon (~L289), outline icon (~L294)
- `app/src/main/java/io/github/omeg4dev/fmhy/ui/onboarding/OnboardingPreviews.kt` — `LookPreview` (~L82), `MockPageContent` (~L704)
- Pref + switch: `app/src/main/java/io/github/omeg4dev/fmhy/data/AppearancePrefs.kt` `Appearance` (~L7), `app/src/main/java/io/github/omeg4dev/fmhy/data/SettingsRepository.kt`, `app/src/main/java/io/github/omeg4dev/fmhy/ui/settings/AppearancePage.kt`
- Note: UI-05 in website style: the bell sits next to the subsection name in the "On this page" row, and holding a subsection in the "On this page" panel opens the same Follow/Pin menu as in app style.

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.ui.common.MarkdownInlineTest' --tests 'io.github.omeg4dev.fmhy.data.SettingsRepositoryTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.ui.common.WebsiteStyleFormatTest` (:app) for any pure text formatting (separators, inline icons)

## Done when

- [ ] UI-01: A new switch "Website style" in settings under Appearance.
- [ ] UI-01: The same choice appears in the appearance step of the first-run setup, with a small side-by-side preview of both styles.
- [ ] UI-01: It works with light, dark and pure black, and with the text size setting.
- [ ] UI-01: The switch changes looks only.
- [ ] UI-01: New installs start with the current app style.
- [ ] UI-01: The top of a section page also differs between the two styles: website style gets the "Menu" and "On this page" row of fmhy.net, app style gets the current subsection under the title (see UI-04).
- [ ] UI-01: Website style is not built yet.
- [ ] UI-04: Under the title bar there is a slim row like on fmhy.net on a phone: "☰ Menu" on the left and "On this page ›" on the right, separated from the list by a thin line.
- [ ] UI-04: "Menu" opens a panel from the left with all main sections of the wiki, like the website's sidebar.
- [ ] UI-04: "On this page" opens a panel with the headings of the current section (subsections and the headings inside them), indented like the website's outline.
- [ ] UI-04: The current subsection's name is shown in the row next to "On this page", in smaller text, so the user always knows where they are.
- [ ] UI-04: In website style, the filter and outline buttons move from the title bar into the three-dot menu, because "On this page" replaces the outline.

## Out of scope

- FMHY artwork in the setup (ONB-01, batch 17).

## Screenshots

None named in the plan for this batch.

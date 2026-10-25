# Batch 22 — Downloads tied to FMHY (BRW-06)

## Session prompt

```text
1.0.0 batch 22 (BRW-06): Downloads tied to FMHY.
Read CLAUDE.md, docs/plan-1.0.0/COMMON.md and docs/plan-1.0.0/BATCH-22-downloads-fmhy.md, nothing else from docs/plan-1.0.0/.
Do every entry in that batch file within its scope, then finish as COMMON.md says.
Plan first and wait for my approval before you change any code.
```

## Goal

Show FMHY's status of the page and the file's real source on the download confirmation and on every entry, check the whole redirect path against the unsafe list and lookalikes, and offer the security checks according to the source. Everything is checked on the phone.

## Entries

### BRW-06: Download Manager tied to FMHY

**Goal:** Connect the Download Manager (BRW-02) to what FMHY knows about the place a file comes from. A normal browser's download list does not have this, and it gives people a reason to download through this app. Everything FMHY-related is checked on the phone, with the wiki data and the unsafe-sites list the app already has, so no web address is sent anywhere for it. This fits the no-tracking promise.

**Depends on:** BRW-02 (the Download Manager itself), and BRW-05 and BUG-08 for "Show in section".

**Source status on the download confirmation**

- The confirmation from BRW-02 gets one line about the source site, with the same wording and icons as the link checker and the FMHY status button in the address bar:
    - Starred on FMHY.
    - Listed on FMHY.
    - Not listed on FMHY (neutral wording: this is neither a verdict nor a warning).
    - Listed as unsafe by FMHY (red, shown as the single combined warning from BUG-04).
    - Known fake lookalike of a listed site (red).
- Two addresses are shown when they differ: the page the user is on and the site the file really comes from, because file hosts often differ from the page. If the file comes from another site than the page, the line says so, and the status is shown for the file's site as well as for the page.
- Next to a listed or starred source there is a "Show in section" link (see BRW-05 and BUG-08).
- Pasted links get the same line.

**Status on every entry in the downloads sheet**

- Each entry in "Active" and "Finished" keeps a small status badge next to the source name. Tapping it opens the same information as the FMHY status button, with "Show in section".
- The badge shows the status at the time of the download, together with the date of the wiki data it was based on.

**Checks during the download**

- The unsafe-site warning from BRW-02 applies to the whole path of the download, not only the first address. If a download is redirected to a site on the unsafe list or to a known lookalike, it stops and shows one combined warning (see BUG-04), and the user can continue or cancel.
- Risky file types from a source that is not listed on FMHY get a slightly firmer confirmation than the same file from a starred source.

**Offering the security checks**

- The VirusTotal and Hybrid Analysis checks from BRW-02 are offered according to the source. For files from sites that are not listed on FMHY, or for risky file types, "Check link first" is highlighted on the confirmation and "Check file" is suggested after the download. For starred sources they stay available but quiet.
- It is only an offer. Nothing is checked or sent without the user's tap (or the separate automatic-check setting from BRW-02).
- FMHY status and scan results are shown side by side but kept separate on the entry, for example "Starred on FMHY" and "VirusTotal: no flags, 23 Oct". A short reminder states that the FMHY status only says where the site is listed and does not mean that a file is safe.

**Decided details**

- "Not listed on FMHY" is shown in neutral wording, with no warning color.
- A mirror of a listed site counts as listed, and the line says that it is a mirror.
- A switch in settings turns off the FMHY status lines in the Download Manager. It is on by default.

**Out of scope for now**

- Judging the content of a file from FMHY data.
- Blocking downloads from sites that are not listed.
- Scanning files automatically without the user's choice.

**Things to check when implementing:**

- Sites and file hosts with several domains, subdomains and mirrors, and how they are matched to the wiki.
- Address shorteners and chains of redirects.
- The wiki data not synced yet, or the phone offline: the status uses the last data and shows its date.
- A site that was listed when the download started and was removed or marked unsafe later.
- False alarms from the lookalike check.
- Private tabs: the status is shown, but nothing is kept after the app closes (as in BRW-02).
- The status line on small screens and with the largest text size, in the confirmation as well as in the sheet.

#### Decisions made (rows naming these IDs or the carried-over ones)

| Date       | Decision                                                                        | Applied in     |
| ---------- | ------------------------------------------------------------------------------- | -------------- |
| 2026-10-23 | The Download Manager is tied to FMHY (moved out of the parking lot) | BRW-06 |
| 2026-10-25 | "Not listed on FMHY" is neutral, mirrors count as listed, the FMHY status lines have a switch (on by default) | BRW-06 |

## Depends on / affects

- BRW-02 (batches 21a/21b) built the Download Manager and the checks.
- BRW-05 (batch 02) and BUG-08 (batch 12) provide "Show in section".
- BUG-04 (batch 05) provides the single combined warning.

## Where to start in the code

- `app/src/main/java/io/github/omeg4dev/fmhy/browser/SiteBarModel.kt` — `SiteBarModel.from` (~L24-25), `statusIconSpec` (~L115), `statusTitle` (~L126)
- `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/safety/SafeGuard.kt` — `SafetyStatus` (~L9), `Verdict` (~L21), `check` (~L35), `findLookalikes` (~L210), `normalizeUrl` (~L337), `urlMatchesListedResource` (~L379), `extractHostFromUrl` (~L458)
- `core/data/src/main/kotlin/io/github/omeg4dev/fmhy/data/safety/HostMatch.kt` — `HostMatch` (~L11) for mirrors
- Link checker wording: `app/src/main/java/io/github/omeg4dev/fmhy/ui/check/VerdictText.kt` `verdictTitle` (~L7), `verdictBody` (~L19); `app/src/main/java/io/github/omeg4dev/fmhy/ui/check/VerdictCard.kt` (~L68)
- The confirmation, sheet and entries from batch 21a

## Tests

```bash
./gw -q :app:testDebugUnitTest --tests 'io.github.omeg4dev.fmhy.browser.SiteBarModelTest' --tests 'io.github.omeg4dev.fmhy.ui.check.VerdictTextTest' 2>&1 | tail -40
./gw -q :core:data:test --tests 'io.github.omeg4dev.fmhy.data.safety.SafeGuardTest' --tests 'io.github.omeg4dev.fmhy.data.safety.SafeGuardMatchingTest' 2>&1 | tail -40
```

New tests:
- `io.github.omeg4dev.fmhy.browser.download.DownloadSourceStatusTest` (:app): page vs file host, mirrors, redirect chain, status date

## Done when

- [ ] BRW-06: The confirmation from BRW-02 gets one line about the source site, with the same wording and icons as the link checker and the FMHY status button in the address bar:
- [ ] BRW-06: Two addresses are shown when they differ: the page the user is on and the site the file really comes from, because file hosts often differ from the page.
- [ ] BRW-06: Next to a listed or starred source there is a "Show in section" link (see BRW-05 and BUG-08).
- [ ] BRW-06: Pasted links get the same line.
- [ ] BRW-06: Each entry in "Active" and "Finished" keeps a small status badge next to the source name.
- [ ] BRW-06: The badge shows the status at the time of the download, together with the date of the wiki data it was based on.
- [ ] BRW-06: The unsafe-site warning from BRW-02 applies to the whole path of the download, not only the first address.
- [ ] BRW-06: Risky file types from a source that is not listed on FMHY get a slightly firmer confirmation than the same file from a starred source.
- [ ] BRW-06: The VirusTotal and Hybrid Analysis checks from BRW-02 are offered according to the source.
- [ ] BRW-06: It is only an offer.
- [ ] BRW-06: FMHY status and scan results are shown side by side but kept separate on the entry, for example "Starred on FMHY" and "VirusTotal: no flags, 23 Oct".

## Out of scope

- Everything in BRW-06 "Out of scope for now".

## Screenshots

None named in the plan for this batch.

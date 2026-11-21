# Dataset contract (schema 5)

The pipeline publishes these files to the `data` branch (served by GitHub Pages under
`https://<owner>.github.io/fmhy-android/data/`). The app only ever reads them. Changing anything here
means bumping `schema` and handling both versions.

## `manifest.json`

```json
{
  "schema": 1,
  "version": 42,
  "created": 1792345678,
  "source_commit": "f479740d…",
  "entries": 21345,
  "full":   {"file": "full-42.sqlite.gz", "sha256": "…", "size": 3812345, "raw_size": 14123456},
  "deltas": [
    {"from": 41, "to": 42, "file": "delta-41-42.json", "sha256": "…", "size": 1234},
    {"from": 40, "to": 41, "file": "delta-40-41.json", "sha256": "…", "size": 987}
  ]
}
```

- `version` is a monotonic integer; a new version is published only when the content changed.
- `deltas` covers at most the last 50 steps, newest first. Each delta goes exactly one version forward.
- `sha256` is of the file exactly as stored, lowercase hex; `size` likewise.
- `full-*.sqlite.gz` is gzip (level 9, stdlib `gzip`) of a SQLite file created from `dataset.sql`.
  Deltas are plain UTF-8 JSON (compact separators); GitHub Pages gzips them in transit and OkHttp
  decompresses transparently, so the client hashes the plain JSON bytes. No zstd anywhere.

## `delta-<from>-<to>.json`

```json
{
  "schema": 1, "from": 41, "to": 42,
  "upsert": {
    "sections": [ {row}, … ], "headings": [ … ], "entries": [ … ], "notes": [ … ],
    "critical_notes": [ … ], "note_links": [ … ],
    "posts": [ … ], "unsafe_domains": [ … ], "site_lists": [ … ]
  },
  "delete": {
    "sections": ["id", …], "headings": ["id", …], "entries": ["id", …], "notes": ["id", …],
    "critical_notes": ["id", …], "note_links": [["entry_id", "note_id"], …],
    "posts": ["id", …], "unsafe_domains": ["domain", …], "site_lists": [["key", "list"], …]
  },
  "changes_append": [ {row}, … ],
  "changes_prune_before": 1758000000,
  "meta": {"version": "42", "created": "…", "source_commit": "…"}
}
```

- A `{row}` is an object with **every** column of that table from `dataset.sql`, keyed by column name
  (`entries` rows include `num`). Upsert = `INSERT … ON CONFLICT(<primary key>) DO UPDATE SET <all
  columns>` (`entries` conflict target is `id`; `num` never changes for an id; composite keys like
  `note_links` conflict target is `entry_id, note_id`; `site_lists` is `key, list`).
- Deletes are by primary key (`entries` by `id`; `critical_notes` by `id`; composite keys like `note_links`
  by `["entry_id", "note_id"]`). Empty lists may be omitted.
- The `critical_notes` table stores warnings treated as critical by the website (columns: `id`, `title`,
  `text_md`, `hash`). The `note_links` table maps entries to referenced note IDs on their line
  (columns: `entry_id`, `note_id`, `section_id`, `line`, `ord`).
- Apply order inside ONE transaction, with `PRAGMA foreign_keys = OFF`: deletes in the order
  notes, critical_notes, note_links, entries, headings, sections, posts, unsafe_domains, site_lists;
  then upserts in the order sections, headings, entries, notes, critical_notes, note_links, posts,
  unsafe_domains, site_lists; then `changes_append` (plain INSERT); then
  `DELETE FROM changes WHERE ts < changes_prune_before`; then every `meta` key with
  `INSERT OR REPLACE`. FTS tables update through the triggers.
- Correctness contract (tested in the pipeline): applying `delta-A-B` to `full-A` gives a database whose
  table contents (all non-FTS tables, rows sorted by primary key) equal those of `full-B`.

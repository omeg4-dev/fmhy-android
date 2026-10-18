# Dataset contract (schema 1)

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
  "full":   {"file": "full-42.sqlite.zst", "sha256": "…", "size": 3812345, "raw_size": 14123456},
  "deltas": [
    {"from": 41, "to": 42, "file": "delta-41-42.json.zst", "sha256": "…", "size": 1234},
    {"from": 40, "to": 41, "file": "delta-40-41.json.zst", "sha256": "…", "size": 987}
  ]
}
```

- `version` is a monotonic integer; a new version is published only when the content changed.
- `deltas` covers at most the last 50 steps, newest first. Each delta goes exactly one version forward.
- `sha256` is of the file as downloaded (the `.zst`), lowercase hex. `size` likewise.
- Files are zstd-compressed (level 19). `full-*.sqlite.zst` decompresses to a SQLite file created from
  `dataset.sql`.

## `delta-<from>-<to>.json.zst`

```json
{
  "schema": 1, "from": 41, "to": 42,
  "upsert": {
    "sections": [ {row}, … ], "headings": [ … ], "entries": [ … ], "notes": [ … ],
    "posts": [ … ], "unsafe_domains": [ … ]
  },
  "delete": {
    "sections": ["id", …], "headings": ["id", …], "entries": ["id", …], "notes": ["id", …],
    "posts": ["id", …], "unsafe_domains": ["domain", …]
  },
  "changes_append": [ {row}, … ],
  "changes_prune_before": 1758000000,
  "meta": {"version": "42", "created": "…", "source_commit": "…"}
}
```

- A `{row}` is an object with **every** column of that table from `dataset.sql`, keyed by column name
  (`entries` rows include `num`). Upsert = `INSERT … ON CONFLICT(<primary key>) DO UPDATE SET <all
  columns>` (`entries` conflict target is `id`; `num` never changes for an id).
- Deletes are by primary key (`entries` by `id`). Empty lists may be omitted.
- Apply order inside ONE transaction, with `PRAGMA foreign_keys = OFF`: deletes in the order
  notes, entries, headings, sections, posts, unsafe_domains; then upserts in the order sections,
  headings, entries, notes, posts, unsafe_domains; then `changes_append` (plain INSERT); then
  `DELETE FROM changes WHERE ts < changes_prune_before`; then every `meta` key with
  `INSERT OR REPLACE`. FTS tables update through the triggers.
- Correctness contract (tested in the pipeline): applying `delta-A-B` to `full-A` gives a database whose
  table contents (all non-FTS tables, rows sorted by primary key) equal those of `full-B`.

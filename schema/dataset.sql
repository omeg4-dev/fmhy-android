-- FMHY dataset, schema 1. Single source of truth for pipeline/ (writes it) and app/ (reads it).
-- The pipeline creates a fresh SQLite file with exactly this DDL, fills it, then VACUUMs it.
-- The app opens it with androidx.sqlite BundledSQLiteDriver (not Room) and applies deltas to it.
-- Timestamps are unix seconds (UTC). Booleans are 0/1. JSON columns hold compact JSON arrays.

PRAGMA user_version = 1;

-- key/value: schema ('1'), version (monotonic int as text), created (unix), source_commit (fmhy/edit sha),
-- history_start (unix ts of the oldest fmhy/edit commit; entries older than that have first_seen = it)
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
) WITHOUT ROWID;

-- one row per docs/*.md wiki page, e.g. id 'video', title 'Movies / TV / Anime', file 'video.md'
CREATE TABLE sections (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    file        TEXT NOT NULL,
    ord         INTEGER NOT NULL
) WITHOUT ROWID;

-- markdown headings inside a section; id = '<section_id>/<anchor>' (anchor = fmhy.net heading slug,
-- de-duplicated with -1, -2 like VitePress), parent_id NULL for top-level headings of the page
CREATE TABLE headings (
    id         TEXT PRIMARY KEY,
    section_id TEXT NOT NULL REFERENCES sections(id),
    parent_id  TEXT REFERENCES headings(id),
    level      INTEGER NOT NULL,          -- 1..4 (markdown #..####)
    title      TEXT NOT NULL,             -- without the ► / ▷ markers
    anchor     TEXT NOT NULL,
    ord        INTEGER NOT NULL           -- order within the section, global across all its headings
) WITHOUT ROWID;
CREATE INDEX headings_section ON headings(section_id, ord);

-- one row per listed site. num is a stable integer key (assigned once per id by the pipeline's history
-- db, never reused) so FTS rowids survive deltas and VACUUM.
CREATE TABLE entries (
    num            INTEGER PRIMARY KEY,
    id             TEXT NOT NULL UNIQUE,  -- stable id, see pipeline ids.py
    section_id     TEXT NOT NULL REFERENCES sections(id),
    heading_id     TEXT REFERENCES headings(id),
    ord            INTEGER NOT NULL,      -- order within the section
    name           TEXT NOT NULL,
    url            TEXT NOT NULL,         -- final URL to open (already base64-decoded when is_base64 = 1)
    original_url   TEXT,                  -- the rentry.co/FMHYB64#… link when is_base64 = 1, else NULL
    description    TEXT NOT NULL DEFAULT '',  -- plain text after " - ", link labels kept, markup removed
    tags           TEXT NOT NULL DEFAULT '',  -- ' / '-split description parts, joined by '|'
    heading_path   TEXT NOT NULL DEFAULT '',  -- 'Section › Heading › Subheading' for search + display
    tier           INTEGER NOT NULL DEFAULT 0, -- 0 normal, 1 ⭐ starred, 2 🌟 top pick
    is_inline_more INTEGER NOT NULL DEFAULT 0, -- 1 = from a comma-separated "more" paragraph
    is_base64      INTEGER NOT NULL DEFAULT 0,
    mirrors        TEXT NOT NULL DEFAULT '[]', -- JSON [{"label":"2","url":"…"}]
    extra_links    TEXT NOT NULL DEFAULT '[]', -- JSON [{"label":"GitHub","url":"…"}]
    first_seen     INTEGER NOT NULL,
    last_changed   INTEGER NOT NULL,
    starred_at     INTEGER                     -- when tier last became >= 1, NULL if never / unknown
);
CREATE INDEX entries_section ON entries(section_id, ord);
CREATE INDEX entries_first_seen ON entries(first_seen DESC);

-- "**Note** - …" lines and > blockquotes, rendered as callouts
CREATE TABLE notes (
    id         TEXT PRIMARY KEY,           -- '<heading_id or section_id>#n<k>'
    section_id TEXT NOT NULL REFERENCES sections(id),
    heading_id TEXT REFERENCES headings(id),
    ord        INTEGER NOT NULL,           -- shares the ord space with entries of the same section
    text_md    TEXT NOT NULL               -- inline markdown (links allowed)
) WITHOUT ROWID;

-- history log for the "Recently added" view; ids are assigned in commit order and never change.
-- Only the last 400 days are shipped; entries.first_seen covers anything older.
CREATE TABLE changes (
    id         INTEGER PRIMARY KEY,
    ts         INTEGER NOT NULL,
    kind       TEXT NOT NULL CHECK (kind IN ('added','removed','starred','unstarred','moved','renamed','url_changed')),
    entry_id   TEXT NOT NULL,              -- may no longer exist in entries (kind = removed)
    section_id TEXT NOT NULL,
    name       TEXT NOT NULL,              -- copied so removed entries can still be shown
    url        TEXT NOT NULL,
    detail     TEXT NOT NULL DEFAULT '',   -- e.g. old name / old section / old url
    commit_sha TEXT NOT NULL
);
CREATE INDEX changes_ts ON changes(ts DESC);

-- monthly update posts from docs/posts/*.md
CREATE TABLE posts (
    id      TEXT PRIMARY KEY,              -- file stem, e.g. 'sept-2026'
    title   TEXT NOT NULL,
    date    INTEGER NOT NULL,
    body_md TEXT NOT NULL                  -- markdown, Vue components (<Post …/>) and ::: fences removed
) WITHOUT ROWID;

-- domains from github.com/fmhy/FMHYFilterlist (filterlist-domains.txt + filterlists-reasons.json,
-- the machine-readable form of docs/unsafe.md); the app warns before opening any URL whose host equals or ends with
-- '.' || domain
CREATE TABLE unsafe_domains (
    domain TEXT PRIMARY KEY,
    reason TEXT NOT NULL DEFAULT ''
) WITHOUT ROWID;

-- Full-text search. Both are external-content tables over entries, kept in sync by the triggers below,
-- so applying a delta (plain INSERT/UPDATE/DELETE on entries) updates search automatically.
CREATE VIRTUAL TABLE fts_words USING fts5(
    name, tags, description, heading_path, url,
    content = 'entries', content_rowid = 'num',
    tokenize = 'unicode61 remove_diacritics 2',
    prefix = '2 3'
);
CREATE VIRTUAL TABLE fts_tri USING fts5(
    name, description,
    content = 'entries', content_rowid = 'num',
    tokenize = 'trigram'
);

CREATE TRIGGER entries_ai AFTER INSERT ON entries BEGIN
    INSERT INTO fts_words(rowid, name, tags, description, heading_path, url)
        VALUES (new.num, new.name, new.tags, new.description, new.heading_path, new.url);
    INSERT INTO fts_tri(rowid, name, description) VALUES (new.num, new.name, new.description);
END;
CREATE TRIGGER entries_ad AFTER DELETE ON entries BEGIN
    INSERT INTO fts_words(fts_words, rowid, name, tags, description, heading_path, url)
        VALUES ('delete', old.num, old.name, old.tags, old.description, old.heading_path, old.url);
    INSERT INTO fts_tri(fts_tri, rowid, name, description) VALUES ('delete', old.num, old.name, old.description);
END;
CREATE TRIGGER entries_au AFTER UPDATE ON entries BEGIN
    INSERT INTO fts_words(fts_words, rowid, name, tags, description, heading_path, url)
        VALUES ('delete', old.num, old.name, old.tags, old.description, old.heading_path, old.url);
    INSERT INTO fts_tri(fts_tri, rowid, name, description) VALUES ('delete', old.num, old.name, old.description);
    INSERT INTO fts_words(rowid, name, tags, description, heading_path, url)
        VALUES (new.num, new.name, new.tags, new.description, new.heading_path, new.url);
    INSERT INTO fts_tri(rowid, name, description) VALUES (new.num, new.name, new.description);
END;

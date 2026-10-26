PRAGMA user_version = 4;
CREATE TABLE folders (id INTEGER PRIMARY KEY, name TEXT NOT NULL, ord INTEGER NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE bookmarks (
  entry_id TEXT PRIMARY KEY, name TEXT NOT NULL, url TEXT NOT NULL, section_id TEXT NOT NULL,
  section_title TEXT NOT NULL, heading_path TEXT NOT NULL, saved_at INTEGER NOT NULL,
  note TEXT NOT NULL DEFAULT '', folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL);
CREATE INDEX bookmarks_saved ON bookmarks(saved_at DESC);
CREATE TABLE history (id INTEGER PRIMARY KEY, url TEXT NOT NULL, entry_id TEXT, title TEXT NOT NULL, ts INTEGER NOT NULL);
CREATE INDEX history_ts ON history(ts DESC);
CREATE TABLE follows (section_id TEXT NOT NULL, heading_id TEXT NOT NULL DEFAULT '', title TEXT NOT NULL,
  created_at INTEGER NOT NULL, excluded INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (section_id, heading_id)) WITHOUT ROWID;   -- heading_id '' = whole section
CREATE TABLE pins (section_id TEXT NOT NULL, heading_id TEXT NOT NULL DEFAULT '', title TEXT NOT NULL,
  ord INTEGER NOT NULL, PRIMARY KEY (section_id, heading_id)) WITHOUT ROWID;
CREATE TABLE hidden_sections (section_id TEXT PRIMARY KEY) WITHOUT ROWID;
CREATE TABLE site_prefs (host TEXT PRIMARY KEY, adblock INTEGER, popups INTEGER, javascript INTEGER,
  desktop INTEGER, dark INTEGER, updated_at INTEGER NOT NULL) WITHOUT ROWID;          -- NULL column = use global setting
CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE downloads (
  id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, file_name TEXT NOT NULL, mime_type TEXT NOT NULL DEFAULT '',
  source_host TEXT NOT NULL DEFAULT '', referer TEXT, user_agent TEXT, total_bytes INTEGER NOT NULL DEFAULT -1,
  downloaded_bytes INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, resumable INTEGER NOT NULL DEFAULT 1,
  restart_note INTEGER NOT NULL DEFAULT 0, allow_mobile INTEGER NOT NULL DEFAULT 0, error TEXT, file_uri TEXT,
  folder_label TEXT NOT NULL DEFAULT '', etag TEXT, last_modified TEXT, created_at INTEGER NOT NULL, finished_at INTEGER, check_json TEXT);
CREATE INDEX downloads_created ON downloads(created_at DESC);

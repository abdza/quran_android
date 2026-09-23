-- quran.db schema created by mobile-sync 0.1.14, taken from a device install
CREATE TABLE bookmark_collection(
  local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  bookmark_local_id INTEGER NOT NULL,
  collection_local_id INTEGER NOT NULL,
  is_active INTEGER NOT NULL DEFAULT 1,
  pending_op TEXT,
  pending_version INTEGER NOT NULL DEFAULT 0,
  last_synced_bookmark_remote_id TEXT,
  last_synced_collection_remote_id TEXT,
  created_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  modified_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  CHECK (is_active IN (0, 1)),
  CHECK (pending_op IS NULL OR pending_op IN ('CREATED', 'DELETED')),
  UNIQUE(bookmark_local_id, collection_local_id),
  FOREIGN KEY(bookmark_local_id) REFERENCES bookmark(local_id),
  FOREIGN KEY(collection_local_id) REFERENCES collection(local_id)
);
CREATE TABLE bookmark(
  local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  remote_id TEXT,
  bookmark_type TEXT NOT NULL,
  sura INTEGER,
  ayah INTEGER,
  page INTEGER,
  ayah_id INTEGER,
  mushaf_id INTEGER NOT NULL DEFAULT 1,
  is_reading INTEGER NOT NULL DEFAULT 0,
  is_in_default_collection INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  bookmark_pending_op TEXT,
  bookmark_pending_version INTEGER NOT NULL DEFAULT 0,
  reading_pending_op TEXT,
  reading_pending_version INTEGER NOT NULL DEFAULT 0,
  default_pending_op TEXT,
  default_pending_version INTEGER NOT NULL DEFAULT 0,
  default_last_synced_bookmark_remote_id TEXT,
  created_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  modified_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  bookmark_modified_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  reading_modified_at INTEGER,
  default_modified_at INTEGER,
  CHECK (bookmark_type IN ('AYAH', 'PAGE')),
  CHECK (
    (bookmark_type = 'AYAH' AND sura IS NOT NULL AND ayah IS NOT NULL AND page IS NULL AND ayah_id IS NOT NULL)
    OR
    (bookmark_type = 'PAGE' AND page IS NOT NULL AND sura IS NULL AND ayah IS NULL AND ayah_id IS NULL)
  ),
  CHECK (is_reading IN (0, 1)),
  CHECK (is_in_default_collection IN (0, 1)),
  CHECK (deleted IN (0, 1)),
  CHECK (bookmark_pending_op IS NULL OR bookmark_pending_op IN ('CREATED', 'MODIFIED', 'DELETED')),
  CHECK (reading_pending_op IS NULL OR reading_pending_op IN ('CREATED', 'MODIFIED', 'DELETED')),
  CHECK (default_pending_op IS NULL OR default_pending_op IN ('CREATED', 'MODIFIED', 'DELETED'))
);
CREATE TABLE collection(
  local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  remote_id TEXT,
  name TEXT NOT NULL,
  created_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  modified_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  is_edited INTEGER NOT NULL DEFAULT 0,
  pending_version INTEGER NOT NULL DEFAULT 0,
  CHECK (deleted IN (0, 1)),
  CHECK (is_edited IN (0, 1))
);
CREATE TABLE note(
  local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  remote_id TEXT,
  note TEXT NOT NULL,
  start_ayah_id INTEGER NOT NULL,
  end_ayah_id INTEGER NOT NULL,
  created_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  modified_at INTEGER DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000) NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  is_edited INTEGER NOT NULL DEFAULT 0,
  pending_version INTEGER NOT NULL DEFAULT 0,
  -- Ensure deleted is either 0 or 1
  CHECK (deleted IN (0, 1)),
  CHECK (is_edited IN (0, 1))
);
CREATE TABLE reading_session(
  local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  remote_id TEXT,
  chapter_number INTEGER NOT NULL,
  verse_number INTEGER NOT NULL,
  is_edited INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER DEFAULT (CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) NOT NULL,
  modified_at INTEGER DEFAULT (CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  pending_version INTEGER NOT NULL DEFAULT 0,
  CHECK (is_edited IN (0, 1)),
  CHECK (deleted IN (0, 1))
);
CREATE INDEX bookmark_collection_active_created_idx
ON bookmark_collection(is_active, created_at DESC);
CREATE INDEX bookmark_collection_collection_active_created_idx
ON bookmark_collection(collection_local_id, is_active, created_at DESC);
CREATE INDEX bookmark_collection_pending_idx
ON bookmark_collection(pending_op, modified_at DESC);
CREATE INDEX bookmark_collection_snapshots_idx
ON bookmark_collection(last_synced_collection_remote_id, last_synced_bookmark_remote_id);
CREATE UNIQUE INDEX bookmark_remote_id_idx ON bookmark(remote_id);
CREATE UNIQUE INDEX bookmark_ayah_identity_idx
ON bookmark(bookmark_type, sura, ayah, mushaf_id)
WHERE bookmark_type = 'AYAH';
CREATE UNIQUE INDEX bookmark_page_identity_idx
ON bookmark(bookmark_type, page, mushaf_id)
WHERE bookmark_type = 'PAGE';
CREATE INDEX bookmark_active_modified_idx
ON bookmark(deleted, modified_at DESC);
CREATE INDEX bookmark_reading_idx
ON bookmark(is_reading, reading_modified_at DESC);
CREATE INDEX bookmark_pending_idx
ON bookmark(bookmark_pending_op, reading_pending_op, default_pending_op, modified_at DESC);
CREATE INDEX bookmark_default_last_synced_idx
ON bookmark(default_last_synced_bookmark_remote_id);
CREATE UNIQUE INDEX collection_remote_id_idx ON collection(remote_id);
CREATE UNIQUE INDEX collection_active_name_idx
ON collection(name)
WHERE deleted = 0;
CREATE INDEX collection_active_created_idx
ON collection(deleted, created_at DESC);
CREATE INDEX collection_unsynced_created_idx
ON collection(created_at DESC)
WHERE remote_id IS NULL OR is_edited = 1;
CREATE INDEX note_start_ayah_idx ON note(start_ayah_id);
CREATE INDEX note_end_ayah_idx ON note(end_ayah_id);
CREATE UNIQUE INDEX note_remote_id_idx ON note(remote_id);
CREATE INDEX note_active_created_idx
ON note(deleted, created_at DESC);
CREATE INDEX note_unsynced_created_idx
ON note(created_at DESC)
WHERE remote_id IS NULL OR deleted = 1 OR is_edited = 1;
CREATE UNIQUE INDEX reading_session_remote_id_idx ON reading_session(remote_id);
CREATE UNIQUE INDEX reading_session_active_position_idx
ON reading_session(chapter_number, verse_number)
WHERE deleted = 0;
CREATE INDEX reading_session_active_modified_idx
ON reading_session(deleted, modified_at DESC, local_id DESC);
CREATE INDEX reading_session_unsynced_modified_idx
ON reading_session(modified_at DESC)
WHERE remote_id IS NULL OR is_edited = 1 OR deleted = 1;

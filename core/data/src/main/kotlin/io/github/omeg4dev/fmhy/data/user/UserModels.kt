package io.github.omeg4dev.fmhy.data.user

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UserBookmark(
    val entryId: String,
    val name: String,
    val url: String,
    val sectionId: String,
    val sectionTitle: String,
    val headingPath: String,
    val savedAt: Long,
    val note: String = "",
    val folderId: Long? = null,
)

@Serializable
data class Folder(
    val id: Long,
    val name: String,
    val ord: Int,
    val createdAt: Long,
    val syncId: String? = null,
)

data class BookmarksSnapshot(
    val folders: List<Folder>,
    val bookmarks: List<UserBookmark>,
)

data class SyncFolderCreate(
    val syncId: String,
    val name: String,
)

data class SyncFolderRename(
    val folderId: Long,
    val name: String,
)

data class SyncBookmarkUpdate(
    val entryId: String,
    val savedAt: Long,
    val name: String? = null,
)

data class SyncBookmarkAdd(
    val bookmark: UserBookmark,
    val folderSyncId: String? = null,
)

data class SyncBookmarkMove(
    val entryId: String,
    val folderSyncId: String?,
)

data class SyncApplyResult(
    val createFolders: List<SyncFolderCreate> = emptyList(),
    val renameFolders: List<SyncFolderRename> = emptyList(),
    val deleteFolderIds: List<Long> = emptyList(),
    val deleteBookmarkEntryIds: List<String> = emptyList(),
    val updateBookmarks: List<SyncBookmarkUpdate> = emptyList(),
    val addBookmarks: List<SyncBookmarkAdd> = emptyList(),
    val moveBookmarks: List<SyncBookmarkMove> = emptyList(),
    val baseJson: String,
)

@Serializable
data class HistoryItem(
    val id: Long,
    val url: String,
    val entryId: String?,
    val title: String,
    val ts: Long,
)

@Serializable
data class Follow(
    val sectionId: String,
    val headingId: String = "",
    val title: String,
    val createdAt: Long,
    val excluded: Boolean = false,
)

@Serializable
data class Pin(
    val sectionId: String,
    val headingId: String = "",
    val title: String,
    val ord: Int,
)

@Serializable
data class SitePrefs(
    val host: String,
    val adblock: Boolean? = null,
    val popups: Boolean? = null,
    val javascript: Boolean? = null,
    val desktop: Boolean? = null,
    val dark: Boolean? = null,
    /** "Fix this site": allow every ad on this host (blocking, element hiding and scriptlets off). */
    val fixSite: Boolean? = null,
    val blockRedirects: Boolean? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class ImportResult(
    val bookmarks: Int,
    val folders: Int,
    val follows: Int,
    val pins: Int,
)

/**
 * Backup JSON schema for export/import.
 * Format: {"format":"fmhy-backup","version":1,"exportedAt":<ms>,"folders":[…],"bookmarks":[…],"follows":[…],"pins":[…],"hidden":[…],"history":[…]?}
 * Bookmarks reference folders by folder name instead of internal database ID.
 */
@Serializable
internal data class BackupPayload(
    val format: String = "fmhy-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val folders: List<BackupFolder> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val follows: List<Follow> = emptyList(),
    val pins: List<Pin> = emptyList(),
    val hidden: List<String> = emptyList(),
    val history: List<BackupHistoryItem>? = null,
    val criticalHidden: Map<String, String> = emptyMap(),
    val settings: JsonObject? = null,
    val siteSettings: List<SitePrefs> = emptyList(),
)

@Serializable
internal data class BackupFolder(
    val name: String,
    val ord: Int,
    val createdAt: Long,
)

@Serializable
internal data class BackupBookmark(
    val entryId: String,
    val name: String,
    val url: String,
    val sectionId: String,
    val sectionTitle: String,
    val headingPath: String,
    val savedAt: Long,
    val note: String = "",
    val folder: String? = null,
)

@Serializable
internal data class BackupHistoryItem(
    val url: String,
    val entryId: String? = null,
    val title: String,
    val ts: Long,
)

data class DownloadRecord(
    val id: Long = 0, val url: String, val fileName: String, val mimeType: String = "",
    val sourceHost: String = "", val referer: String? = null, val userAgent: String? = null,
    val totalBytes: Long = -1, val downloadedBytes: Long = 0, val status: String,
    val resumable: Boolean = true, val restartNote: Boolean = false, val allowMobile: Boolean = false,
    val error: String? = null, val fileUri: String? = null, val folderLabel: String = "",
    val etag: String? = null, val lastModified: String? = null, val createdAt: Long, val finishedAt: Long? = null,
    val checkJson: String? = null,
    val fmhyJson: String? = null,
)

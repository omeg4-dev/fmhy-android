package io.github.omeg4dev.fmhy.data.user

import kotlinx.serialization.Serializable

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

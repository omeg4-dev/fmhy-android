package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.UserBookmark

typealias Bookmark = UserBookmark

data class NotificationModel(
    val title: String,
    val text: String,
    val inboxLines: List<String>,
    val openFilter: String, // "following" or "saved"
    val changeCount: Int,
)

object UpdateWorkerLogic {

    fun resolveLastTs(storedLastTs: Long?, nowSec: Long): Long {
        return storedLastTs ?: (nowSec - 86400L)
    }

    fun filterFollowedChanges(
        changes: List<ChangeItem>,
        follows: List<Follow>,
        hierarchy: Map<String, Set<String>> = emptyMap(),
    ): List<ChangeItem> {
        if (follows.isEmpty()) return emptyList()
        val sectionFollows = follows.filter { it.headingId.isEmpty() }.map { it.sectionId }.toSet()
        val categoryFollows = follows.filter { it.headingId.isNotEmpty() }.flatMap { f ->
            hierarchy[f.headingId] ?: setOf(f.headingId)
        }.toSet()

        val allowedKinds = setOf(ChangeKind.ADDED, ChangeKind.STARRED)
        return changes.filter { item ->
            if (item.kind !in allowedKinds) return@filter false
            if (item.sectionId in sectionFollows) return@filter true
            val hId = item.entry?.headingId
            if (hId != null && hId in categoryFollows) return@filter true
            false
        }
    }

    fun filterSavedChanges(
        changes: List<ChangeItem>,
        bookmarks: List<Bookmark>,
        watchSaved: Boolean,
    ): List<ChangeItem> {
        if (!watchSaved || bookmarks.isEmpty()) return emptyList()
        val allowedKinds = setOf(
            ChangeKind.STARRED,
            ChangeKind.UNSTARRED,
            ChangeKind.REMOVED,
            ChangeKind.URL_CHANGED,
        )
        val bookmarkIds = bookmarks.map { it.entryId }.toSet()
        val bookmarkUrls = bookmarks.map { DatasetStore.normalizeEntryUrl(it.url) }.toSet()

        return changes.filter { item ->
            if (item.kind !in allowedKinds) return@filter false
            item.entryId in bookmarkIds || DatasetStore.normalizeEntryUrl(item.url) in bookmarkUrls
        }
    }

    fun buildNotification(
        followedChanges: List<ChangeItem>,
        savedChanges: List<ChangeItem>,
        sectionTitles: Map<String, String> = emptyMap(),
    ): NotificationModel? {
        val allDistinct = (followedChanges + savedChanges).distinctBy { it.id }
        if (allDistinct.isEmpty()) return null

        val hasFollowed = followedChanges.isNotEmpty()
        val hasSaved = savedChanges.isNotEmpty()

        val title: String
        val openFilter: String
        val primaryChanges: List<ChangeItem>

        if (hasFollowed && !hasSaved) {
            openFilter = "following"
            primaryChanges = followedChanges
            title = if (followedChanges.size == 1) {
                "1 new site in followed categories"
            } else {
                "${followedChanges.size} new sites in followed categories"
            }
        } else if (!hasFollowed && hasSaved) {
            openFilter = "saved"
            primaryChanges = savedChanges
            title = if (savedChanges.size == 1) {
                val sc = savedChanges.first()
                val action = when (sc.kind) {
                    ChangeKind.STARRED -> "starred"
                    ChangeKind.UNSTARRED -> "unstarred"
                    ChangeKind.REMOVED -> "removed"
                    ChangeKind.URL_CHANGED -> "updated"
                    else -> "changed"
                }
                "Your saved site ${sc.name} was $action"
            } else {
                "FMHY: ${savedChanges.size} saved sites changed"
            }
        } else {
            // Both followed and saved
            openFilter = "following"
            primaryChanges = followedChanges + savedChanges
            title = if (followedChanges.size == 1) {
                "1 new site in followed categories"
            } else {
                "${followedChanges.size} new sites in followed categories"
            }
        }

        // text = up to 3 names
        val text = primaryChanges.distinctBy { it.name }
            .take(3)
            .joinToString(", ") { it.name }

        // InboxStyle with up to 6 lines "Video › Streaming: Foo, Bar"
        val inboxLines = buildInboxLines(primaryChanges, sectionTitles).take(6)

        return NotificationModel(
            title = title,
            text = text,
            inboxLines = inboxLines,
            openFilter = openFilter,
            changeCount = allDistinct.size,
        )
    }

    private fun buildInboxLines(
        changes: List<ChangeItem>,
        sectionTitles: Map<String, String>,
    ): List<String> {
        val grouped = changes.groupBy { item ->
            val sec = sectionTitles[item.sectionId] ?: item.sectionId
            val heading = item.entry?.headingPath
            if (!heading.isNullOrBlank()) {
                val segments = heading.split(Regex("""\s*[›>]\s*""")).filter { it.isNotBlank() }
                if (segments.size > 1) {
                    "$sec › ${segments.last()}"
                } else {
                    sec
                }
            } else {
                sec
            }
        }
        return grouped.map { (groupName, items) ->
            val names = items.map { it.name }.distinct().joinToString(", ")
            "$groupName: $names"
        }
    }

    fun evaluate(
        changes: List<ChangeItem>,
        follows: List<Follow>,
        bookmarks: List<Bookmark>,
        lastTs: Long?,
        nowSec: Long = System.currentTimeMillis() / 1000,
        watchSaved: Boolean = true,
        sectionTitles: Map<String, String> = emptyMap(),
        hierarchy: Map<String, Set<String>> = emptyMap(),
    ): NotificationModel? {
        val effectiveLastTs = resolveLastTs(lastTs, nowSec)
        val sinceChanges = changes.filter { it.ts >= effectiveLastTs }
        val followed = filterFollowedChanges(sinceChanges, follows, hierarchy)
        val saved = filterSavedChanges(sinceChanges, bookmarks, watchSaved)
        return buildNotification(followed, saved, sectionTitles)
    }
}

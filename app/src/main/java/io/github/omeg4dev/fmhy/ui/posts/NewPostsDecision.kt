package io.github.omeg4dev.fmhy.ui.posts

/**
 * Which posts deserve a notification: listed now, not in the list we knew before, and not read.
 * [previouslyKnown] or [read] null means "first run": there is no baseline, so nothing is new.
 * Result is newest first.
 */
fun newUnreadPosts(
    listed: List<CachedPost>,
    previouslyKnown: Set<String>?,
    read: Set<String>?,
): List<CachedPost> {
    if (previouslyKnown == null || read == null) return emptyList()
    return listed
        .filter { it.path !in previouslyKnown && it.path !in read }
        .sortedByDescending { it.dateEpoch }
}

/** Title and text of the single "new post" notification; null when there is nothing new. */
fun newPostsNotificationText(newPosts: List<CachedPost>): Pair<String, String>? {
    val newest = newPosts.firstOrNull() ?: return null
    val more = newPosts.size - 1
    val text = if (more > 0) "${newest.title} and $more more" else newest.title
    return "New FMHY post" to text
}

/** At most one posts check per 24 hours. */
fun shouldCheckPosts(lastCheckEpochSec: Long?, nowSec: Long): Boolean =
    lastCheckEpochSec == null || nowSec - lastCheckEpochSec >= 86_400L || nowSec < lastCheckEpochSec

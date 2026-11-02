package io.github.omeg4dev.fmhy.browser.download

enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Done"),
    FAILED("Failed"),
}

enum class DownloadSort(val label: String) {
    NEWEST("Newest"),
    NAME("Name"),
    SIZE("Size"),
}

object DownloadsView {

    fun matchesFilter(item: DownloadItem, filter: DownloadFilter): Boolean {
        return when (filter) {
            DownloadFilter.ALL -> true
            DownloadFilter.ACTIVE -> item.status == DownloadStatus.WAITING ||
                item.status == DownloadStatus.WAITING_WIFI ||
                item.status == DownloadStatus.RUNNING ||
                item.status == DownloadStatus.PAUSED ||
                item.status == DownloadStatus.CHECKING ||
                item.status == DownloadStatus.AWAITING_DECISION
            DownloadFilter.DONE -> item.status == DownloadStatus.FINISHED
            DownloadFilter.FAILED -> item.status == DownloadStatus.FAILED
        }
    }

    fun counts(items: List<DownloadItem>): Map<DownloadFilter, Int> {
        val result = mutableMapOf(
            DownloadFilter.ALL to items.size,
            DownloadFilter.ACTIVE to 0,
            DownloadFilter.DONE to 0,
            DownloadFilter.FAILED to 0,
        )
        for (item in items) {
            when {
                matchesFilter(item, DownloadFilter.ACTIVE) -> {
                    result[DownloadFilter.ACTIVE] = (result[DownloadFilter.ACTIVE] ?: 0) + 1
                }
                matchesFilter(item, DownloadFilter.DONE) -> {
                    result[DownloadFilter.DONE] = (result[DownloadFilter.DONE] ?: 0) + 1
                }
                matchesFilter(item, DownloadFilter.FAILED) -> {
                    result[DownloadFilter.FAILED] = (result[DownloadFilter.FAILED] ?: 0) + 1
                }
            }
        }
        return result
    }

    fun apply(
        items: List<DownloadItem>,
        filter: DownloadFilter,
        query: String,
        sort: DownloadSort,
    ): List<DownloadItem> {
        val trimmedQuery = query.trim()

        val filtered = items.filter { item ->
            if (!matchesFilter(item, filter)) return@filter false
            if (trimmedQuery.isNotEmpty()) {
                val matchesName = item.fileName.contains(trimmedQuery, ignoreCase = true)
                val matchesHost = item.sourceHost.contains(trimmedQuery, ignoreCase = true)
                if (!matchesName && !matchesHost) return@filter false
            }
            true
        }

        val comparator = when (sort) {
            DownloadSort.NEWEST -> compareByDescending<DownloadItem> { it.createdAt }.thenByDescending { it.id }
            DownloadSort.NAME -> compareBy<DownloadItem, String>(String.CASE_INSENSITIVE_ORDER) { it.fileName }.thenByDescending { it.id }
            DownloadSort.SIZE -> compareByDescending<DownloadItem> {
                if (it.totalBytes > 0) it.totalBytes else it.downloadedBytes
            }.thenByDescending { it.id }
        }

        return filtered.sortedWith(comparator)
    }
}

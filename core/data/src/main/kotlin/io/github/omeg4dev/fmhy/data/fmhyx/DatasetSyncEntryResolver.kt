package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry

/**
 * [SyncEntryResolver] backed by [DatasetStore] and an injected NSFW URL provider.
 * Database/provider exceptions propagate directly so a failing lookup aborts sync.
 */
class DatasetSyncEntryResolver(
    private val datasetStore: DatasetStore,
    private val nsfwUrlsProvider: suspend () -> Collection<String> = { emptyList() },
) : SyncEntryResolver {

    override suspend fun byIds(ids: Collection<String>): Map<String, ResolvedEntry> {
        val uniqueIds = ids.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        val entries = datasetStore.entries(uniqueIds)
        if (entries.isEmpty()) return emptyMap()
        val sectionTitles = datasetStore.sections().associate { it.id to it.title }
        return entries.associate { entry ->
            entry.id to entry.toResolvedEntry(sectionTitles[entry.sectionId].orEmpty())
        }
    }

    override suspend fun byUrls(urls: Collection<String>): Map<String, List<ResolvedEntry>> {
        val uniqueUrls = urls.distinct()
        if (uniqueUrls.isEmpty()) return emptyMap()
        val requested = uniqueUrls.toHashSet()
        val entries = datasetStore.entriesByUrls(uniqueUrls)
        if (entries.isEmpty()) return emptyMap()
        val sectionTitles = datasetStore.sections().associate { it.id to it.title }
        val result = HashMap<String, MutableList<ResolvedEntry>>()
        for (entry in entries) {
            val resolved = entry.toResolvedEntry(sectionTitles[entry.sectionId].orEmpty())
            if (entry.url in requested) {
                result.getOrPut(entry.url) { mutableListOf() }.add(resolved)
            }
            val orig = entry.originalUrl
            if (orig != null && orig != entry.url && orig in requested) {
                result.getOrPut(orig) { mutableListOf() }.add(resolved)
            }
        }
        return result
    }

    override suspend fun nsfwUrls(): Set<String> = nsfwUrlsProvider().toHashSet()
}

private fun Entry.toResolvedEntry(sectionTitle: String): ResolvedEntry = ResolvedEntry(
    entryId = id,
    url = url,
    originalUrl = originalUrl,
    name = name,
    sectionId = sectionId,
    sectionTitle = sectionTitle,
    headingId = headingId,
    headingPath = headingPath,
    description = description,
)

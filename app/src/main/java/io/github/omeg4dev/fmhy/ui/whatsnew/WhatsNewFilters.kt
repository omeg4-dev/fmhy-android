package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeKind

fun filtersAreCustom(period: WhatsNewPeriod, types: Set<ChangeKind>, scope: WhatsNewScope): Boolean =
    period != WhatsNewPeriod.WEEK || types != WhatsNewGrouping.DEFAULT_TYPES || scope !is WhatsNewScope.All

data class WhatsNewFilterPrefs(
    val remember: Boolean = false,
    val period: WhatsNewPeriod = WhatsNewPeriod.WEEK,
    val types: Set<ChangeKind> = WhatsNewGrouping.DEFAULT_TYPES,
    val scope: WhatsNewScope = WhatsNewScope.All,
)

object WhatsNewFilterCodec {

    fun encodePeriod(period: WhatsNewPeriod): String = period.name

    fun decodePeriod(raw: String?): WhatsNewPeriod {
        if (raw == null) return WhatsNewPeriod.WEEK
        return WhatsNewPeriod.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: WhatsNewPeriod.WEEK
    }

    fun encodeTypes(types: Set<ChangeKind>): String {
        if (types.isEmpty()) return ""
        return types.joinToString(",") { it.value }
    }

    fun decodeTypes(raw: String?): Set<ChangeKind> {
        if (raw == null) return WhatsNewGrouping.DEFAULT_TYPES
        if (raw.isEmpty()) return emptySet()
        val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = tokens.mapNotNull { token ->
            ChangeKind.entries.firstOrNull {
                it.value.equals(token, ignoreCase = true) || it.name.equals(token, ignoreCase = true)
            }
        }.toSet()
        return if (parsed.isEmpty()) WhatsNewGrouping.DEFAULT_TYPES else parsed
    }

    fun encodeScope(scope: WhatsNewScope): String = when (scope) {
        is WhatsNewScope.All -> "all"
        is WhatsNewScope.Following -> "following"
        is WhatsNewScope.Saved -> "saved"
        is WhatsNewScope.Sections -> "sections:" + scope.sections.sorted().joinToString(",")
    }

    fun decodeScope(raw: String?): WhatsNewScope {
        if (raw == null) return WhatsNewScope.All
        return when {
            raw.equals("all", ignoreCase = true) -> WhatsNewScope.All
            raw.equals("following", ignoreCase = true) -> WhatsNewScope.Following
            raw.equals("saved", ignoreCase = true) -> WhatsNewScope.Saved
            raw.startsWith("sections:", ignoreCase = true) -> {
                val rest = raw.substring("sections:".length)
                val ids = rest.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                if (ids.isEmpty()) WhatsNewScope.All else WhatsNewScope.Sections(ids)
            }
            else -> WhatsNewScope.All
        }
    }
}

fun resolveInitialFilters(
    stored: WhatsNewFilterPrefs,
    initialFilter: String,      // NavKey.filter: "all" | "following" | "saved"
    initialType: String?,       // NavKey.type: null | "ALL" | a ChangeKind name or value (case-insensitive)
    initialPeriod: String?,     // NavKey.period: null | "since_last_visit"
): WhatsNewFilterPrefs {
    val preselected = initialFilter != "all" || initialType != null || initialPeriod != null
    if (preselected) {
        val period = if (initialPeriod == "since_last_visit") WhatsNewPeriod.SINCE_LAST_VISIT else WhatsNewPeriod.WEEK
        val types = when {
            initialType == null -> WhatsNewGrouping.DEFAULT_TYPES
            initialType.equals("ALL", ignoreCase = true) -> emptySet()
            else -> ChangeKind.entries.firstOrNull {
                it.name.equals(initialType, ignoreCase = true) || it.value.equals(initialType, ignoreCase = true)
            }?.let { setOf(it) } ?: WhatsNewGrouping.DEFAULT_TYPES
        }
        val scope = when (initialFilter) {
            "following" -> WhatsNewScope.Following
            "saved" -> WhatsNewScope.Saved
            else -> WhatsNewScope.All
        }
        return WhatsNewFilterPrefs(
            remember = stored.remember,
            period = period,
            types = types,
            scope = scope,
        )
    }

    return if (stored.remember) {
        stored
    } else {
        WhatsNewFilterPrefs(
            remember = false,
            period = WhatsNewPeriod.WEEK,
            types = WhatsNewGrouping.DEFAULT_TYPES,
            scope = WhatsNewScope.All,
        )
    }
}

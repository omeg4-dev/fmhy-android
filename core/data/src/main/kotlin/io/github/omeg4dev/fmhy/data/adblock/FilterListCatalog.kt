package io.github.omeg4dev.fmhy.data.adblock

/**
 * Filter lists the in-app browser can use. URLs follow uBlock Origin's own `assets/assets.json`
 * (first URL is tried first, the rest are mirrors). Ids are stable: they are stored in settings.
 */
data class FilterList(
    val id: String,
    val title: String,
    val group: Group,
    val urls: List<String>,
    /** On in the "Balanced" preset (and for new installs). */
    val balanced: Boolean,
    /** On in the "Strict" preset. */
    val strict: Boolean,
) {
    enum class Group { UBLOCK, ADS, PRIVACY, ANNOYANCES, FMHY }
}

object FilterListCatalog {
    private const val UASSETS = "https://ublockorigin.github.io/uAssets"

    val all: List<FilterList> = listOf(
        FilterList("ublock-filters", "uBlock filters", FilterList.Group.UBLOCK,
            listOf("$UASSETS/filters/filters.min.txt"), balanced = true, strict = true),
        FilterList("ublock-badware", "uBlock filters – Badware risks", FilterList.Group.UBLOCK,
            listOf("$UASSETS/filters/badware.min.txt"), balanced = true, strict = true),
        FilterList("ublock-privacy", "uBlock filters – Privacy", FilterList.Group.UBLOCK,
            listOf("$UASSETS/filters/privacy.min.txt"), balanced = true, strict = true),
        FilterList("ublock-quick-fixes", "uBlock filters – Quick fixes", FilterList.Group.UBLOCK,
            listOf("$UASSETS/filters/quick-fixes.min.txt"), balanced = true, strict = true),
        FilterList("ublock-unbreak", "uBlock filters – Unbreak", FilterList.Group.UBLOCK,
            listOf("$UASSETS/filters/unbreak.min.txt"), balanced = true, strict = true),
        FilterList("easylist", "EasyList", FilterList.Group.ADS,
            listOf("https://easylist.to/easylist/easylist.txt", "$UASSETS/thirdparties/easylist.txt"),
            balanced = true, strict = true),
        FilterList("easyprivacy", "EasyPrivacy", FilterList.Group.PRIVACY,
            listOf("https://easylist.to/easylist/easyprivacy.txt", "$UASSETS/thirdparties/easyprivacy.txt"),
            balanced = true, strict = true),
        FilterList("peter-lowe", "Peter Lowe's Ad and tracking server list", FilterList.Group.PRIVACY,
            listOf("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=1&mimetype=plaintext"),
            balanced = true, strict = true),
        FilterList("ublock-cookies", "uBlock filters – Cookie notices", FilterList.Group.ANNOYANCES,
            listOf("$UASSETS/filters/annoyances-cookies.txt"), balanced = false, strict = true),
        FilterList("ublock-annoyances", "uBlock filters – Annoyances", FilterList.Group.ANNOYANCES,
            listOf("$UASSETS/filters/annoyances.txt"), balanced = false, strict = true),
        FilterList("fmhy-filterlist", "FMHY unsafe sites", FilterList.Group.FMHY,
            listOf("https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/filterlist.txt"),
            balanced = true, strict = true),
        FilterList("fmhy-ublock-only", "FMHY unsafe sites – uBlock extras", FilterList.Group.FMHY,
            listOf("https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/filters-ublock-only.txt"),
            balanced = true, strict = true),
    )

    /** The list that "Hide cookie banners" switches on and off. */
    const val COOKIE_LIST_ID = "ublock-cookies"

    fun byId(id: String): FilterList? = all.firstOrNull { it.id == id }

    val balancedIds: Set<String> = all.filter { it.balanced }.mapTo(LinkedHashSet()) { it.id }
    val strictIds: Set<String> = all.filter { it.strict }.mapTo(LinkedHashSet()) { it.id }
}

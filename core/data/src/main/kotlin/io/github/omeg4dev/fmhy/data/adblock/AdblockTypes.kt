package io.github.omeg4dev.fmhy.data.adblock

enum class ResourceType {
    DOCUMENT,
    SUBDOCUMENT,
    SCRIPT,
    STYLESHEET,
    IMAGE,
    MEDIA,
    FONT,
    XHR,
    WEBSOCKET,
    PING,
    POPUP,
    OTHER
}

data class FilterRequest(
    val url: String,
    val sourceUrl: String,
    val type: ResourceType
)

sealed interface MatchResult {
    data object NoMatch : MatchResult
    data class Block(val rule: String) : MatchResult
    data class Allow(val rule: String) : MatchResult
    data class Redirect(val resource: String, val rule: String) : MatchResult
}

data class Scriptlet(
    val name: String,
    val args: List<String>
)

data class EngineStats(
    val networkRules: Int,
    val cosmeticRules: Int,
    val scriptletRules: Int,
    val ignored: Map<String, Int>
)

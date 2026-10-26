package io.github.omeg4dev.fmhy.report

import kotlinx.serialization.Serializable

enum class ReportArea(val label: String) {
    PAGE_DOES_NOT_LOAD("Page does not load"),
    LISTS("Lists"),
    SEARCH("Search"),
    DOWNLOADS("Downloads"),
    UPDATE("Update"),
    OTHER("Other"),
}

data class TechnicalInfo(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

@Serializable
data class CrashDetails(
    val exceptionClass: String,
    val frames: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(
            throwable: Throwable,
            appPackage: String = "io.github.omeg4dev.fmhy",
            timestamp: Long = System.currentTimeMillis(),
        ): CrashDetails {
            var root = throwable
            while (root.cause != null && root.cause !== root) {
                root = root.cause!!
            }
            val frames = root.stackTrace
                .filter { it.className.startsWith(appPackage) }
                .take(8)
                .map { element ->
                    val simpleClass = element.className.substringAfterLast('.')
                    val fileName = element.fileName
                    val fileInfo = if (fileName != null) "$fileName:${element.lineNumber}" else "${element.lineNumber}"
                    "$simpleClass.${element.methodName}($fileInfo)"
                }
            return CrashDetails(
                exceptionClass = root.javaClass.name,
                frames = frames,
                timestamp = timestamp,
            )
        }
    }
}

data class ReportDraft(
    val area: ReportArea,
    val whatWentWrong: String,
    val expected: String = "",
    val includeTechnical: Boolean,
    val includePageUrl: Boolean,
    val pageUrl: String?,
    val isPrivateTab: Boolean,
    val technical: TechnicalInfo?,
    val crash: CrashDetails?,
)

data class IssueLink(
    val url: String,
    val truncated: Boolean,
)

const val DISCORD_PROFILE_URL = "https://discord.com/users/626069774002159619"

enum class ReportAction {
    GITHUB,
    DISCORD,
}

data class ReportRequest(
    val defaultArea: ReportArea = ReportArea.OTHER,
    val pageUrl: String? = null,
    val isPrivateTab: Boolean = false,
    val crash: CrashDetails? = null,
    val emphasizedAction: ReportAction? = null,
)

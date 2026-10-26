package io.github.omeg4dev.fmhy.report

import java.net.URLEncoder

object IssueUrlBuilder {
    private const val BASE_URL = "https://github.com/omeg4-dev/fmhy-android/issues/new"
    private const val TRUNCATION_SUFFIX = "\n\n(report shortened - the full text was copied)"

    fun buildTitle(draft: ReportDraft): String {
        if (draft.crash != null) {
            val simpleName = draft.crash.exceptionClass.substringAfterLast('.')
            return "[Crash] $simpleName"
        }
        val singleLine = draft.whatWentWrong.replace('\r', ' ').replace('\n', ' ').trim()
        val desc = if (singleLine.isBlank()) "(not described)" else singleLine.take(60)
        return "[${draft.area.label}] $desc"
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    fun build(title: String, body: String, maxLength: Int = 7000): IssueLink {
        val encTitle = urlEncode(title)
        val fullUrl = "$BASE_URL?title=$encTitle&body=${urlEncode(body)}"
        if (fullUrl.length <= maxLength) {
            return IssueLink(url = fullUrl, truncated = false)
        }

        val lines = body.lines().toMutableList()
        var finalUrl: String? = null

        while (lines.isNotEmpty()) {
            lines.removeAt(lines.lastIndex)
            val shortenedBody = if (lines.isEmpty()) {
                TRUNCATION_SUFFIX.trimStart()
            } else {
                lines.joinToString("\n") + TRUNCATION_SUFFIX
            }
            val candidateUrl = "$BASE_URL?title=$encTitle&body=${urlEncode(shortenedBody)}"
            if (candidateUrl.length <= maxLength) {
                finalUrl = candidateUrl
                break
            }
        }

        if (finalUrl == null) {
            var shortened = TRUNCATION_SUFFIX.trimStart()
            while (shortened.isNotEmpty() && "$BASE_URL?title=$encTitle&body=${urlEncode(shortened)}".length > maxLength) {
                shortened = shortened.dropLast(1)
            }
            finalUrl = "$BASE_URL?title=$encTitle&body=${urlEncode(shortened)}"
        }

        return IssueLink(url = finalUrl, truncated = true)
    }

    fun build(draft: ReportDraft, maxLength: Int = 7000): IssueLink {
        return build(buildTitle(draft), ReportText.build(draft), maxLength)
    }
}

package io.github.omeg4dev.fmhy.data.doc

sealed interface DocResult {
    data class Success(
        val text: String,
        val stale: Boolean = false,
        val rawText: String = text,
        val frontMatter: Map<String, String> = emptyMap(),
    ) : DocResult {
        val content: String get() = text
    }

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : DocResult

    val textOrNull: String?
        get() = (this as? Success)?.text
}

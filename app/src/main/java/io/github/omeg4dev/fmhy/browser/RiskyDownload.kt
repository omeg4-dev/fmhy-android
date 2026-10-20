package io.github.omeg4dev.fmhy.browser

object RiskyDownload {
    val RISKY_EXTENSIONS = setOf(
        "apk", "xapk", "apks", "exe", "msi", "bat", "cmd", "scr", "dmg", "pkg", "jar", "vbs", "ps1"
    )

    fun extractExtension(fileNameOrUrl: String): String {
        // Strip query params and fragments if present
        val clean = fileNameOrUrl.substringBefore('?').substringBefore('#').trimEnd('.', ' ')
        val lastDot = clean.lastIndexOf('.')
        if (lastDot == -1 || lastDot == clean.length - 1) return ""
        val ext = clean.substring(lastDot + 1)
        if (ext.contains('/') || ext.contains('\\')) return ""
        return ext.lowercase()
    }

    fun isRiskyExtension(fileNameOrUrl: String): Boolean {
        val ext = extractExtension(fileNameOrUrl)
        return RISKY_EXTENSIONS.contains(ext)
    }

    fun warningMessage(host: String): String {
        return "This file can install software. Only download it if you trust $host."
    }
}

package io.github.omeg4dev.fmhy.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CrashReportStore(
    val file: File,
    private val enabled: () -> Boolean = { true },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(details: CrashDetails) {
        if (!enabled()) return
        runCatching {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val text = json.encodeToString(details)
            file.writeText(text)
        }
    }

    fun peek(): CrashDetails? {
        if (!enabled()) return null
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CrashDetails>(file.readText())
        }.getOrNull()
    }

    fun clear() {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

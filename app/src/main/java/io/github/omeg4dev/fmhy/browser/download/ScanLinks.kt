package io.github.omeg4dev.fmhy.browser.download

import java.net.URLEncoder
import java.util.Base64

object ScanLinks {
    fun fileReport(service: ScanService, sha256: String): String = when (service) {
        ScanService.VIRUSTOTAL -> "https://www.virustotal.com/gui/file/$sha256"
        ScanService.HYBRID_ANALYSIS -> "https://hybrid-analysis.com/sample/$sha256"
    }

    fun linkReport(service: ScanService, url: String): String {
        val enc = URLEncoder.encode(url, "UTF-8")
        return when (service) {
            ScanService.VIRUSTOTAL -> "https://www.virustotal.com/gui/search/$enc"
            ScanService.HYBRID_ANALYSIS -> "https://hybrid-analysis.com/search?query=$enc"
        }
    }

    fun uploadPage(service: ScanService): String = when (service) {
        ScanService.VIRUSTOTAL -> "https://www.virustotal.com/gui/home/upload"
        ScanService.HYBRID_ANALYSIS -> "https://hybrid-analysis.com/"
    }

    fun vtUrlId(url: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
    }
}

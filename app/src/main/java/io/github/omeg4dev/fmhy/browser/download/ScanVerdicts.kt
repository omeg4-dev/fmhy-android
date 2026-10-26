package io.github.omeg4dev.fmhy.browser.download

object ScanVerdicts {
    fun fromCounts(malicious: Int, suspicious: Int, engines: Int): ScanVerdict {
        if (engines <= 0) return ScanVerdict.UNKNOWN
        if (malicious >= 3) return ScanVerdict.DANGEROUS
        if (malicious >= 1 || suspicious >= 1) return ScanVerdict.SUSPICIOUS
        return ScanVerdict.NO_FLAGS
    }

    fun fromHybridVerdict(verdict: String?): ScanVerdict {
        if (verdict == null) return ScanVerdict.UNKNOWN
        return when (verdict.trim().lowercase()) {
            "malicious" -> ScanVerdict.DANGEROUS
            "suspicious" -> ScanVerdict.SUSPICIOUS
            "no specific threat", "no-specific-threat", "whitelisted", "clean" -> ScanVerdict.NO_FLAGS
            else -> ScanVerdict.UNKNOWN
        }
    }

    fun combine(results: List<ServiceResult>): ScanVerdict {
        val valid = results.filter { it.error == null && it.verdict != ScanVerdict.UNKNOWN }
        if (valid.isEmpty()) return ScanVerdict.UNKNOWN
        return valid.maxByOrNull { it.verdict.rank }!!.verdict
    }
}

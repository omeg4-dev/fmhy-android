package io.github.omeg4dev.fmhy.ui.section

/**
 * Maps Non-English section headings to country flag emojis.
 */
object LanguageFlags {

    fun countryCodeToFlag(countryCode: String): String {
        if (countryCode.length != 2) return countryCode
        val upper = countryCode.uppercase()
        val first = 0x1F1E6 + (upper[0] - 'A')
        val second = 0x1F1E6 + (upper[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private val LANGUAGE_MAP = mapOf(
        "arabic" to "SA",
        "bangla" to "BD",
        "bengali" to "BD",
        "bulgarian" to "BG",
        "chinese" to "CN",
        "czech" to "CZ",
        "filipino" to "PH",
        "tagalog" to "PH",
        "pinoy" to "PH",
        "finnish" to "FI",
        "french" to "FR",
        "german" to "DE",
        "greek" to "GR",
        "hebrew" to "IL",
        "hungarian" to "HU",
        "indian" to "IN",
        "indian languages" to "IN",
        "hindi" to "IN",
        "indonesian" to "ID",
        "italian" to "IT",
        "japanese" to "JP",
        "korean" to "KR",
        "persian" to "IR",
        "farsi" to "IR",
        "polish" to "PL",
        "romanian" to "RO",
        "russian" to "RU",
        "slovak" to "SK",
        "spanish" to "ES",
        "swedish" to "SE",
        "thai" to "TH",
        "turkish" to "TR",
        "ukrainian" to "UA",
        "uzbek" to "UZ",
        "vietnamese" to "VN",
        // Additional common languages
        "dutch" to "NL",
        "danish" to "DK",
        "norwegian" to "NO",
        "catalan" to "AD",
        "croatian" to "HR",
        "serbian" to "RS",
        "malay" to "MY",
        "tamil" to "IN",
        "telugu" to "IN",
        "urdu" to "PK",
    )

    /**
     * Extracts language name and returns flag emoji, or null if unknown / not applicable.
     */
    fun flagFor(headingTitle: String): String? {
        val clean = headingTitle
            .trimStart('#')
            .replace("►", "")
            .replace("▷", "")
            .trim()
        if (clean.isEmpty()) return null

        val lang = clean.substringBefore(" / ").trim().lowercase()

        // Portuguese: Brazilian if stated, else Portugal
        if (lang == "portuguese" || lang == "português" || lang == "brazilian portuguese") {
            return if (clean.contains("brazil", ignoreCase = true)) {
                countryCodeToFlag("BR")
            } else {
                countryCodeToFlag("PT")
            }
        }

        // Non-language or aggregate headings
        if (lang.startsWith("other") || lang.startsWith("multi") || lang == "global") {
            return "🌍"
        }

        val code = LANGUAGE_MAP[lang]
        if (code != null) {
            return countryCodeToFlag(code)
        }

        // Substring fallback for names like "Indian Languages" or compound names
        for ((key, c) in LANGUAGE_MAP) {
            if (lang.contains(key)) {
                return countryCodeToFlag(c)
            }
        }

        return null
    }

    /**
     * Section-aware flag lookup. Returns the flag emoji only for the "non-english" section.
     */
    fun flagFor(sectionId: String?, headingTitle: String): String? {
        if (sectionId != "non-english") return null
        return flagFor(headingTitle)
    }

    fun flagForSection(sectionId: String?, headingTitle: String): String? {
        return flagFor(sectionId, headingTitle)
    }
}

/**
 * Top-level convenience functions matching brief specification.
 */
fun flagFor(headingTitle: String): String? = LanguageFlags.flagFor(headingTitle)

fun flagFor(sectionId: String?, headingTitle: String): String? = LanguageFlags.flagFor(sectionId, headingTitle)

fun flagForSection(sectionId: String?, headingTitle: String): String? = LanguageFlags.flagFor(sectionId, headingTitle)

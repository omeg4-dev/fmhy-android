package io.github.omeg4dev.fmhy.browser

object DesktopUa {
    private val ANDROID_VER_REGEX = Regex("""Android\s+[^;]+;""")

    fun transform(defaultUa: String): String {
        return defaultUa
            .replace("; wv", "")
            .replace("Mobile", "")
            .replace(ANDROID_VER_REGEX, "X11; Linux x86_64;")
    }
}

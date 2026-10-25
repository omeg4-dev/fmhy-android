package io.github.omeg4dev.fmhy.browser

enum class LoadFailureKind {
    BLOCKED,
    OFFLINE,
    REFUSED,
    TIMEOUT,
    SERVER,
    BLANK,
    UNKNOWN,
}

object RescueReason {
    const val ERROR_UNKNOWN = -1
    const val ERROR_HOST_LOOKUP = -2
    const val ERROR_UNSUPPORTED_AUTH_SCHEME = -3
    const val ERROR_AUTHENTICATION = -4
    const val ERROR_PROXY_AUTHENTICATION = -5
    const val ERROR_CONNECT = -6
    const val ERROR_IO = -7
    const val ERROR_TIMEOUT = -8
    const val ERROR_REDIRECT_LOOP = -9
    const val ERROR_UNSUPPORTED_SCHEME = -10
    const val ERROR_FAILED_SSL_HANDSHAKE = -11
    const val ERROR_BAD_URL = -12
    const val ERROR_FILE = -13
    const val ERROR_FILE_NOT_FOUND = -14
    const val ERROR_TOO_MANY_REQUESTS = -15

    fun shouldTrigger(errorCode: Int, description: String = ""): Boolean {
        if (errorCode == ERROR_UNSUPPORTED_SCHEME) return false
        if (description.contains("net::ERR_ABORTED", ignoreCase = true) ||
            description.contains("cancel", ignoreCase = true)) {
            return false
        }
        return when (errorCode) {
            ERROR_HOST_LOOKUP,
            ERROR_CONNECT,
            ERROR_TIMEOUT,
            ERROR_FAILED_SSL_HANDSHAKE,
            ERROR_UNKNOWN,
            ERROR_IO,
            ERROR_REDIRECT_LOOP,
            ERROR_TOO_MANY_REQUESTS -> true
            else -> false
        }
    }

    fun shouldTriggerHttp(statusCode: Int): Boolean {
        return statusCode in 500..599
    }

    fun shouldTriggerRefused(statusCode: Int): Boolean {
        return statusCode in setOf(403, 429, 451)
    }

    fun fromErrorCode(errorCode: Int, description: String = ""): String {
        if (description.contains("CLEARTEXT", ignoreCase = true)) return "Insecure (http) connection blocked"
        return when (errorCode) {
            ERROR_HOST_LOOKUP -> "The site's address could not be found"
            ERROR_CONNECT -> "Could not connect to the site"
            ERROR_TIMEOUT -> "The site did not answer"
            ERROR_FAILED_SSL_HANDSHAKE -> "A secure connection to the site failed"
            ERROR_REDIRECT_LOOP -> "The site keeps redirecting"
            ERROR_TOO_MANY_REQUESTS -> "Too many requests to the site"
            ERROR_IO -> "The connection was interrupted"
            else -> "The page could not be loaded"
        }
    }

    fun fromHttpStatus(statusCode: Int): String {
        val what = when (statusCode) {
            403 -> "The site refused access"
            429 -> "Too many requests to the site"
            451 -> "The site is not available here"
            503, 529 -> "The site is overloaded or down for maintenance"
            504, 522, 524 -> "The site did not answer in time"
            502, 520, 521, 523 -> "The site's server is down"
            525, 526 -> "A secure connection to the site failed"
            else -> "The site's server had an error"
        }
        return "$what (error $statusCode)"
    }

    fun title(kind: LoadFailureKind): String = when (kind) {
        LoadFailureKind.BLOCKED -> "FMHY's filters blocked part of this page"
        LoadFailureKind.OFFLINE -> "No internet connection"
        LoadFailureKind.REFUSED -> "The site refused to show this page"
        LoadFailureKind.TIMEOUT -> "The site took too long to answer"
        LoadFailureKind.BLANK -> "The page stayed blank"
        LoadFailureKind.SERVER,
        LoadFailureKind.UNKNOWN -> "This site isn't loading"
    }

    fun advice(kind: LoadFailureKind): String = when (kind) {
        LoadFailureKind.BLOCKED -> "Reload without blocking, or allow the blocked sites in the shield panel."
        LoadFailureKind.OFFLINE -> "Check Wi-Fi or mobile data, then retry."
        LoadFailureKind.REFUSED -> "Try desktop site, a private tab, or a mirror."
        LoadFailureKind.TIMEOUT -> "Retry, or try a mirror."
        LoadFailureKind.SERVER -> "The site may be down. Retry later or try a mirror."
        LoadFailureKind.BLANK -> "Reload without blocking, or try desktop site."
        LoadFailureKind.UNKNOWN -> "Retry, or try a mirror."
    }

    fun kindForError(errorCode: Int, online: Boolean): LoadFailureKind {
        if (!online && errorCode in setOf(
                ERROR_HOST_LOOKUP,
                ERROR_CONNECT,
                ERROR_IO,
                ERROR_TIMEOUT,
                ERROR_UNKNOWN,
            )
        ) {
            return LoadFailureKind.OFFLINE
        }
        return if (errorCode == ERROR_TIMEOUT) {
            LoadFailureKind.TIMEOUT
        } else {
            LoadFailureKind.UNKNOWN
        }
    }

    fun kindForHttp(status: Int): LoadFailureKind = when (status) {
        403, 429, 451 -> LoadFailureKind.REFUSED
        in 500..599 -> LoadFailureKind.SERVER
        else -> LoadFailureKind.UNKNOWN
    }
}

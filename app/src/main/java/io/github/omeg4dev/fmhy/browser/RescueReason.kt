package io.github.omeg4dev.fmhy.browser

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

    fun fromErrorCode(errorCode: Int, description: String = ""): String {
        if (description.contains("CLEARTEXT", ignoreCase = true)) return "Insecure connection blocked"
        return when (errorCode) {
            ERROR_HOST_LOOKUP -> "Server not found"
            ERROR_CONNECT -> "Connection failed"
            ERROR_TIMEOUT -> "Connection timed out"
            ERROR_FAILED_SSL_HANDSHAKE -> "Secure connection failed"
            ERROR_REDIRECT_LOOP -> "Too many redirects"
            ERROR_TOO_MANY_REQUESTS -> "Too many requests"
            ERROR_IO -> "Network error"
            else -> "Could not load the page"
        }
    }

    fun fromHttpStatus(statusCode: Int): String {
        return "Server error $statusCode"
    }
}

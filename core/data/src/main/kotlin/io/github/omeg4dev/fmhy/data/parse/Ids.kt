package io.github.omeg4dev.fmhy.data.parse

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

class IdAssigner {
    private val counts = mutableMapOf<Pair<String, String>, Int>()

    fun assign(normUrl: String, sectionId: String): String {
        val key = normUrl to sectionId
        val count = counts[key] ?: 0
        counts[key] = count + 1
        val base = Ids.entryId(normUrl, sectionId)
        return if (count == 0) base else "$base-${count + 1}"
    }
}

object Ids {
    private val REMOVE_PARAMS = setOf("ref", "fbclid")

    fun normalizeUrl(u: String): String {
        val hashIdx = u.indexOf('#')
        val fragment = if (hashIdx >= 0) u.substring(hashIdx + 1) else ""
        val withoutHash = if (hashIdx >= 0) u.substring(0, hashIdx) else u

        val queryIdx = withoutHash.indexOf('?')
        val query = if (queryIdx >= 0) withoutHash.substring(queryIdx + 1) else ""
        val withoutQuery = if (queryIdx >= 0) withoutHash.substring(0, queryIdx) else withoutHash

        val schemeIdx = withoutQuery.indexOf("://")
        val scheme: String
        val netlocAndPath: String
        if (schemeIdx >= 0) {
            scheme = withoutQuery.substring(0, schemeIdx).lowercase(Locale.ROOT)
            netlocAndPath = withoutQuery.substring(schemeIdx + 3)
        } else {
            val colonIdx = withoutQuery.indexOf(':')
            if (colonIdx >= 0) {
                scheme = withoutQuery.substring(0, colonIdx).lowercase(Locale.ROOT)
                val rest = withoutQuery.substring(colonIdx + 1)
                netlocAndPath = if (rest.startsWith("//")) rest.substring(2) else rest
            } else {
                scheme = ""
                netlocAndPath = withoutQuery
            }
        }

        var netloc: String
        var path: String
        val slashIdx = netlocAndPath.indexOf('/')
        if (slashIdx >= 0) {
            netloc = netlocAndPath.substring(0, slashIdx).lowercase(Locale.ROOT)
            path = netlocAndPath.substring(slashIdx)
        } else {
            netloc = netlocAndPath.lowercase(Locale.ROOT)
            path = ""
        }

        if (netloc.startsWith("www.")) {
            netloc = netloc.substring(4)
        }

        val colonPortIdx = netloc.lastIndexOf(':')
        if (colonPortIdx >= 0) {
            val host = netloc.substring(0, colonPortIdx)
            val port = netloc.substring(colonPortIdx + 1)
            if ((scheme == "http" && port == "80") || (scheme == "https" && port == "443")) {
                netloc = host
            }
        }

        if (path.endsWith("/")) {
            path = path.trimEnd('/')
        }

        val normalizedQuery = if (query.isNotEmpty()) {
            filterAndEncodeQuery(query)
        } else {
            ""
        }

        val sb = StringBuilder()
        if (scheme.isNotEmpty()) {
            sb.append(scheme).append("://")
        } else if (withoutQuery.startsWith("//")) {
            sb.append("//")
        }
        sb.append(netloc)
        sb.append(path)
        if (normalizedQuery.isNotEmpty()) {
            sb.append('?').append(normalizedQuery)
        }
        if (fragment.isNotEmpty()) {
            sb.append('#').append(fragment)
        }
        return sb.toString()
    }

    private fun filterAndEncodeQuery(query: String): String {
        val pairs = query.split('&').mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null
            val eqIdx = part.indexOf('=')
            if (eqIdx >= 0) {
                val rawKey = part.substring(0, eqIdx)
                val rawVal = part.substring(eqIdx + 1)
                val key = try { URLDecoder.decode(rawKey, "UTF-8") } catch (_: Exception) { rawKey }
                val value = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }
                key to value
            } else {
                val key = try { URLDecoder.decode(part, "UTF-8") } catch (_: Exception) { part }
                key to ""
            }
        }.filter { (k, _) ->
            k !in REMOVE_PARAMS && !k.startsWith("utm_")
        }

        if (pairs.isEmpty()) return ""
        return pairs.joinToString("&") { (k, v) ->
            val ek = URLEncoder.encode(k, "UTF-8").replace("+", "%20").replace("%20", "+")
            val ev = URLEncoder.encode(v, "UTF-8").replace("+", "%20").replace("%20", "+")
            "$ek=$ev"
        }
    }

    fun entryId(normUrl: String, sectionId: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$normUrl|$sectionId".toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(12)
        for (i in 0 until 6) {
            val b = digest[i].toInt() and 0xFF
            if (b < 16) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        return sb.toString()
    }
}

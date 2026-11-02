package io.github.omeg4dev.fmhy.work

import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

const val MULLVAD_IP_URL = "https://am.i.mullvad.net/json"

data class IpInfo(val ip: String, val country: String)

sealed interface IpResult {
    data class Value(val info: IpInfo) : IpResult
    data object Unavailable : IpResult
    data object Disabled : IpResult
}

object IpInfoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(jsonString: String): IpInfo? {
        return try {
            val element = json.parseToJsonElement(jsonString)
            val jsonObject = element as? JsonObject ?: return null

            val ipPrimitive = jsonObject["ip"] as? JsonPrimitive ?: return null
            val ip = ipPrimitive.contentOrNull?.trim()
            if (ip.isNullOrBlank()) return null

            val countryPrimitive = (jsonObject["country"] as? JsonPrimitive)?.contentOrNull?.trim()
            val country = if (countryPrimitive.isNullOrBlank()) "Unknown" else countryPrimitive

            IpInfo(ip = ip, country = country)
        } catch (_: Throwable) {
            null
        }
    }
}

class IpLookup(
    private val client: OkHttpClient,
    private val url: String = MULLVAD_IP_URL,
) {
    private val fetchClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(): IpInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        try {
            fetchClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                IpInfoParser.parse(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}

class IpInfoRepository(
    private val lookup: IpLookup,
    private val networkMonitor: NetworkMonitor,
    private val vpnMonitor: VpnMonitor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 5 * 60_000L,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: CacheEntry? = null

    private data class CacheEntry(
        val info: IpInfo,
        val timestamp: Long,
        val networkKind: NetworkKind,
        val vpnState: VpnState,
    )

    fun invalidate() {
        cached = null
    }

    suspend fun get(enabled: Boolean): IpResult {
        if (!enabled) return IpResult.Disabled

        return mutex.withLock {
            val currentKind = networkMonitor.kind.value
            val currentState = vpnMonitor.state.value

            val existing = cached
            if (existing != null) {
                if (existing.networkKind != currentKind || existing.vpnState != currentState) {
                    invalidate()
                } else {
                    val now = clock()
                    val age = now - existing.timestamp
                    if (age in 0 until ttlMs) {
                        return@withLock IpResult.Value(existing.info)
                    }
                }
            }

            val fetched = lookup.fetch()
            if (fetched != null) {
                cached = CacheEntry(
                    info = fetched,
                    timestamp = clock(),
                    networkKind = networkMonitor.kind.value,
                    vpnState = vpnMonitor.state.value,
                )
                IpResult.Value(fetched)
            } else {
                cached = null
                IpResult.Unavailable
            }
        }
    }
}

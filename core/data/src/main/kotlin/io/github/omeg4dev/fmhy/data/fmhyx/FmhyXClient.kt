package io.github.omeg4dev.fmhy.data.fmhyx

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * HTTP client for FMHY X account and sync endpoints with primary-to-mirror fallback.
 */
class FmhyXClient(
    http: OkHttpClient,
    baseUrls: List<String> = listOf("https://x.fmhy.bid", "https://d.fmhy.bid"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val bases: List<String>
    private val client: OkHttpClient

    init {
        require(baseUrls.isNotEmpty()) { "baseUrls must not be empty" }
        bases = baseUrls.take(2).map { it.trimEnd('/') }
        client = http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cache(null)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkAccount(token: String) {
        execute(
            token = token,
            buildRequest = { base ->
                Request.Builder()
                    .url("$base/api/account")
                    .get()
                    .build()
            },
            parseSuccess = ::parseOptionalJsonObject,
        )
    }

    suspend fun deleteAccount(token: String) {
        execute(
            token = token,
            allow401OnMirrorRetry = true,
            buildRequest = { base ->
                Request.Builder()
                    .url("$base/api/account")
                    .delete()
                    .build()
            },
            parseSuccess = ::parseOptionalJsonObject,
        )
    }

    suspend fun pull(token: String): RemoteAccount {
        return execute(
            token = token,
            buildRequest = { base ->
                Request.Builder()
                    .url("$base/api/v1/sync")
                    .get()
                    .build()
            },
            parseSuccess = ::parseSyncResponse,
        )
    }

    suspend fun push(
        token: String,
        favorites: List<JsonObject>,
        collections: List<JsonObject>,
    ): RemoteAccount {
        val bodyStr = buildPushBodyJson(favorites, collections)
        return execute(
            token = token,
            buildRequest = { base ->
                Request.Builder()
                    .url("$base/api/v1/sync")
                    .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            },
            parseSuccess = ::parseSyncResponse,
        )
    }

    suspend fun removeFavorite(token: String, item: JsonObject) {
        val payload = buildJsonObject {
            put("list", JsonPrimitive("favorites"))
            put("on", JsonPrimitive(false))
            put("item", item)
        }
        val bodyStr = json.encodeToString(JsonObject.serializer(), payload)
        execute(
            token = token,
            buildRequest = { base ->
                Request.Builder()
                    .url("$base/api/account/library")
                    .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            },
            parseSuccess = ::parseOptionalJsonObject,
        )
    }

    private suspend fun <T> execute(
        token: String,
        allow401OnMirrorRetry: Boolean = false,
        buildRequest: (baseUrl: String) -> Request,
        parseSuccess: (body: String) -> T,
    ): T = withContext(ioDispatcher) {
        if (!TOKEN_REGEX.matches(token)) {
            throw FmhyXException.Unauthorized()
        }
        var lastError: FmhyXException? = null
        for ((index, base) in bases.withIndex()) {
            currentCoroutineContext().ensureActive()
            val request = buildRequest(base).newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            try {
                val reply = client.newCall(request).await()
                when (val code = reply.code) {
                    in 200..299 -> return@withContext parseSuccess(reply.body)
                    401 -> {
                        if (allow401OnMirrorRetry && index > 0) {
                            return@withContext parseSuccess("")
                        }
                        throw FmhyXException.Unauthorized()
                    }
                    429 -> throw FmhyXException.RateLimited(
                        reply.retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0L }
                    )
                    413 -> throw FmhyXException.LimitExceeded(SyncLimit.BODY_SIZE)
                    in 500..599 -> lastError = FmhyXException.Server(code)
                    else -> throw FmhyXException.Server(code)
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                lastError = FmhyXException.Offline(e)
            }
        }
        throw lastError ?: FmhyXException.Offline(null)
    }

    private class HttpReply(val code: Int, val retryAfter: String?, val body: String)

    /** Runs the call asynchronously so that cancelling the coroutine aborts it, including the body read. */
    private suspend fun Call.await(): HttpReply = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use {
                        HttpReply(it.code, it.header("Retry-After"), if (it.isSuccessful) it.body.string() else "")
                    }
                    continuation.resume(reply)
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    companion object {
        private val TOKEN_REGEX = Regex("^[\\x21-\\x7E]{1,512}$")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        internal val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        internal fun buildPushBodyJson(
            favorites: List<JsonObject>,
            collections: List<JsonObject>,
        ): String {
            val root = buildJsonObject {
                if (favorites.isNotEmpty()) {
                    put("favorites", JsonArray(favorites))
                }
                if (collections.isNotEmpty()) {
                    put("collections", JsonArray(collections))
                }
            }
            return json.encodeToString(JsonObject.serializer(), root)
        }

        private fun parseOptionalJsonObject(body: String) {
            if (body.isBlank()) return
            val parsed = try {
                json.parseToJsonElement(body)
            } catch (e: SerializationException) {
                throw FmhyXException.BadResponse(e)
            } catch (e: IllegalArgumentException) {
                throw FmhyXException.BadResponse(e)
            }
            if (parsed !is JsonObject) {
                throw FmhyXException.BadResponse(IllegalArgumentException("Expected JSON object"))
            }
        }

        internal fun parseSyncResponse(body: String): RemoteAccount {
            if (body.isBlank()) {
                throw FmhyXException.BadResponse(IllegalArgumentException("Empty sync response"))
            }
            val root = try {
                json.parseToJsonElement(body) as? JsonObject
            } catch (e: SerializationException) {
                throw FmhyXException.BadResponse(e)
            } catch (e: IllegalArgumentException) {
                throw FmhyXException.BadResponse(e)
            } ?: throw FmhyXException.BadResponse(IllegalArgumentException("Sync response is not a JSON object"))

            val favoritesArr = root["favorites"] as? JsonArray
                ?: throw FmhyXException.BadResponse(IllegalArgumentException("Missing favorites array"))
            val dislikesArr = root["dislikes"] as? JsonArray
                ?: throw FmhyXException.BadResponse(IllegalArgumentException("Missing dislikes array"))
            val collectionsArr = root["collections"] as? JsonArray
                ?: throw FmhyXException.BadResponse(IllegalArgumentException("Missing collections array"))

            val favorites = favoritesArr.map { validateItem(it) }
            val dislikes = dislikesArr.map { validateItem(it) }
            val collections = collectionsArr.map { validateCollection(it) }
            return RemoteAccount(favorites = favorites, dislikes = dislikes, collections = collections)
        }

        private fun validateItem(el: JsonElement): JsonObject {
            val obj = el as? JsonObject
                ?: throw FmhyXException.BadResponse(IllegalArgumentException("Item is not a JSON object"))
            val u = obj["u"] as? JsonPrimitive
            if (u == null || !u.isString || u.content.isEmpty()) {
                throw FmhyXException.BadResponse(IllegalArgumentException("Item missing string u"))
            }
            validateOptionalLong(obj["at"], "at")
            return obj
        }

        private fun validateCollection(el: JsonElement): JsonObject {
            val obj = el as? JsonObject
                ?: throw FmhyXException.BadResponse(IllegalArgumentException("Collection is not a JSON object"))
            val id = obj["id"] as? JsonPrimitive
            if (id == null || !id.isString || id.content.isEmpty()) {
                throw FmhyXException.BadResponse(IllegalArgumentException("Collection missing string id"))
            }
            validateOptionalLong(obj["updated"], "updated")
            val deletedEl = obj["deleted"]
            val deleted = if (deletedEl == null || deletedEl is JsonNull) {
                false
            } else {
                val prim = deletedEl as? JsonPrimitive
                if (prim == null || prim.isString || prim.booleanOrNull == null) {
                    throw FmhyXException.BadResponse(IllegalArgumentException("Invalid collection deleted flag"))
                }
                prim.booleanOrNull == true
            }
            if (!deleted) {
                val name = obj["name"] as? JsonPrimitive
                if (name == null || !name.isString) {
                    throw FmhyXException.BadResponse(IllegalArgumentException("Collection missing string name"))
                }
                val items = obj["items"] as? JsonArray
                    ?: throw FmhyXException.BadResponse(IllegalArgumentException("Collection missing items array"))
                for (item in items) {
                    validateItem(item)
                }
            } else {
                val itemsEl = obj["items"]
                if (itemsEl != null && itemsEl !is JsonNull) {
                    val items = itemsEl as? JsonArray
                        ?: throw FmhyXException.BadResponse(IllegalArgumentException("Invalid deleted collection items"))
                    for (item in items) {
                        validateItem(item)
                    }
                }
            }
            return obj
        }

        private fun validateOptionalLong(el: JsonElement?, fieldName: String) {
            if (el == null || el is JsonNull) return
            val prim = el as? JsonPrimitive
            if (prim == null || prim.isString || prim.longOrNull == null) {
                throw FmhyXException.BadResponse(IllegalArgumentException("Invalid $fieldName field"))
            }
        }
    }
}

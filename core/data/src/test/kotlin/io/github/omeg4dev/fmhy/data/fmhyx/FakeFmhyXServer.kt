package io.github.omeg4dev.fmhy.data.fmhyx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * In-memory fake FMHY X backend matching observed live API validation, merge, and error rules.
 */
internal class FakeFmhyXServer(
    val validToken: String = "fmhyx_test_token_123",
    var strictValidation: Boolean = true,
    var allowPreseededUnknownFields: Boolean = true,
) : Dispatcher() {

    private val json = Json { ignoreUnknownKeys = true }

    val favorites = LinkedHashMap<String, JsonObject>()
    val dislikes = LinkedHashMap<String, JsonObject>()
    val collections = LinkedHashMap<String, JsonObject>()
    val requests = mutableListOf<CapturedRequest>()

    @Volatile
    var accountDeleted: Boolean = false

    @Volatile
    var nextHook: ((RecordedRequest) -> MockResponse?)? = null

    private val queue = ArrayDeque<MockResponse>()

    data class CapturedRequest(
        val method: String,
        val path: String,
        val authHeader: String?,
        val body: String,
    )

    fun enqueueOverride(response: MockResponse) {
        synchronized(this) {
            queue.addLast(response)
        }
    }

    fun seedFavorite(item: JsonObject) {
        val u = item.str("u") ?: error("missing u")
        favorites[u] = item
    }

    fun seedDislike(item: JsonObject) {
        val u = item.str("u") ?: error("missing u")
        dislikes[u] = item
    }

    fun seedCollection(col: JsonObject) {
        val id = col.str("id") ?: error("missing id")
        collections[id] = col
    }

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val bodyStr = request.body?.utf8().orEmpty()
        requests.add(
            CapturedRequest(
                method = request.method,
                path = request.target,
                authHeader = request.headers["Authorization"],
                body = bodyStr,
            )
        )

        nextHook?.invoke(request)?.let { return it }
        if (queue.isNotEmpty()) {
            return queue.removeFirst()
        }

        val auth = request.headers["Authorization"]
        if (accountDeleted || auth != "Bearer $validToken") {
            return jsonResponse(401, """{"error":"unauthorized"}""")
        }

        return when {
            request.method == "GET" && request.target == "/api/account" ->
                jsonResponse(200, """{"created_at":1732270000,"feed_url":"https://x.fmhy.bid/rss/feed.xml"}""")

            request.method == "DELETE" && request.target == "/api/account" -> {
                accountDeleted = true
                favorites.clear()
                dislikes.clear()
                collections.clear()
                jsonResponse(200, """{"ok":true}""")
            }

            request.method == "GET" && request.target == "/api/v1/sync" ->
                jsonResponse(200, buildAccountJson())

            request.method == "POST" && request.target == "/api/account/library" ->
                handleLibraryPost(bodyStr)

            request.method == "POST" && request.target == "/api/v1/sync" ->
                handleSyncPost(bodyStr)

            else -> jsonResponse(404, """{"error":"not found"}""")
        }
    }

    private fun handleLibraryPost(bodyStr: String): MockResponse {
        val obj = runCatching { json.parseToJsonElement(bodyStr) as? JsonObject }.getOrNull()
            ?: return jsonResponse(400, """{"error":"bad json"}""")
        val list = (obj["list"] as? JsonPrimitive)?.content
        val on = (obj["on"] as? JsonPrimitive)?.booleanOrNull
        val item = obj["item"] as? JsonObject
        val u = item?.str("u")
        if (list == "favorites" && on == false && u != null) {
            // Live API rule 1: removes favorite with NO marker kept
            favorites.remove(u)
            return jsonResponse(200, """{"favorites":${JsonArray(favorites.values.toList())},"dislikes":${JsonArray(dislikes.values.toList())}}""")
        }
        return jsonResponse(400, """{"error":"unsupported library call"}""")
    }

    private fun handleSyncPost(bodyStr: String): MockResponse {
        if (bodyStr.toByteArray(Charsets.UTF_8).size > 20 * 1024 * 1024) {
            return jsonResponse(413, """{"error":"too large"}""")
        }
        val root = runCatching { json.parseToJsonElement(bodyStr) as? JsonObject }.getOrNull()
            ?: return jsonResponse(400, """{"error":"bad json"}""")

        // Validate collections FIRST (Live API rule 5: invalid collection fails whole request with 400)
        val incomingCollections = (root["collections"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val validatedCols = mutableListOf<JsonObject>()
        for (col in incomingCollections) {
            val validated = validateAndNormalizeCollection(col)
                ?: return jsonResponse(400, """{"error":"that collection can't be saved"}""")
            validatedCols.add(validated)
        }

        // Validate favorites (Live API rule 4: unknown field -> 400; bad field -> silently dropped)
        val incomingFavorites = (root["favorites"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val acceptedFavorites = mutableListOf<JsonObject>()
        for (fav in incomingFavorites) {
            if (strictValidation && hasUnknownItemField(fav, favorites[fav.str("u")])) {
                return jsonResponse(400, """{"error":"unknown item field"}""")
            }
            val normalized = validateAndNormalizeFavorite(fav)
            if (normalized != null) {
                acceptedFavorites.add(normalized)
            }
        }

        // Projected limits check
        val projFavs = LinkedHashMap(favorites)
        val projDislikes = LinkedHashMap(dislikes)
        for (fav in acceptedFavorites) {
            val u = fav.str("u")!!
            val at = fav.longOrZero("at")
            val existingFav = projFavs[u]
            val existingDis = projDislikes[u]
            if (existingDis != null) {
                if (at > existingDis.longOrZero("at")) {
                    projDislikes.remove(u)
                    projFavs[u] = fav
                }
            } else if (existingFav == null || at >= existingFav.longOrZero("at")) {
                projFavs[u] = fav
            }
        }
        if (projFavs.size + projDislikes.size > 5000) {
            return jsonResponse(400, """{"error":"too many saved items"}""")
        }

        val projCols = LinkedHashMap(collections)
        for (col in validatedCols) {
            val id = col.str("id")!!
            val prev = projCols[id]
            val prevUpdated = prev?.longOrZero("updated") ?: Long.MIN_VALUE
            if (prev == null || col.longOrZero("updated") > prevUpdated) {
                projCols[id] = col
            }
        }
        val liveColCount = projCols.values.count { !it.boolOrFalse("deleted") }
        if (liveColCount > 50) {
            return jsonResponse(400, """{"error":"too many collections"}""")
        }

        favorites.clear()
        favorites.putAll(projFavs)
        dislikes.clear()
        dislikes.putAll(projDislikes)
        collections.clear()
        collections.putAll(projCols)

        return jsonResponse(200, buildAccountJson())
    }

    private fun hasUnknownItemField(item: JsonObject, existing: JsonObject?): Boolean {
        val preseededKeys = if (allowPreseededUnknownFields && existing != null) existing.keys else emptySet()
        return item.keys.any { it !in ALLOWED_ITEM_KEYS && it !in preseededKeys }
    }

    private fun validateAndNormalizeFavorite(item: JsonObject): JsonObject? {
        val u = item.str("u") ?: return null
        if (!isValidHttpUrl(u) || u.length > 2048) return null
        val t = item.str("t")
        if (t.isNullOrEmpty() || t.length > 300) return null
        val d = item.str("d")
        if (d != null && d.length > 600) return null
        val p = item.str("p")
        if (p != null && p.length > 200) return null
        val h = item.str("h")
        if (h != null && h.length > 200) return null

        val map = LinkedHashMap<String, JsonElement>(item)
        // Live API rule 4: pushed item without p comes back with p:''
        if ("p" !in map) {
            map["p"] = JsonPrimitive("")
        }
        return JsonObject(map)
    }

    private fun validateAndNormalizeCollection(col: JsonObject): JsonObject? {
        val id = col.str("id") ?: return null
        if (id.isEmpty() || id.length > 36) return null
        val existing = collections[id]
        val preseededColKeys = if (allowPreseededUnknownFields && existing != null) existing.keys else emptySet()
        if (strictValidation && col.keys.any { it !in ALLOWED_COLLECTION_KEYS && it !in preseededColKeys }) {
            return null
        }

        val deleted = col.boolOrFalse("deleted")
        val updated = col.longOrZero("updated")
        if (deleted) {
            // Live API rule 2: deleted collections come back as {id, name:'', items:[], updated, deleted:true}
            return JsonObject(
                mapOf(
                    "id" to JsonPrimitive(id),
                    "name" to JsonPrimitive(""),
                    "items" to JsonArray(emptyList()),
                    "updated" to JsonPrimitive(updated),
                    "deleted" to JsonPrimitive(true),
                )
            )
        }

        val name = col.str("name") ?: return null
        if (name.isEmpty() || name.length > 60) return null
        val itemsArr = col["items"] as? JsonArray ?: return null
        if (itemsArr.size > 500) return null

        val seenUrls = HashSet<String>()
        val normalizedItems = mutableListOf<JsonObject>()
        val existingItemsByUrl = existing?.collectionItems()?.associateBy { it.str("u").orEmpty() }.orEmpty()
        for (el in itemsArr) {
            val item = el as? JsonObject ?: return null
            if (strictValidation && hasUnknownItemField(item, existingItemsByUrl[item.str("u").orEmpty()])) {
                return null
            }
            val normItem = validateAndNormalizeFavorite(item) ?: return null
            val u = normItem.str("u")!!
            if (!seenUrls.add(u)) return null
            normalizedItems.add(normItem)
        }

        val map = LinkedHashMap<String, JsonElement>()
        // Live API rule 3: share is kept if omitted when replacing collection
        if (existing != null && "share" in existing && "share" !in col) {
            existing["share"]?.let { map["share"] = it }
        }
        for ((k, v) in col) {
            map[k] = v
        }
        map["id"] = JsonPrimitive(id)
        map["name"] = JsonPrimitive(name)
        map["items"] = JsonArray(normalizedItems)
        map["updated"] = JsonPrimitive(updated)
        map.remove("deleted")
        return JsonObject(map)
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    private fun buildAccountJson(): String {
        val root = buildJsonObject {
            put("created_at", JsonPrimitive(1732270000L))
            put("feed_url", JsonPrimitive("https://x.fmhy.bid/rss/feed.xml"))
            put("favorites", JsonArray(favorites.values.toList()))
            put("dislikes", JsonArray(dislikes.values.toList()))
            put("collections", JsonArray(collections.values.toList()))
            put("subscriptions", JsonArray(emptyList()))
            put("activity", JsonObject(emptyMap()))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun jsonResponse(code: Int, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .setHeader("Content-Type", "application/json")
            .body(body)
            .build()

    companion object {
        private val ALLOWED_ITEM_KEYS = setOf("u", "t", "p", "d", "h", "at")
        private val ALLOWED_COLLECTION_KEYS = setOf("id", "name", "items", "updated", "share", "deleted")
    }
}

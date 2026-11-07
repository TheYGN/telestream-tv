package com.telestream.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** The TeleStream server's HTTP API: the same addresses the Kodi add-on uses, read and written as JSON. */
object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val server: String get() = Prefs.server
    private val tv: String get() = Prefs.tvId

    /** Pictures the server keeps itself come as /thumb/... or /tgphoto/...; the player needs the full address. */
    fun abs(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("/") -> server + url
        else -> url
    }

    fun streamUrl(path: String) = server + path

    private suspend fun get(path: String, params: Map<String, String?> = emptyMap()): String = withContext(Dispatchers.IO) {
        val builder = (server + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (!v.isNullOrBlank()) builder.addQueryParameter(k, v) }
        client.newCall(Request.Builder().url(builder.build()).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    private suspend fun send(method: String, path: String, body: JSONObject? = null): Unit = withContext(Dispatchers.IO) {
        val payload = (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(server + path).method(method, if (method == "DELETE") null else payload).build()
        client.newCall(request).execute().use { resp -> if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}") }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun home() = parseHome(JSONObject(get("/api/home", mapOf("tv" to tv))))

    suspend fun movies(page: Int = 1, genre: String? = null, q: String? = null, size: Int? = null, sort: String? = null) =
        parsePage(JSONObject(get("/api/movies", mapOf("page" to "$page", "genre" to genre, "q" to q, "tv" to tv,
            "size" to size?.toString(), "sort" to sort))), ::parseMovie)

    suspend fun shows(page: Int = 1, genre: String? = null, q: String? = null, size: Int? = null, sort: String? = null) =
        parsePage(JSONObject(get("/api/shows", mapOf("page" to "$page", "genre" to genre, "q" to q,
            "size" to size?.toString(), "sort" to sort))), ::parseShow)

    suspend fun other(page: Int = 1, q: String? = null) =
        parsePage(JSONObject(get("/api/other", mapOf("page" to "$page", "q" to q))), ::parseFile)

    suspend fun show(key: String) = parseShowDetail(JSONObject(get("/api/show/" + enc(key), mapOf("tv" to tv))))

    suspend fun continueWatching(): List<ContinueItem> =
        JSONArray(get("/api/tv/$tv/continue")).mapObjects(::parseContinue)

    /** This TV's list: movies come whole, shows as summaries; each tagged with its "list_kind". */
    suspend fun myList(): List<Any> = JSONArray(get("/api/tv/$tv/list")).mapObjects {
        if (it.optString("list_kind") == "show") parseShow(it) else parseMovie(it)
    }

    suspend fun addToList(kind: String, key: String) = send("POST", "/api/tv/$tv/list", JSONObject().put("kind", kind).put("key", key))
    suspend fun removeFromList(kind: String, key: String) = send("DELETE", "/api/tv/$tv/list/$kind/${enc(key)}")

    /** The YouTube id of a title's trailer, or null. kind: "movie" or "tv". */
    suspend fun upcoming(): List<Upcoming> =
        JSONObject(get("/api/upcoming")).optJSONArray("items")?.mapObjects(::parseUpcoming) ?: emptyList()

    suspend fun other(page: Int = 1, q: String? = null, size: Int) =
        parsePage(JSONObject(get("/api/other", mapOf("page" to "$page", "q" to q, "size" to "$size"))), ::parseFile)

    suspend fun trailer(kind: String, tmdbId: Int): String? =
        JSONObject(get("/api/trailer/$kind/$tmdbId")).let { if (it.isNull("key")) null else it.optString("key").ifBlank { null } }

    suspend fun cast(kind: String, tmdbId: Int): List<Actor> =
        JSONObject(get("/api/cast/$kind/$tmdbId")).optJSONArray("cast")?.mapObjects(::parseActor) ?: emptyList()

    suspend fun next(path: String) = parseNext(JSONObject(get("/api/next", mapOf("path" to path))))
    suspend fun skip(path: String) = parseSkip(JSONObject(get("/api/skip", mapOf("path" to path))))

    suspend fun progress(path: String, position: Double, total: Double) =
        send("POST", "/api/tv/$tv/progress", JSONObject().put("path", path).put("position", position).put("total", total))

    suspend fun hello() = send("POST", "/api/tv/hello", JSONObject().put("id", tv).put("name", Prefs.tvName.ifBlank { "Android TV" }))

    /** Waits up to [seconds] for the library page to send something to this TV. 404 means: say hello again. */
    suspend fun waitForPlay(seconds: Int): PlayRequest? {
        val o = JSONObject(get("/api/tv/$tv/wait", mapOf("timeout" to "$seconds")))
        val path = o.optString("path")
        if (path.isBlank()) return null
        return PlayRequest(path, o.optString("title"), o.optDouble("start", 0.0).let { if (it.isNaN()) 0.0 else it })
    }
}

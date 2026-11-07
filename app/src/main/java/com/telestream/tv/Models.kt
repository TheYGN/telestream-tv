package com.telestream.tv

import org.json.JSONArray
import org.json.JSONObject

data class MediaFile(val path: String, val name: String, val size: Long, val group: String, val duration: Double)
data class Progress(val position: Double, val total: Double, val done: Boolean) {
    val fraction: Float get() = if (total > 0) (position / total).toFloat().coerceIn(0f, 1f) else 0f
}

data class Movie(
    val key: String, val title: String, val originalTitle: String, val year: Int?, val overview: String,
    val genres: List<String>, val rating: Double?, val poster: String?, val fanart: String?,
    val files: List<MediaFile>, val progress: Progress?,
)

data class Show(
    val key: String, val title: String, val originalTitle: String, val year: Int?, val overview: String,
    val genres: List<String>, val rating: Double?, val poster: String?, val fanart: String?, val seasonCount: Int,
)

data class Episode(
    val number: Int, val name: String, val overview: String, val still: String?, val aired: String,
    val files: List<MediaFile>, val progress: Progress?,
)

data class Season(val number: Int, val poster: String?, val episodes: List<Episode>)
data class ShowDetail(val show: Show, val seasons: List<Season>)

data class ContinueItem(
    val kind: String, val key: String, val title: String, val label: String, val poster: String?,
    val fanart: String?, val still: String?, val path: String, val position: Double, val total: Double,
    val upNext: Boolean,
)

data class Page<T>(val items: List<T>, val page: Int, val pages: Int, val total: Int)
data class Home(val movies: Int, val shows: Int, val other: Int, val movieGenres: List<String>, val showGenres: List<String>)
data class NextEpisode(val path: String, val show: String, val season: Int, val episode: Int, val name: String, val still: String?, val poster: String?)
data class SkipTimes(val intro: Pair<Double, Double>?, val recap: Pair<Double, Double>?, val outro: Double?)

/** Something to play: a file on the server, what to call it, and where to start (seconds). */
data class PlayRequest(val path: String, val title: String, val start: Double = 0.0)

// ---------- parsing ----------

private fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")
private fun JSONObject.strOrNull(name: String): String? = str(name).ifBlank { null }
private fun JSONObject.intOrNull(name: String): Int? =
    if (isNull(name)) null else opt(name).toString().take(4).toIntOrNull()
private fun JSONObject.doubleOrNull(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() && it > 0 }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }

fun parseFile(o: JSONObject) = MediaFile(
    path = o.str("path"), name = o.str("name"), size = o.optLong("size", 0),
    group = o.str("group"), duration = o.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it },
)

fun parseProgress(o: JSONObject?): Progress? = o?.let {
    Progress(it.optDouble("position", 0.0), it.optDouble("total", 0.0), it.optBoolean("done", false))
}

fun parseMovie(o: JSONObject) = Movie(
    key = o.str("key"), title = o.str("title"), originalTitle = o.str("original_title"), year = o.intOrNull("year"),
    overview = o.str("overview"), genres = o.optJSONArray("genres").strings(), rating = o.doubleOrNull("rating"),
    poster = Api.abs(o.strOrNull("poster")), fanart = Api.abs(o.strOrNull("fanart")),
    files = o.optJSONArray("files")?.mapObjects(::parseFile) ?: emptyList(),
    progress = parseProgress(o.optJSONObject("progress")),
)

fun parseShow(o: JSONObject) = Show(
    key = o.str("key"), title = o.str("title"), originalTitle = o.str("original_title"), year = o.intOrNull("year"),
    overview = o.str("overview"), genres = o.optJSONArray("genres").strings(), rating = o.doubleOrNull("rating"),
    poster = Api.abs(o.strOrNull("poster")), fanart = Api.abs(o.strOrNull("fanart")),
    seasonCount = o.optInt("season_count", o.optJSONObject("seasons")?.length() ?: 0),
)

fun parseShowDetail(o: JSONObject): ShowDetail {
    val seasonsObj = o.optJSONObject("seasons") ?: JSONObject()
    val seasons = seasonsObj.keys().asSequence().mapNotNull { sk ->
        val s = seasonsObj.getJSONObject(sk)
        val eps = s.optJSONObject("episodes") ?: JSONObject()
        val episodes = eps.keys().asSequence().mapNotNull { ek ->
            val e = eps.getJSONObject(ek)
            ek.toIntOrNull()?.let { n ->
                Episode(
                    number = n, name = e.str("name"), overview = e.str("overview"), still = Api.abs(e.strOrNull("still")),
                    aired = e.str("aired"), files = e.optJSONArray("files")?.mapObjects(::parseFile) ?: emptyList(),
                    progress = parseProgress(e.optJSONObject("progress")),
                )
            }
        }.sortedBy { it.number }.toList()
        sk.toIntOrNull()?.let { Season(it, Api.abs(s.strOrNull("poster")), episodes) }
    }.sortedBy { it.number }.toList()
    return ShowDetail(parseShow(o), seasons)
}

fun parseContinue(o: JSONObject) = ContinueItem(
    kind = o.str("kind"), key = o.str("key"), title = o.str("title"), label = o.str("label"),
    poster = Api.abs(o.strOrNull("poster")), fanart = Api.abs(o.strOrNull("fanart")), still = Api.abs(o.strOrNull("still")),
    path = o.str("path"), position = o.optDouble("position", 0.0), total = o.optDouble("total", 0.0),
    upNext = o.optBoolean("up_next", false),
)

fun parseHome(o: JSONObject): Home {
    fun genres(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONArray(it).getString(0) } } ?: emptyList()
    return Home(o.optInt("movies"), o.optInt("shows"), o.optInt("other"), genres("movie_genres"), genres("show_genres"))
}

fun <T> parsePage(o: JSONObject, item: (JSONObject) -> T) = Page(
    items = o.getJSONArray("items").mapObjects(item), page = o.optInt("page", 1),
    pages = o.optInt("pages", 1), total = o.optInt("total", 0),
)

private fun span(o: JSONObject, name: String): Pair<Double, Double>? =
    o.optJSONArray(name)?.takeIf { it.length() >= 2 && !it.isNull(0) && !it.isNull(1) }?.let { it.getDouble(0) to it.getDouble(1) }

fun parseSkip(o: JSONObject) = SkipTimes(
    intro = span(o, "intro"), recap = span(o, "recap"),
    outro = o.optJSONArray("outro")?.takeIf { it.length() > 0 && !it.isNull(0) }?.getDouble(0),
)

fun parseNext(o: JSONObject): NextEpisode? = o.optString("path").takeIf { it.isNotBlank() }?.let {
    NextEpisode(
        path = it, show = o.optString("show"), season = o.optInt("season"), episode = o.optInt("episode"),
        name = if (o.isNull("name")) "" else o.optString("name"), still = Api.abs(o.optString("still").ifBlank { null }),
        poster = Api.abs(o.optString("poster").ifBlank { null }),
    )
}

fun fileLabel(f: MediaFile): String =
    "${f.name}  (${"%.1f".format(f.size / 1024.0 / 1024 / 1024)} GB · ${f.group})"

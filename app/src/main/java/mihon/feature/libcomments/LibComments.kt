package mihon.feature.libcomments

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/** Sites of the "Lib" group (MangaLib, HentaiLib, SlashLib) share one comments API. */
private val LIB_HOSTS = listOf("mangalib", "hentailib", "slashlib", "yaoilib")

// The extension's default API domain; its client adds the user's token only for requests to it.
private const val API_DOMAIN = "https://api.cdnlibs.org"

fun Source?.isLibSource(): Boolean {
    val source = this as? HttpSource ?: return false
    val host = runCatching { source.baseUrl.lowercase() }.getOrDefault("")
    return LIB_HOSTS.any { it in host || it in source.name.lowercase() }
}

enum class CommentSort(val apiValue: String) {
    Popular("votes_up"),
    New("id"),
}

data class LibComment(
    val id: Long,
    val userName: String,
    val avatarUrl: String?,
    val html: String,
    val likes: Int,
    val dislikes: Int,
    val createdAtMillis: Long,
    val level: Int,
)

data class RootComment(
    val comment: LibComment,
    val replies: List<LibComment>,
)

data class CommentsPage(
    val comments: List<RootComment>,
    val hasNextPage: Boolean,
)

/**
 * Loads comments through the extension's own client, so its token and Site-Id headers are reused.
 */
class LibCommentsRepository(private val source: HttpSource) {

    private val json = Json { ignoreUnknownKeys = true }

    /** [pageNumber] is the 1-based page of the chapter the comments belong to. */
    suspend fun getPageComments(
        chapterUrl: String,
        pageNumber: Int,
        sort: CommentSort,
        listPage: Int,
    ): CommentsPage {
        val url = "$API_DOMAIN/api/comments".toHttpUrl().newBuilder()
            .addQueryParameter("post_id", getChapterId(chapterUrl).toString())
            .addQueryParameter("post_type", "chapter")
            .addQueryParameter("sort_by", sort.apiValue)
            .addQueryParameter("sort_type", "desc")
            .addQueryParameter("page", listPage.toString())
            .addQueryParameter("post_page", pageNumber.toString())
            .build()
        val response = get<CommentsResponse>(url.toString())
        val repliesByRoot = response.data.replies.groupBy { it.rootId }
        return CommentsPage(
            comments = response.data.root.map { root ->
                RootComment(
                    comment = root.toLibComment(),
                    replies = repliesByRoot[root.id].orEmpty().map { it.toLibComment() },
                )
            },
            hasNextPage = response.meta.hasNextPage,
        )
    }

    private suspend fun getChapterId(chapterUrl: String): Long {
        chapterIds[chapterUrl]?.let { return it }
        return get<ChapterResponse>("$API_DOMAIN/api/manga$chapterUrl").data.id
            .also { chapterIds[chapterUrl] = it }
    }

    private suspend inline fun <reified T> get(url: String): T {
        val body = source.client.newCall(GET(url, source.headers)).awaitSuccess().use { it.body.string() }
        return json.decodeFromString(body)
    }

    private companion object {
        val chapterIds = ConcurrentHashMap<String, Long>()
    }
}

@Serializable
private class ChapterResponse(val data: ChapterDto) {
    @Serializable
    class ChapterDto(val id: Long)
}

@Serializable
private class CommentsResponse(val data: Data, val meta: Meta) {
    @Serializable
    class Data(val root: List<CommentDto> = emptyList(), val replies: List<CommentDto> = emptyList())

    @Serializable
    class Meta(@SerialName("has_next_page") val hasNextPage: Boolean = false)
}

@Serializable
private class CommentDto(
    val id: Long,
    @SerialName("root_id") val rootId: Long? = null,
    @SerialName("comment_level") val level: Int = 0,
    val comment: String = "",
    @SerialName("created_at_ts") val createdAt: Long = 0,
    val user: User? = null,
    val votes: Votes? = null,
) {
    @Serializable
    class User(val username: String = "", val avatar: Avatar? = null)

    @Serializable
    class Avatar(val url: String? = null)

    @Serializable
    class Votes(val up: Int = 0, val down: Int = 0)

    fun toLibComment() = LibComment(
        id = id,
        userName = user?.username.orEmpty(),
        avatarUrl = user?.avatar?.url,
        html = comment,
        likes = votes?.up ?: 0,
        dislikes = votes?.down ?: 0,
        createdAtMillis = createdAt,
        level = level,
    )
}

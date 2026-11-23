package mihon.feature.libcomments

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource

/** Sites of the "Lib" group (MangaLib, HentaiLib, SlashLib) share one comments API. */
private val LIB_HOSTS = listOf("mangalib", "hentailib", "slashlib", "yaoilib")

fun Source?.isLibSource(): Boolean {
    val source = this as? HttpSource ?: return false
    val host = runCatching { source.baseUrl.lowercase() }.getOrDefault("")
    return LIB_HOSTS.any { it in host || it in source.name.lowercase() }
}

enum class CommentSort { Popular, New }

data class LibComment(
    val id: Long,
    val userName: String,
    val avatarUrl: String?,
    val text: String,
    val likes: Int,
    val createdAtMillis: Long,
    val replyCount: Int,
)

data class CommentsPage(
    val comments: List<LibComment>,
    val hasNextPage: Boolean,
    val total: Int?,
)

/**
 * Loads comments through the extension's own client, so its token and Site-Id headers are reused.
 */
class LibCommentsRepository(private val source: HttpSource) {

    /** [pageNumber] is the 1-based page of the chapter the comments belong to. */
    suspend fun getPageComments(
        chapterUrl: String,
        pageNumber: Int,
        sort: CommentSort,
        listPage: Int,
    ): CommentsPage {
        throw UnsupportedOperationException("API комментариев ещё не подключён")
    }

    suspend fun getReplies(commentId: Long): List<LibComment> {
        throw UnsupportedOperationException("API комментариев ещё не подключён")
    }
}

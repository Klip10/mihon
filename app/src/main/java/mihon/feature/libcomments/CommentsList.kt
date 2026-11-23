package mihon.feature.libcomments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import eu.kanade.presentation.util.relativeTimeSpanString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Comments of one chapter page, with paging, sort and lazily loaded replies. */
@Stable
class PageCommentsState(
    val repository: LibCommentsRepository,
    private val chapterUrl: String,
    val pageNumber: Int,
    private val scope: CoroutineScope,
) {
    var sort by mutableStateOf(CommentSort.Popular)
        private set
    val comments = mutableStateListOf<RootComment>()
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasNextPage by mutableStateOf(true)
        private set

    val expanded = mutableStateMapOf<Long, Boolean>()

    private var listPage = 0
    private var loadJob: Job? = null

    fun changeSort(newSort: CommentSort) {
        if (newSort == sort) return
        sort = newSort
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        isLoading = false
        comments.clear()
        expanded.clear()
        listPage = 0
        hasNextPage = true
        error = null
        loadMore()
    }

    fun loadMore() {
        if (isLoading || !hasNextPage) return
        isLoading = true
        loadJob = scope.launch {
            try {
                val result = repository.getPageComments(chapterUrl, pageNumber, sort, listPage + 1)
                listPage++
                comments.addAll(result.comments.filter { new -> comments.none { it.comment.id == new.comment.id } })
                hasNextPage = result.hasNextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                hasNextPage = false
            }
            isLoading = false
        }
    }

    fun toggleReplies(commentId: Long) {
        if (expanded.remove(commentId) == null) expanded[commentId] = true
    }
}

@Composable
fun rememberPageCommentsState(
    repository: LibCommentsRepository,
    chapterUrl: String,
    pageNumber: Int,
): PageCommentsState {
    val scope = rememberCoroutineScope()
    val state = remember(chapterUrl, pageNumber) {
        PageCommentsState(repository, chapterUrl, pageNumber, scope)
    }
    LaunchedEffect(state) { state.reload() }
    return state
}

@Composable
fun CommentsList(
    state: PageCommentsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        commentsItems(state)
    }
}

private fun LazyListScope.commentsItems(state: PageCommentsState) {
    item(key = "header") {
        CommentsHeader(state)
    }
    items(state.comments, key = { it.comment.id }) { root ->
        RootCommentItem(root, state)
    }
    item(key = "footer") {
        LaunchedEffect(state.comments.size) {
            if (state.comments.isNotEmpty()) state.loadMore()
        }
        CommentsFooter(state, showLoadMore = false)
    }
}

/**
 * Non-lazy comments for places that already scroll, like under a page. More are loaded on request, since a
 * footer that loads by being composed would fetch every page at once here.
 */
@Composable
fun CommentsColumn(state: PageCommentsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        CommentsHeader(state)
        state.comments.forEach { root ->
            key(root.comment.id) {
                RootCommentItem(root, state)
            }
        }
        CommentsFooter(state, showLoadMore = true)
    }
}

@Composable
private fun RootCommentItem(root: RootComment, state: PageCommentsState) {
    Column {
        CommentItem(root.comment, state.repository.imageHeaders)
        if (root.replies.isNotEmpty()) {
            val isExpanded = state.expanded[root.comment.id] == true
            TextButton(
                onClick = { state.toggleReplies(root.comment.id) },
                modifier = Modifier.padding(start = 52.dp),
            ) {
                Text(if (isExpanded) "Скрыть ответы" else "Показать ответы (${root.replies.size})")
            }
            if (isExpanded) {
                root.replies.forEach { reply ->
                    CommentItem(
                        comment = reply,
                        imageHeaders = state.repository.imageHeaders,
                        modifier = Modifier.padding(start = (24 * reply.level.coerceIn(1, 3)).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentsFooter(state: PageCommentsState, showLoadMore: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = state::reload) { Text("Повторить") }
            }
            state.comments.isEmpty() -> Text(
                "Комментариев пока нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            showLoadMore && state.hasNextPage -> TextButton(onClick = state::loadMore) {
                Text("Ещё комментарии")
            }
        }
    }
}

@Composable
private fun CommentsHeader(state: PageCommentsState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Комментарии · стр. ${state.pageNumber}",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sort == CommentSort.Popular,
                onClick = { state.changeSort(CommentSort.Popular) },
                label = { Text("Популярные") },
            )
            FilterChip(
                selected = state.sort == CommentSort.New,
                onClick = { state.changeSort(CommentSort.New) },
                label = { Text("Новые") },
            )
        }
    }
}

@Composable
private fun CommentItem(comment: LibComment, imageHeaders: Headers, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val avatarRequest = remember(comment.avatarUrl) {
        ImageRequest.Builder(context)
            .data(comment.avatarUrl)
            .httpHeaders(imageHeaders.toNetworkHeaders())
            .build()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = avatarRequest,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTimeSpanString(comment.createdAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CommentText(comment.html, imageHeaders)
            Text(
                text = "▲ ${comment.likes}   ▼ ${comment.dislikes}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val SPOILER_SCHEME = "spoiler:"

// Relative image paths in comments point at the site's image host.
private const val IMAGE_BASE_URL = "https://cover.imglib.info/"

private class CommentContent(val text: AnnotatedString, val images: List<String>)

/**
 * Hides the site's spoiler blocks behind a tappable label until the reader opens them, and shows the
 * comment's images under its text, since text can't hold them.
 */
@Composable
private fun CommentText(html: String, imageHeaders: Headers) {
    var revealed by remember(html) { mutableStateOf(emptySet<Int>()) }
    val uriHandler = LocalUriHandler.current
    val hiddenStyle = SpanStyle(
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        background = MaterialTheme.colorScheme.tertiaryContainer,
    )
    val content = remember(html, revealed, hiddenStyle) {
        val body = Jsoup.parseBodyFragment(html, IMAGE_BASE_URL).body()
        body.select(".spoiler-node")
            .filter { spoiler -> spoiler.parents().none { it.hasClass("spoiler-node") } }
            .forEachIndexed { i, spoiler ->
                if (i in revealed) {
                    spoiler.unwrap()
                } else {
                    val title = spoiler.attr("data-spoiler-text").ifBlank { "Спойлер" }
                    spoiler.replaceWith(
                        Element("a").attr("href", "$SPOILER_SCHEME$i").text("\u00A0$title\u00A0"),
                    )
                }
            }
        val images = body.select("img").mapNotNull { img ->
            img.absUrl("src").takeIf { it.isNotBlank() }.also { img.remove() }
        }
        body.select("p").filter { !it.hasText() && it.children().isEmpty() }.forEach { it.remove() }
        val text = AnnotatedString.fromHtml(
            htmlString = body.html(),
            linkStyles = TextLinkStyles(style = hiddenStyle),
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url ?: return@fromHtml
                val spoiler = url.removePrefix(SPOILER_SCHEME).toIntOrNull()
                if (url.startsWith(SPOILER_SCHEME) && spoiler != null) {
                    revealed = revealed + spoiler
                } else {
                    uriHandler.openUri(url)
                }
            },
        )
        CommentContent(text.subSequence(0, text.text.trimEnd().length), images)
    }
    if (content.text.isNotEmpty()) {
        Text(text = content.text, style = MaterialTheme.typography.bodyMedium)
    }
    val context = LocalContext.current
    content.images.forEach { url ->
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .httpHeaders(imageHeaders.toNetworkHeaders())
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(top = 4.dp)
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

private fun Headers.toNetworkHeaders(): NetworkHeaders =
    NetworkHeaders.Builder()
        .apply { forEach { (name, value) -> set(name, value) } }
        .build()

/** Pure black themes would leave comments on black, which reads poorly under pages; lift them to a dark grey. */
@Composable
fun commentsBackgroundColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.surface.luminance() < 0.02f) Color(0xFF1E1F22) else colors.surfaceContainerHigh
}

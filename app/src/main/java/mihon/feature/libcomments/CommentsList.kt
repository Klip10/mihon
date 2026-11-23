package mihon.feature.libcomments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.relativeTimeSpanString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Comments of one chapter page, with paging, sort and lazily loaded replies. */
@Stable
class PageCommentsState(
    private val repository: LibCommentsRepository,
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

/** Header, comments and footer as lazy items, so they can sit inside an existing list. */
fun LazyListScope.commentsItems(state: PageCommentsState) {
    item(key = "header") {
        CommentsHeader(state)
    }
    items(state.comments, key = { it.comment.id }) { root ->
        Column {
            CommentItem(root.comment)
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
                            modifier = Modifier.padding(start = (24 * reply.level.coerceIn(1, 3)).dp),
                        )
                    }
                }
            }
        }
    }
    item(key = "footer") {
        LaunchedEffect(state.comments.size) {
            if (state.comments.isNotEmpty()) state.loadMore()
        }
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
private fun CommentItem(comment: LibComment, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
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
            Text(
                text = remember(comment.html) { AnnotatedString.fromHtml(comment.html) },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "▲ ${comment.likes}   ▼ ${comment.dislikes}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

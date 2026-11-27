package mihon.feature.libcomments

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AdaptiveSheet

@Composable
fun CommentsSheet(
    repository: LibCommentsRepository,
    chapterUrl: String,
    pageNumber: Int,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        val state = rememberPageCommentsState(repository, chapterUrl, pageNumber)
        Surface(color = commentsBackgroundColor()) {
            CommentsList(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
            )
        }
    }
}

@Composable
fun MangaCommentsSheet(
    repository: LibCommentsRepository,
    mangaUrl: String,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        val state = rememberMangaCommentsState(repository, mangaUrl)
        Surface(color = commentsBackgroundColor()) {
            CommentsList(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
            )
        }
    }
}

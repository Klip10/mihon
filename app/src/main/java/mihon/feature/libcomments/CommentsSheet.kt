package mihon.feature.libcomments

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
        CommentsList(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        )
    }
}

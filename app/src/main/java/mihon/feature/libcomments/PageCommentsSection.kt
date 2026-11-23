package mihon.feature.libcomments

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/** Which page the section under it shows comments for; null hides the section. */
class PageCommentsTarget(val chapterUrl: String, val pageNumber: Int)

fun ReaderPage.commentsTarget(): PageCommentsTarget? {
    val url = chapter.chapter.url.takeIf { it.isNotBlank() } ?: return null
    return PageCommentsTarget(url, index + 1)
}

/**
 * A view showing the comments of a page under it. With [collapsible] they open on a tap, otherwise they load
 * once [PageCommentsView.activate] is called, e.g. when the reader scrolls to them.
 */
class PageCommentsView(
    context: Context,
    repository: LibCommentsRepository,
    collapsible: Boolean,
) {
    val target: MutableState<PageCommentsTarget?> = mutableStateOf(null)
    private var active by mutableStateOf(false)

    val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            val current = target.value
            if (current != null) {
                TachiyomiTheme {
                    PageCommentsSection(
                        repository = repository,
                        target = current,
                        expanded = active,
                        onToggle = if (collapsible) ({ active = !active }) else null,
                    )
                }
            }
        }
    }

    fun bind(newTarget: PageCommentsTarget?) {
        if (newTarget?.chapterUrl != target.value?.chapterUrl || newTarget?.pageNumber != target.value?.pageNumber) {
            active = false
        }
        target.value = newTarget
    }

    fun activate() {
        active = true
    }

    /** Whether [event] lands on the comments, so the reader doesn't treat it as a tap on the page. */
    fun contains(event: MotionEvent): Boolean = view.containsTouch(event)
}

fun View.containsTouch(event: MotionEvent): Boolean {
    val rect = Rect()
    return isShown && getGlobalVisibleRect(rect) && rect.contains(event.rawX.toInt(), event.rawY.toInt())
}

@Composable
private fun PageCommentsSection(
    repository: LibCommentsRepository,
    target: PageCommentsTarget,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = commentsBackgroundColor()) {
        Column {
            when {
                onToggle != null -> TextButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (expanded) "Скрыть комментарии" else "Комментарии к странице ${target.pageNumber}")
                }
                !expanded -> Text(
                    text = "Комментарии к странице ${target.pageNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (expanded) {
                val state = rememberPageCommentsState(repository, target.chapterUrl, target.pageNumber)
                CommentsColumn(state)
            }
        }
    }
}

package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import mihon.feature.libcomments.LibCommentsRepository
import mihon.feature.libcomments.PageCommentsView
import mihon.feature.libcomments.commentsTarget

/**
 * Holds a [PagerPageHolder] at the height of the pager, with the page's comments below it, reached by scrolling
 * down. Comments load the first time the reader scrolls.
 */
@SuppressLint("ViewConstructor")
class PagerPageCommentsContainer(
    context: Context,
    val holder: PagerPageHolder,
    repository: LibCommentsRepository,
) : NestedScrollView(context), ViewPagerAdapter.PositionableView {

    override val item
        get() = holder.item

    private val comments = PageCommentsView(context, repository, collapsible = false)

    init {
        comments.bind(holder.page.commentsTarget())
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(holder, LinearLayout.LayoutParams(MATCH_PARENT, resources.displayMetrics.heightPixels))
            addView(comments.view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        addView(content, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setOnScrollChangeListener { _: NestedScrollView, _: Int, scrollY: Int, _: Int, _: Int ->
            if (scrollY > 0) comments.activate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0 && holder.layoutParams.height != h) {
            holder.layoutParams.height = h
            post { holder.requestLayout() }
        }
    }

    fun isOnComments(event: MotionEvent): Boolean = comments.contains(event)
}

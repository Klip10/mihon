package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import mihon.feature.libcomments.LibCommentsRepository
import mihon.feature.libcomments.PageCommentsView
import mihon.feature.libcomments.commentsTarget
import kotlin.math.abs

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

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f

    // The page view asks parents not to intercept so it can pan, which would keep this view from ever scrolling
    // while a drag starts on the page. The request still goes up to the pager for horizontal pans, and this view
    // decides itself in onInterceptTouchEvent when a drag belongs to it.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                super.onInterceptTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - lastY
                lastY = ev.y
                val totalDx = abs(ev.x - downX)
                val totalDy = abs(ev.y - downY)
                if (totalDy <= touchSlop || totalDy <= totalDx) return false
                // Once comments show, any vertical drag moves the whole view; before that, only a drag past the
                // bottom of the page does, so a tall or zoomed page still pans first and then flows into comments.
                val takeOver = scrollY > 0 || (dy < 0 && !holder.canPanDown())
                if (!takeOver) return false
                // Restart the drag from here so the scroll doesn't jump by what the page already panned.
                val restart = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_DOWN }
                super.onInterceptTouchEvent(restart)
                restart.recycle()
                return true
            }
            else -> {
                super.onInterceptTouchEvent(ev)
                return false
            }
        }
    }
}

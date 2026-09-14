package com.thefactor1.taskskiller.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView

/**
 * A ScrollView tuned for the D-pad.
 *
 * The stock ScrollView scrolls the least it can to bring the next focusable
 * view on screen, so focus rides along the bottom edge, and when the move goes
 * through requestChildFocus it jumps rather than animating. It also lets a
 * focused child consume up/down first, which is how a selectable TextView
 * trapped the Setup screen halfway down.
 *
 * Here up/down are handled before any child sees them: focus moves to the next
 * box and the content glides so that box sits in the middle of the screen.
 * When nothing focusable is left in that direction the content still scrolls,
 * so the end of the page is always reachable.
 */
class FocusScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var glide: ValueAnimator? = null

    /** Set while we move focus ourselves, so ScrollView's own jump-to-child is skipped. */
    private var movingFocus = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val next = FocusFinder.getInstance().findNextFocus(this, findFocus(), direction)
        if (next != null) {
            movingFocus = true
            try {
                next.requestFocus(direction)
            } finally {
                movingFocus = false
            }
            glideTo(targetFor(next))
            return true
        }

        // Nothing focusable left that way: keep the page moving.
        val step = height / 2 * if (direction == View.FOCUS_DOWN) 1 else -1
        val target = (scrollY + step).coerceIn(0, maxScroll())
        if (target != scrollY) {
            glideTo(target)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int =
        if (movingFocus) 0 else super.computeScrollDeltaToGetChildRectOnScreen(rect)

    override fun onDetachedFromWindow() {
        glide?.cancel()
        super.onDetachedFromWindow()
    }

    /** Scroll position that centres [view]; a box taller than the screen shows its top instead. */
    private fun targetFor(view: View): Int {
        val rect = Rect()
        view.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(view, rect)
        val target = if (rect.height() > height * TALL_FRACTION) {
            rect.top - (height * TOP_MARGIN_FRACTION).toInt()
        } else {
            rect.centerY() - height / 2
        }
        return target.coerceIn(0, maxScroll())
    }

    private fun maxScroll(): Int {
        val child = getChildAt(0) ?: return 0
        return (child.height + paddingTop + paddingBottom - height).coerceAtLeast(0)
    }

    /** Restarting from wherever the previous glide got to keeps a held key smooth. */
    private fun glideTo(target: Int) {
        glide?.cancel()
        if (target == scrollY) return
        glide = ValueAnimator.ofInt(scrollY, target).apply {
            duration = GLIDE_MS
            interpolator = DecelerateInterpolator(GLIDE_EASE)
            addUpdateListener { scrollTo(scrollX, it.animatedValue as Int) }
            start()
        }
    }

    private companion object {
        const val GLIDE_MS = 280L
        const val GLIDE_EASE = 1.6f
        const val TALL_FRACTION = 0.8f
        const val TOP_MARGIN_FRACTION = 0.08f
    }
}

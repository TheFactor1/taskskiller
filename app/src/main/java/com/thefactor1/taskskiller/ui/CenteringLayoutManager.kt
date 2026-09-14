package com.thefactor1.taskskiller.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Keeps the focused row near the middle of the list and glides there. The
 * default scrolls the least it can, which parks focus on the bottom edge and
 * makes a long list feel like it jumps a row at a time.
 */
class CenteringLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        val dy = child.top + rect.centerY() - parent.height / 2
        if (dy == 0) return false
        if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy, GLIDE_INTERPOLATOR, GLIDE_MS)
        return true
    }

    private companion object {
        const val GLIDE_MS = 260
        val GLIDE_INTERPOLATOR = DecelerateInterpolator(1.6f)
    }
}

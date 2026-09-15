package com.thefactor1.taskskiller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * A list that fades its content out at an edge while there is more to scroll
 * that way, so a row cut off by the edge reads as "more above/below" rather
 * than as a clipped box. The platform's requiresFadingEdge had no visible
 * effect on these lists (see [dispatchDraw]), so the fade is drawn here: the
 * rows are drawn into a layer and a gradient erases them towards the edge.
 */
class FadingEdgeRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private val fadePx = FADE_DP * resources.displayMetrics.density
    private val erase = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private var topFade: Shader? = null
    private var bottomFade: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topFade = LinearGradient(0f, 0f, 0f, fadePx, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        bottomFade = LinearGradient(0f, h - fadePx, 0f, h.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
    }

    // dispatchDraw, not draw: with overScrollMode="never" RecyclerView marks
    // itself willNotDraw, so the framework skips draw() entirely and only
    // dispatches to the rows. That is also why requiresFadingEdge, which
    // lives in View.draw(), never showed on these lists.
    override fun dispatchDraw(canvas: Canvas) {
        // At the very top (or bottom) nothing is cut off, so nothing fades:
        // the first and last rows always show at full strength.
        val fadeTop = canScrollVertically(-1)
        val fadeBottom = canScrollVertically(1)
        if (!fadeTop && !fadeBottom) {
            super.dispatchDraw(canvas)
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        super.dispatchDraw(canvas)
        if (fadeTop) {
            erase.shader = topFade
            canvas.drawRect(0f, 0f, w, fadePx, erase)
        }
        if (fadeBottom) {
            erase.shader = bottomFade
            canvas.drawRect(0f, h - fadePx, w, h, erase)
        }
        canvas.restoreToCount(layer)
    }

    private companion object {
        const val FADE_DP = 40f
    }
}

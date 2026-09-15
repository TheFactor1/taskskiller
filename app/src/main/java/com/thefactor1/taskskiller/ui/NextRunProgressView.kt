package com.thefactor1.taskskiller.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.thefactor1.taskskiller.R

/**
 * How far a rule is through its interval: a thin bar that sweeps up to the
 * current fraction when the list is shown, then glides on as time passes. Its
 * head breathes softly while the schedule is live, so a glance tells a running
 * countdown from a paused one (an empty track, no glow).
 */
class NextRunProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barHeight = BAR_DP * resources.displayMetrics.density
    private val glowRadius = GLOW_DP * resources.displayMetrics.density

    private val deep = ContextCompat.getColor(context, R.color.accent_deep)
    private val accent = ContextCompat.getColor(context, R.color.accent)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hairline)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_bright)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

    /** The fraction currently drawn; animates towards the one last set. */
    private var shown = 0f
    private var live = false
    private var sweep: ValueAnimator? = null

    /** 0..1, the head's breathing phase. */
    private var breath = 0f
    private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            breath = it.animatedValue as Float
            invalidate()
        }
    }

    /**
     * [fromZero] replays the sweep from empty (a fresh bind of the list);
     * otherwise the bar glides on from where it is (the periodic tick).
     */
    fun setProgress(fraction: Float, live: Boolean, fromZero: Boolean) {
        val target = if (live) fraction.coerceIn(0f, 1f) else 0f
        this.live = live
        sweep?.cancel()
        val from = if (fromZero) 0f else shown
        if (from == target) {
            shown = target
            invalidate()
        } else {
            sweep = ValueAnimator.ofFloat(from, target).apply {
                duration = if (fromZero) SWEEP_MS else STEP_MS
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    shown = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        updatePulse()
    }

    private fun updatePulse() {
        // "Remove animations" in the TV's accessibility settings turns the
        // breathing off; the bar itself still shows the right fraction.
        val wanted = live && isAttachedToWindow && animatorsEnabled()
        if (wanted && !pulse.isStarted) pulse.start()
        if (!wanted && pulse.isStarted) {
            pulse.cancel()
            breath = 0f
        }
    }

    private fun animatorsEnabled() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        // end(), not cancel(): a recycled row that comes back without a rebind
        // should show where the sweep was heading, not where it stopped.
        sweep?.end()
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, deep, accent, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val r = barHeight / 2f
        canvas.drawRoundRect(0f, cy - r, w, cy + r, r, r, trackPaint)
        if (shown <= 0f) return

        val end = (w * shown).coerceAtLeast(barHeight)
        canvas.drawRoundRect(0f, cy - r, end, cy + r, r, r, fillPaint)
        if (!live) return

        // The glow reaches a few dp past the bar's ends; the row layout lets
        // it draw outside this view (clipChildren="false").
        val headX = end - r
        glowPaint.alpha = (GLOW_ALPHA_MIN + (GLOW_ALPHA_MAX - GLOW_ALPHA_MIN) * breath).toInt()
        canvas.drawCircle(headX, cy, glowRadius * (0.75f + 0.25f * breath), glowPaint)
        canvas.drawCircle(headX, cy, r * 1.35f, headPaint)
    }

    private companion object {
        const val BAR_DP = 4f
        const val GLOW_DP = 6f
        const val SWEEP_MS = 900L
        const val STEP_MS = 600L
        const val PULSE_MS = 1_400L
        const val GLOW_ALPHA_MIN = 40
        const val GLOW_ALPHA_MAX = 130
    }
}

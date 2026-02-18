package com.pomo.timer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class TimerCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dpToPx(10f)
        color = Color.parseColor("#1AF5A623")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dpToPx(10f)
        color = Color.parseColor("#F5A623")
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dpToPx(16f)
        color = Color.parseColor("#28F5A623")
        maskFilter = BlurMaskFilter(dpToPx(12f), BlurMaskFilter.Blur.NORMAL)
    }

    private var animatedProgress: Float = 1f
    private var animator: ValueAnimator? = null
    private val arcRect = RectF()
    private var accentColor = Color.parseColor("#F5A623")

    fun setProgress(value: Float, animate: Boolean = true) {
        val clamped = value.coerceIn(0f, 1f)
        if (animate) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(animatedProgress, clamped).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animatedProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animator?.cancel()
            animatedProgress = clamped
            invalidate()
        }
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        progressPaint.color = color
        glowPaint.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
        trackPaint.color = Color.argb(26, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val stroke = dpToPx(10f)
        val pad = stroke + dpToPx(8f)
        arcRect.set(pad, pad, w - pad, h - pad)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)
        val sweep = 360f * animatedProgress
        if (sweep > 0f) {
            canvas.drawArc(arcRect, -90f, sweep, false, glowPaint)
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}

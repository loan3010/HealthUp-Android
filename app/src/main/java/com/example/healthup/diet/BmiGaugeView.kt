package com.example.healthup.diet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.healthup.R
import kotlin.math.min

class BmiGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val arcRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = resources.displayMetrics.scaledDensity * 11f
    }

    private var displayedBmi = 0f
    private var targetBmi = 22f
    private var animator: ValueAnimator? = null

    private val minBmi = 15f
    private val maxBmi = 35f
    private val startAngle = 150f
    private val sweepAngle = 240f

    private val segments = listOf(
        Segment(15f, 18.5f, R.color.primary_tint_2),
        Segment(18.5f, 23f, R.color.brand_primary),
        Segment(23f, 25f, R.color.yellow_progress),
        Segment(25f, 30f, R.color.status_amber),
        Segment(30f, 35f, R.color.action_error)
    )

    private data class Segment(val from: Float, val to: Float, val colorRes: Int)

    fun setBmi(bmi: Double, animate: Boolean = true) {
        val clamped = bmi.toFloat().coerceIn(minBmi, maxBmi)
        targetBmi = clamped
        animator?.cancel()
        if (!animate) {
            displayedBmi = clamped
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(displayedBmi, clamped).apply {
            duration = 900L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayedBmi = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = min(width, height) * 0.09f
        trackPaint.strokeWidth = stroke
        segmentPaint.strokeWidth = stroke
        val radius = min(width, height) / 2f - stroke
        val cx = width / 2f
        val cy = height / 2f + height * 0.08f
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.color = ContextCompat.getColor(context, R.color.track_gray)
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, trackPaint)

        segments.forEach { segment ->
            val fromAngle = bmiToAngle(segment.from)
            val toAngle = bmiToAngle(segment.to)
            segmentPaint.color = ContextCompat.getColor(context, segment.colorRes)
            canvas.drawArc(arcRect, fromAngle, toAngle - fromAngle, false, segmentPaint)
        }

        val pointerAngle = Math.toRadians(bmiToAngle(displayedBmi).toDouble())
        val pointerLen = radius - stroke * 0.4f
        val px = cx + pointerLen * kotlin.math.cos(pointerAngle).toFloat()
        val py = cy + pointerLen * kotlin.math.sin(pointerAngle).toFloat()
        pointerPaint.color = ContextCompat.getColor(context, R.color.text_primary)
        canvas.drawCircle(px, py, stroke * 0.35f, pointerPaint)

        canvas.drawText("15", arcRect.left + stroke, cy + stroke * 2.2f, labelPaint)
        canvas.drawText("35", arcRect.right - stroke, cy + stroke * 2.2f, labelPaint)
    }

    private fun bmiToAngle(bmi: Float): Float {
        val ratio = ((bmi - minBmi) / (maxBmi - minBmi)).coerceIn(0f, 1f)
        return startAngle + sweepAngle * ratio
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

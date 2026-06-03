package ru.lobotino.walktraveller.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.ui.model.TrackAnimationMode
import ru.lobotino.walktraveller.ui.model.WelcomeTrackGeometry

/**
 * Draws a stylized walking track that grows over time, colored with the app's
 * rating palette. Three modes vary the extra hints drawn on top of the track.
 */
class TrackRecordingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var mode: TrackAnimationMode = TrackAnimationMode.RECORD
        set(value) {
            field = value
            invalidate()
        }

    private var progress: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val keyRect = RectF()

    private val whiteColor = ContextCompat.getColor(context, android.R.color.white)
    private val inactiveKeyColor = ContextCompat.getColor(context, R.color.rating_unknown)

    private val ratingColors: Map<SegmentRating, Int> = mapOf(
        SegmentRating.BADLY to ContextCompat.getColor(context, R.color.rating_badly),
        SegmentRating.NORMAL to ContextCompat.getColor(context, R.color.rating_normal),
        SegmentRating.GOOD to ContextCompat.getColor(context, R.color.rating_good),
        SegmentRating.PERFECT to ContextCompat.getColor(context, R.color.rating_perfect),
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3500L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun startAnimation() {
        if (!animator.isStarted) animator.start()
    }

    fun stopAnimation() {
        animator.cancel()
        progress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawTrack(canvas, w, h)
        drawHead(canvas, w, h)
        when (mode) {
            TrackAnimationMode.RATE -> drawLegend(canvas, w, h)
            TrackAnimationMode.VOLUME -> drawVolumeKeys(canvas, w, h)
            TrackAnimationMode.RECORD -> Unit
        }
    }

    private fun drawTrack(canvas: Canvas, w: Float, h: Float) {
        trackPaint.strokeWidth = h * 0.02f
        val visible = WelcomeTrackGeometry.visibleSegmentCount(progress)
        val points = WelcomeTrackGeometry.trackPoints
        for (i in 0 until visible) {
            val from = points[i]
            val to = points[i + 1]
            trackPaint.color = ratingColors.getValue(WelcomeTrackGeometry.ratingForSegment(i))
            canvas.drawLine(from.x * w, from.y * h, to.x * w, to.y * h, trackPaint)
        }
    }

    private fun drawHead(canvas: Canvas, w: Float, h: Float) {
        val head = WelcomeTrackGeometry.headPosition(progress)
        val cx = head.x * w
        val cy = head.y * h
        val r = h * 0.03f
        fillPaint.color = whiteColor
        canvas.drawCircle(cx, cy, r * 1.45f, fillPaint)
        fillPaint.color = ratingColors.getValue(SegmentRating.PERFECT)
        canvas.drawCircle(cx, cy, r, fillPaint)
    }

    private fun drawLegend(canvas: Canvas, w: Float, h: Float) {
        val order = listOf(
            SegmentRating.BADLY,
            SegmentRating.NORMAL,
            SegmentRating.GOOD,
            SegmentRating.PERFECT,
        )
        val r = h * 0.022f
        val cy = h * 0.95f
        var cx = w * 0.12f
        val step = w * 0.08f
        for (rating in order) {
            fillPaint.color = ratingColors.getValue(rating)
            canvas.drawCircle(cx, cy, r, fillPaint)
            cx += step
        }
    }

    private fun drawVolumeKeys(canvas: Canvas, w: Float, h: Float) {
        val keyW = w * 0.11f
        val keyH = h * 0.16f
        val left = w * 0.84f
        val gap = h * 0.04f
        val upTop = h * 0.26f
        val downTop = upTop + keyH + gap
        val radius = keyW * 0.25f
        val upActive = (progress * 4f).toInt() % 2 == 0

        keyRect.set(left, upTop, left + keyW, upTop + keyH)
        fillPaint.color = if (upActive) ratingColors.getValue(SegmentRating.PERFECT) else inactiveKeyColor
        canvas.drawRoundRect(keyRect, radius, radius, fillPaint)

        keyRect.set(left, downTop, left + keyW, downTop + keyH)
        fillPaint.color = if (!upActive) ratingColors.getValue(SegmentRating.BADLY) else inactiveKeyColor
        canvas.drawRoundRect(keyRect, radius, radius, fillPaint)
    }
}

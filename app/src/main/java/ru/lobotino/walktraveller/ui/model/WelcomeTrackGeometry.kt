package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.SegmentRating

/** A normalized (0f..1f) point on the onboarding track. Android-free for JVM tests. */
data class WelcomePoint(val x: Float, val y: Float)

/**
 * Pure geometry and rating-color logic for the onboarding track animation.
 * Coordinates are normalized 0f..1f and scaled to view bounds by the View.
 */
object WelcomeTrackGeometry {

    /** Rating palette, "worse -> better". Never includes NONE. */
    private val palette = listOf(
        SegmentRating.BADLY,
        SegmentRating.NORMAL,
        SegmentRating.GOOD,
        SegmentRating.PERFECT,
    )

    /** Stylized winding walk. Many points => many segments (dense colored line). */
    val trackPoints: List<WelcomePoint> = listOf(
        WelcomePoint(0.08f, 0.88f),
        WelcomePoint(0.18f, 0.84f),
        WelcomePoint(0.26f, 0.74f),
        WelcomePoint(0.22f, 0.62f),
        WelcomePoint(0.30f, 0.52f),
        WelcomePoint(0.42f, 0.54f),
        WelcomePoint(0.48f, 0.44f),
        WelcomePoint(0.44f, 0.32f),
        WelcomePoint(0.54f, 0.26f),
        WelcomePoint(0.66f, 0.30f),
        WelcomePoint(0.70f, 0.42f),
        WelcomePoint(0.80f, 0.46f),
        WelcomePoint(0.86f, 0.36f),
        WelcomePoint(0.92f, 0.24f),
    )

    val segmentCount: Int = trackPoints.size - 1

    /** Color rating of the given 0-based segment. Cycles through the 4-color palette. */
    fun ratingForSegment(segmentIndex: Int): SegmentRating {
        require(segmentIndex >= 0) { "segmentIndex must be >= 0" }
        return palette[segmentIndex % palette.size]
    }

    /** Segments visible at the given progress (0f -> 0, 1f -> segmentCount). */
    fun visibleSegmentCount(progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return (clamped * segmentCount).toInt()
    }

    /** Position of the moving "me" dot along the polyline at the given progress. */
    fun headPosition(progress: Float): WelcomePoint {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) return trackPoints.first()
        if (clamped >= 1f) return trackPoints.last()
        val exact = clamped * segmentCount
        val index = exact.toInt()
        val frac = exact - index
        val from = trackPoints[index]
        val to = trackPoints[index + 1]
        return WelcomePoint(
            x = from.x + (to.x - from.x) * frac,
            y = from.y + (to.y - from.y) * frac,
        )
    }
}

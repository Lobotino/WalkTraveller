# Rating Segments Gradient Blending — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace abrupt color changes between rating-segments on the map with a smooth ~12 m gradient transition, driven by MapLibre `line-gradient`.

**Architecture:** One `GeoJsonSource` + one `LineLayer` per visible rating-path (each with `lineMetrics(true)` and its own `Expression.interpolate(line-progress, ...)` gradient stops). Gradient stops are computed in a pure Kotlin builder, fully unit-tested. Saved common paths and the active recording path keep their single-source/single-layer structure; recording uses the same per-path gradient pipeline.

**Tech Stack:** Kotlin · MapLibre Android SDK 11.8.0 · JUnit 4 · MockK 1.13.10

**Spec:** `docs/superpowers/specs/2026-05-30-rating-segments-gradient-blending-design.md`

---

## File Structure

**Create:**

- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt`
  - `data class GradientStop(progress: Float, rating: SegmentRating)`
  - `object PathGradientStopsBuilder { fun build(path: MapRatingPath, blendMeters: Float): List<GradientStop> }`
  - `internal fun haversineMeters(a: MapPoint, b: MapPoint): Double`
  - Pure Kotlin, no Android / MapLibre dependencies.

- `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt`
  - Unit tests for builder + haversine.

**Modify:**

- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt`
  - Replace `ratingPathToFeatures(path): List<Feature>` with `ratingPathToFeature(path): Feature?`.
  - Replace `segmentsToFeatures(pathId, segments): List<Feature>` with `segmentsToFeature(pathId, segments): Feature?`.
  - Drop `PROPERTY_RATING` constant (no longer needed).

- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`
  - Constructor adds `blendMeters: Float = 12f`.
  - Internal rating storage: `savedRatingPaths: LinkedHashMap<Long, MapRatingPath>`.
  - Per-path source/layer lifecycle for rating paths (`addLayerBelow(LAYER_SAVED_COMMON)`).
  - Current path becomes a single combined Feature with its own gradient layer.
  - Remove `ratingColorExpression` / `buildRatingColorExpression` / `ratingLineLayer`; add `addRatingPathLayer(pathId, path)` and `updateRatingPathGradient(pathId, path)`.

---

## Task 1: Haversine helper + tests

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt` (skeleton with `haversineMeters` only)
- Create: `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt`

- [ ] **Step 1: Create test file with three failing cases**

Write `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapPoint

class PathGradientStopsBuilderTest {

    @Test
    fun `haversineMeters returns zero for identical points`() {
        // given
        val point = MapPoint(latitude = 55.7558, longitude = 37.6173)

        // when
        val result = haversineMeters(point, point)

        // then
        assertEquals(0.0, result, 1e-6)
    }

    @Test
    fun `haversineMeters one degree of longitude at equator is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 0.0, longitude = 0.0)
        val b = MapPoint(latitude = 0.0, longitude = 1.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }

    @Test
    fun `haversineMeters one degree of latitude is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 55.0, longitude = 37.0)
        val b = MapPoint(latitude = 56.0, longitude = 37.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail to compile**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PathGradientStopsBuilderTest"`
Expected: compilation error — `haversineMeters` unresolved.

- [ ] **Step 3: Create `PathGradientStopsBuilder.kt` with `haversineMeters` only**

Write `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import ru.lobotino.walktraveller.model.map.MapPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

internal fun haversineMeters(a: MapPoint, b: MapPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(h), sqrt(1 - h))
    return EARTH_RADIUS_METERS * c
}
```

- [ ] **Step 4: Run tests, confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PathGradientStopsBuilderTest"`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt \
        app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt
git commit -m "Add haversineMeters helper for gradient distance math"
```

---

## Task 2: `PathGradientStopsBuilder.build()` core algorithm + tests

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt` (add `GradientStop` + `PathGradientStopsBuilder` object)
- Modify: `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt` (add 9 builder tests)

- [ ] **Step 1: Add the 9 builder tests**

Append to the existing test file (after the haversine tests, inside the same class):

```kotlin
    @Test
    fun `build returns empty list for path with no segments`() {
        // given
        val path = MapRatingPath(pathId = 1L, pathSegments = emptyList())

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        assertEquals(emptyList<GradientStop>(), result)
    }

    @Test
    fun `build returns endpoints only for single rating run`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        assertEquals(
            listOf(
                GradientStop(0f, SegmentRating.GOOD),
                GradientStop(1f, SegmentRating.GOOD),
            ),
            result,
        )
    }

    @Test
    fun `build emits four stops around a single mid-path junction`() {
        // given — two equal-length connected segments, ~111 m each, junction at midpoint
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val expectedHalf = (blend / 2f) / totalLen.toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(4, result.size)
        assertEquals(0f, result[0].progress, 1e-6f)
        assertEquals(SegmentRating.GOOD, result[0].rating)
        assertEquals(0.5f - expectedHalf, result[1].progress, 1e-4f)
        assertEquals(SegmentRating.GOOD, result[1].rating)
        assertEquals(0.5f + expectedHalf, result[2].progress, 1e-4f)
        assertEquals(SegmentRating.BADLY, result[2].rating)
        assertEquals(1f, result[3].progress, 1e-6f)
        assertEquals(SegmentRating.BADLY, result[3].rating)
    }

    @Test
    fun `build clamps halfPrev when previous run shorter than half blend`() {
        // given — short GOOD run (~11 m), long BADLY run (~111 m); blend=12 → halfPrev clamped to 5.5 m
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0001, SegmentRating.GOOD),
                segment(0.0, 0.0001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0001))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0011))
        val expectedHalfPrev = (prevLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedHalfNext = (blend / 2f) / totalLen.toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(4, result.size)
        assertEquals(expectedJunction - expectedHalfPrev, result[1].progress, 1e-4f)
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps halfNext when next run shorter than half blend`() {
        // given — long GOOD run (~111 m), short BADLY run (~11 m); blend=12 → halfNext clamped to 5.5 m
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val nextLen = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = prevLen + nextLen
        val expectedHalfNext = (nextLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps adjacent halves at midpoint between two close junctions`() {
        // given — GOOD then BADLY (~11 m) then GOOD; blend zones between junctions overlap if not clamped
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
                segment(0.0, 0.0011, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )
        val blend = 100f
        val len1 = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val len2 = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val j1 = (len1 / totalLen).toFloat()
        val j2 = ((len1 + len2) / totalLen).toFloat()
        val midHalf = ((j2 - j1) / 2f)

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then — six stops total: (0,G), (j1-half,G), (j1+midHalf,B), (j2-midHalf,B), (j2+half,G), (1,G)
        assertEquals(6, result.size)
        assertEquals(j1 + midHalf, result[2].progress, 1e-4f)
        assertEquals(j2 - midHalf, result[3].progress, 1e-4f)
        assertEquals(SegmentRating.BADLY, result[2].rating)
        assertEquals(SegmentRating.BADLY, result[3].rating)
    }

    @Test
    fun `build emits six stops for three runs in order`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.PERFECT),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
                segment(0.0, 0.002, 0.0, 0.003, SegmentRating.PERFECT),
            ),
        )

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        assertEquals(6, result.size)
        assertEquals(
            listOf(
                SegmentRating.PERFECT,
                SegmentRating.PERFECT,
                SegmentRating.BADLY,
                SegmentRating.BADLY,
                SegmentRating.PERFECT,
                SegmentRating.PERFECT,
            ),
            result.map { it.rating },
        )
    }

    @Test
    fun `build dedupes zero-length joins and keeps progress monotonic`() {
        // given — two consecutive identical points in the middle (zero-length segment)
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        result.zipWithNext().forEach { (a, b) -> assertTrue("progress must be non-decreasing", a.progress <= b.progress) }
        assertEquals(SegmentRating.GOOD, result.first().rating)
        assertEquals(SegmentRating.BADLY, result.last().rating)
    }

    @Test
    fun `build returns empty list when total length is zero`() {
        // given — every segment is degenerate, total length = 0
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0, SegmentRating.GOOD),
            ),
        )

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        assertEquals(emptyList<GradientStop>(), result)
    }

    private fun segment(
        startLat: Double,
        startLon: Double,
        finishLat: Double,
        finishLon: Double,
        rating: SegmentRating,
    ) = MapPathSegment(
        startPoint = MapPoint(startLat, startLon),
        finishPoint = MapPoint(finishLat, finishLon),
        rating = rating,
    )
```

Also add imports at the top of the test file (next to existing ones):

```kotlin
import org.junit.Assert.assertTrue
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
```

- [ ] **Step 2: Run tests, confirm compilation/run failures**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PathGradientStopsBuilderTest"`
Expected: compilation error — `GradientStop` and `PathGradientStopsBuilder` unresolved.

- [ ] **Step 3: Implement `GradientStop` + `PathGradientStopsBuilder` in the same file**

Append to `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt`:

```kotlin
data class GradientStop(val progress: Float, val rating: SegmentRating)

object PathGradientStopsBuilder {

    fun build(path: MapRatingPath, blendMeters: Float): List<GradientStop> {
        val segments = path.pathSegments
        if (segments.isEmpty()) return emptyList()

        // Build a deduplicated polyline: p[0..N], and per-edge ratings r[1..N].
        val points = ArrayList<MapPoint>(segments.size + 1)
        val ratings = ArrayList<SegmentRating>(segments.size)
        for ((index, seg) in segments.withIndex()) {
            if (index == 0) {
                points.add(seg.startPoint)
            } else if (seg.startPoint != points.last()) {
                // Non-contiguous data — bridge with the new start point (treated as an edge of the next rating)
                points.add(seg.startPoint)
                ratings.add(seg.rating)
            }
            if (seg.finishPoint != points.last()) {
                points.add(seg.finishPoint)
                ratings.add(seg.rating)
            }
        }
        if (ratings.isEmpty()) return emptyList()

        // Cumulative distances d[k] for k in 0..N.
        val cum = DoubleArray(points.size)
        for (k in 1 until points.size) {
            cum[k] = cum[k - 1] + haversineMeters(points[k - 1], points[k])
        }
        val totalLen = cum.last()
        if (totalLen <= 0.0) return emptyList()

        // Junction indices k (1..N-1) where rating changes.
        val junctions = ArrayList<Int>()
        for (k in 1 until ratings.size) {
            if (ratings[k] != ratings[k - 1]) junctions.add(k)
        }

        val stops = ArrayList<GradientStop>(2 + junctions.size * 2)
        stops.add(GradientStop(0f, ratings.first()))

        val halfBlend = (blendMeters / 2.0)
        for ((i, k) in junctions.withIndex()) {
            val dJ = cum[k]
            val dPrev = if (i == 0) 0.0 else cum[junctions[i - 1]]
            val dNext = if (i == junctions.lastIndex) totalLen else cum[junctions[i + 1]]
            val halfPrev = minOf(halfBlend, (dJ - dPrev) / 2.0)
            val halfNext = minOf(halfBlend, (dNext - dJ) / 2.0)

            val before = ((dJ - halfPrev) / totalLen).toFloat().coerceIn(0f, 1f)
            val after = ((dJ + halfNext) / totalLen).toFloat().coerceIn(0f, 1f)
            stops.add(GradientStop(before, ratings[k - 1]))
            stops.add(GradientStop(after, ratings[k]))
        }
        stops.add(GradientStop(1f, ratings.last()))

        return enforceMonotonic(stops)
    }

    private fun enforceMonotonic(stops: List<GradientStop>): List<GradientStop> {
        // MapLibre requires strictly increasing stop positions. Nudge ties by a tiny epsilon
        // while preserving the visual ordering (colors before vs after).
        val epsilon = 1e-6f
        var last = -1f
        val out = ArrayList<GradientStop>(stops.size)
        for (stop in stops) {
            val next = if (stop.progress > last) stop.progress else (last + epsilon).coerceAtMost(1f)
            out.add(GradientStop(next, stop.rating))
            last = next
        }
        return out
    }
}
```

- [ ] **Step 4: Run tests, confirm all 12 pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PathGradientStopsBuilderTest"`
Expected: 12 tests pass (3 haversine + 9 builder).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt \
        app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt
git commit -m "Add PathGradientStopsBuilder for line-gradient stops on rating paths"
```

---

## Task 3: Reshape `PathGeoJsonMapper` to emit single combined Feature per path

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt`

This task only changes the mapper. The controller still references the old signatures, so the project will not compile after this task in isolation — that's expected. Task 4 finishes the wiring. Keep the commit small so the next task is reviewable on its own.

- [ ] **Step 1: Rewrite `PathGeoJsonMapper.kt`**

Replace the full contents of `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

object PathGeoJsonMapper {

    const val PROPERTY_PATH_ID = "path_id"

    fun ratingPathToFeature(path: MapRatingPath): Feature? =
        segmentsToFeature(path.pathId, path.pathSegments)

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    /**
     * Concatenates rating segments into a single deduplicated LineString feature.
     * Returns null when fewer than two distinct points are available.
     */
    fun segmentsToFeature(pathId: Long, segments: List<MapPathSegment>): Feature? {
        if (segments.isEmpty()) return null
        val points = ArrayList<MapPoint>(segments.size + 1)
        for ((index, seg) in segments.withIndex()) {
            if (index == 0) {
                points.add(seg.startPoint)
            } else if (seg.startPoint != points.last()) {
                points.add(seg.startPoint)
            }
            if (seg.finishPoint != points.last()) {
                points.add(seg.finishPoint)
            }
        }
        if (points.size < 2) return null
        return Feature.fromGeometry(
            LineString.fromLngLats(points.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
        }
    }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
}
```

- [ ] **Step 2: Confirm intentional compile break in controller**

Run: `./gradlew :app:compileDebugKotlin`
Expected: compilation errors in `MapLibrePathController.kt` (`ratingPathToFeatures`, `segmentsToFeatures`, `PROPERTY_RATING` unresolved). This is intentional — fixed in Task 4. Do not commit yet.

- [ ] **Step 3: Stage the mapper change locally (no commit yet)**

Do not commit on its own — bundle with Task 4 so the tree stays green per commit.

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt
```

---

## Task 4: Rewire `MapLibrePathController` to per-path source/layer + line-gradient

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

- [ ] **Step 1: Replace the file**

Replace the full contents of `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath

class MapLibrePathController(
    private val ratingColors: Map<SegmentRating, Int>,
    @ColorInt private val commonPathColor: Int,
    // MapLibre line-width is in density-independent screen pixels, not raw px.
    private val lineWidth: Float = 6f,
    // Length over which two adjacent rating colors blend at a junction.
    private val blendMeters: Float = 12f,
) {
    private var style: Style? = null

    private val savedRatingPaths = LinkedHashMap<Long, MapRatingPath>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    fun onStyleLoaded(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT, GeoJsonOptions().withLineMetrics(true)))

        // Saved rating layers are inserted below the common layer per-path; ordering: saved-rating < saved-common < current.
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(gradientLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

        for ((pathId, path) in savedRatingPaths) {
            installRatingLayer(style, pathId, path)
        }
        pushCommon()
        pushCurrent()
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        val style = this.style
        for (path in paths) {
            val existed = savedRatingPaths.put(path.pathId, path) != null
            if (style == null) continue
            if (existed) {
                updateRatingLayer(style, path)
            } else {
                installRatingLayer(style, path.pathId, path)
            }
        }
    }

    fun showCommonPaths(paths: List<MapCommonPath>) {
        for (path in paths) {
            commonFeatures[path.pathId] = PathGeoJsonMapper.commonPathToFeature(path)
        }
        pushCommon()
    }

    fun appendCurrentPathSegments(segments: List<MapPathSegment>) {
        currentSegments.addAll(segments)
        pushCurrent()
    }

    /**
     * Stop accumulating into the current run. The rendered features are kept so the
     * finished path stays drawn (even across a style reload) until clear().
     */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val ratingRemoved = savedRatingPaths.remove(pathId) != null
        val commonRemoved = commonFeatures.remove(pathId) != null
        if (ratingRemoved) removeRatingLayer(pathId)
        if (commonRemoved) pushCommon()
    }

    fun hidePaths(pathIds: List<Long>) {
        var commonChanged = false
        for (id in pathIds) {
            if (savedRatingPaths.remove(id) != null) removeRatingLayer(id)
            if (commonFeatures.remove(id) != null) commonChanged = true
        }
        if (commonChanged) pushCommon()
    }

    fun clear() {
        val ratingIds = savedRatingPaths.keys.toList()
        savedRatingPaths.clear()
        commonFeatures.clear()
        currentSegments.clear()
        for (id in ratingIds) removeRatingLayer(id)
        pushCommon()
        pushCurrent()
    }

    private fun installRatingLayer(style: Style, pathId: Long, path: MapRatingPath) {
        val sourceId = ratingSourceId(pathId)
        val layerId = ratingLayerId(pathId)
        val feature = PathGeoJsonMapper.ratingPathToFeature(path)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.addSource(GeoJsonSource(sourceId, collection, GeoJsonOptions().withLineMetrics(true)))
        val layer = LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        applyGradientOrFallback(layer, path)
        style.addLayerBelow(layer, LAYER_SAVED_COMMON)
    }

    private fun updateRatingLayer(style: Style, path: MapRatingPath) {
        val sourceId = ratingSourceId(path.pathId)
        val layerId = ratingLayerId(path.pathId)
        val feature = PathGeoJsonMapper.ratingPathToFeature(path)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
        val layer = style.getLayerAs<LineLayer>(layerId) ?: return
        applyGradientOrFallback(layer, path)
    }

    private fun removeRatingLayer(pathId: Long) {
        val style = this.style ?: return
        style.removeLayer(ratingLayerId(pathId))
        style.removeSource(ratingSourceId(pathId))
    }

    private fun applyGradientOrFallback(layer: LineLayer, path: MapRatingPath) {
        val stops = PathGradientStopsBuilder.build(path, blendMeters)
        if (stops.isEmpty()) {
            layer.setProperties(PropertyFactory.lineColor(color(SegmentRating.NONE)))
        } else {
            layer.setProperties(PropertyFactory.lineGradient(gradientExpression(stops)))
        }
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        val feature = PathGeoJsonMapper.segmentsToFeature(CURRENT_PATH_ID, currentSegments)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style?.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)?.setGeoJson(collection)
        val style = this.style ?: return
        val layer = style.getLayerAs<LineLayer>(LAYER_CURRENT) ?: return
        val currentPath = MapRatingPath(pathId = CURRENT_PATH_ID, pathSegments = currentSegments.toList())
        applyGradientOrFallback(layer, currentPath)
    }

    private fun gradientLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(color(SegmentRating.NONE)),
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun commonLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(commonPathColor),
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun gradientExpression(stops: List<GradientStop>): Expression {
        val args = ArrayList<Expression>(2 + stops.size * 2)
        args.add(Expression.linear())
        args.add(Expression.lineProgress())
        for (stop in stops) {
            args.add(Expression.literal(stop.progress))
            args.add(Expression.color(color(stop.rating)))
        }
        return Expression.interpolate(*args.toTypedArray())
    }

    @ColorInt
    private fun color(rating: SegmentRating): Int =
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)

    private fun ratingSourceId(pathId: Long): String = "$SOURCE_SAVED_RATING_PREFIX$pathId"
    private fun ratingLayerId(pathId: Long): String = "$LAYER_SAVED_RATING_PREFIX$pathId"

    companion object {
        private const val CURRENT_PATH_ID = -1L
        private const val SOURCE_SAVED_RATING_PREFIX = "wt-saved-rating-source-"
        private const val SOURCE_SAVED_COMMON = "wt-saved-common-source"
        private const val SOURCE_CURRENT = "wt-current-source"
        private const val LAYER_SAVED_RATING_PREFIX = "wt-saved-rating-layer-"
        private const val LAYER_SAVED_COMMON = "wt-saved-common-layer"
        private const val LAYER_CURRENT = "wt-current-layer"
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no unresolved references.

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `PathGradientStopsBuilderTest` (12) plus all pre-existing tests pass. No new failures.

- [ ] **Step 4: Commit bundled mapper + controller change**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt
git commit -m "Render rating paths with per-path line-gradient for smooth segment transitions"
```

---

## Task 5: Manual verification on device

`MapLibrePathController` is not unit-tested (interacts with `Style`/`Expression`). Verify visually.

- [ ] **Step 1: Install on a connected device or emulator**

Run: `./gradlew :app:installDebug`
Expected: app installs successfully.

- [ ] **Step 2: Active recording — gradient smoothness**

Start a new walk, change rating mid-walk (e.g., NONE → GOOD → BADLY → PERFECT). On the map, transitions between rating runs should be a smooth fade over roughly 10–15 m, not an abrupt color step.

- [ ] **Step 3: Saved paths — show / hide / clear**

- Stop and save the path.
- From the "My Paths" menu, hide the path → it disappears.
- Show two or more saved paths simultaneously → each renders its own gradient; layer order keeps common (single-color) paths above ratings, current recording above all.
- "Clear all" → all saved layers are removed cleanly.

- [ ] **Step 4: Style reload**

Switch tile source in Settings → all saved rating paths and the current recording re-render with gradients intact.

- [ ] **Step 5: Edge cases**

- Very short walk (one or two GPS points) → no crash; a single solid-color line appears.
- Long uniform-rating walk → solid color throughout, no visible banding from gradient stops.

---

## Self-Review Notes

- **Spec coverage:** Tasks 1–2 cover the builder + tests (spec §Тесты, §Построение градиента); Task 3 covers the mapper changes (spec §Структура кода); Task 4 covers per-path source/layer, lifecycle, layer ordering, current path, fallback (spec §Архитектура); Task 5 covers manual verification (spec §Тесты «Что не тестируется юнитами»).
- **No placeholders:** Every code step has full code; every test step has full assertions; every run step lists expected output.
- **Type consistency:** `ratingPathToFeature`, `segmentsToFeature`, `GradientStop`, `PathGradientStopsBuilder.build`, `installRatingLayer`/`updateRatingLayer`/`removeRatingLayer`, `applyGradientOrFallback` are used identically across tasks.
- **Commit hygiene:** Mapper-only change (Task 3) deliberately bundles into the same commit as the controller rewrite (Task 4) because the mapper change alone breaks compilation; each commit on the branch leaves the tree compiling.

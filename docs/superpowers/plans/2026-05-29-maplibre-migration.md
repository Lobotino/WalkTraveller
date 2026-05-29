# MapLibre GL Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace osmdroid with MapLibre GL Native as the map engine, using free keyless OpenFreeMap vector styles and data-driven GeoJSON line layers for rating-colored paths.

**Architecture:** New, self-contained render units (`PathGeoJsonMapper`, `MapLibrePathController`, `MapLibreUserLocationMarker`) are added first while osmdroid still runs. Then `MainMapFragment` and the map-style selection layer are cut over to MapLibre in one coherent step, and finally all osmdroid code and the dependency are removed. The domain layer and all ViewModels are untouched.

**Tech Stack:** Kotlin, Android, MapLibre GL Native Android (`org.maplibre.gl:android-sdk`), MapLibre GeoJSON, OpenFreeMap styles.

**Note on tests:** Per the project owner, unit tests are authored separately by them (convention: SUT named `sut`, `@Before`/`@After`, given/when/then) before this plan runs. This plan therefore verifies each task by compiling (`./gradlew :app:compileDebugKotlin`), with a full `assembleDebug` at cutover, `assembleRelease` for R8, and a manual on-device checklist at the end. `PathGeoJsonMapper` is a pure object designed for the owner's unit tests. The first MapLibre build requires internet access to fetch the dependency from Maven Central (already configured in `settings.gradle`).

---

## File Structure

**New files:**
- `app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapLibreCoordinateExt.kt` — `MapPoint.toLatLng()` / `LatLng.toMapPoint()`.
- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt` — pure domain→GeoJSON mapping, incl. adjacent-same-rating merge.
- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt` — owns GeoJSON sources/line layers for saved-rating, saved-common, current paths.
- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibreUserLocationMarker.kt` — user marker via GeoJSON point source + symbol layer with rotation.

**Modified files:**
- `app/build.gradle` — swap osmdroid dependency for MapLibre.
- `app/src/main/java/ru/lobotino/walktraveller/App.kt` — init MapLibre; remove osmdroid config.
- `app/src/main/java/ru/lobotino/walktraveller/model/TileSource.kt` — holds a style URL.
- `app/src/main/java/ru/lobotino/walktraveller/model/TileSourceType.kt` — `LIBERTY` / `POSITRON`.
- `app/src/main/java/ru/lobotino/walktraveller/usecases/TileSourceInteractor.kt` — map type → OpenFreeMap style URL.
- `app/src/main/java/ru/lobotino/walktraveller/repositories/TileSourceRepository.kt` — default `LIBERTY`, guard unknown saved values.
- `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt` — cut over map engine.
- `app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapPointExt.kt` — drop `toGeoPoint()`.

**Deleted files:**
- `app/src/main/java/ru/lobotino/walktraveller/ui/UserLocationOverlay.kt`
- `app/src/main/java/ru/lobotino/walktraveller/utils/ext/IGeoPointExt.kt`

**Unchanged (verify, do not edit):** all domain models, ViewModels (`MapViewModel` consumes `TileSource`/path flows but does not read removed members), DI factories, `SettingsFragment` / `SettingsViewModel` (drive off `TileSourceType.values()`), `fragment_map.xml` (MapView is added programmatically, so no layout change).

---

## Task 1: Add MapLibre dependency and initialize the SDK

**Files:**
- Modify: `app/build.gradle:72`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/App.kt`

- [ ] **Step 1: Add the MapLibre dependency (keep osmdroid for now)**

In `app/build.gradle`, immediately after the osmdroid line (`implementation 'org.osmdroid:osmdroid-android:6.1.20'`), add:

```groovy
    implementation 'org.maplibre.gl:android-sdk:11.8.0'
```

(If `11.8.0` fails to resolve, use the latest `11.x` available on Maven Central.)

- [ ] **Step 2: Initialize MapLibre in `App.onCreate`**

In `App.kt`, add the import:

```kotlin
import org.maplibre.android.MapLibre
```

In `onCreate()`, add this line right after `super.onCreate()` (before the existing `StrictMode`/osmdroid `Configuration` code, which stays for now):

```kotlin
        MapLibre.getInstance(this)
```

- [ ] **Step 3: Verify it compiles (dependency resolves)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (MapLibre artifact downloaded).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle app/src/main/java/ru/lobotino/walktraveller/App.kt
git commit -m "Add MapLibre SDK dependency and initialize it"
```

---

## Task 2: Add MapLibre coordinate extensions

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapLibreCoordinateExt.kt`

- [ ] **Step 1: Create the extension file**

```kotlin
package ru.lobotino.walktraveller.utils.ext

import org.maplibre.android.geometry.LatLng
import ru.lobotino.walktraveller.model.map.MapPoint

fun MapPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

fun LatLng.toMapPoint(): MapPoint = MapPoint(latitude, longitude)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapLibreCoordinateExt.kt
git commit -m "Add MapPoint<->LatLng extensions for MapLibre"
```

---

## Task 3: Add PathGeoJsonMapper (pure domain → GeoJSON)

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt`

This object reproduces the current "merge adjacent segments that share a rating and connect end-to-start" logic from `MainMapFragment.paintNewRatingPaths`. It carries `path_id` (number) and, for rating segments, `rating` (the `SegmentRating.name`) as feature properties.

- [ ] **Step 1: Create the mapper**

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

object PathGeoJsonMapper {

    const val PROPERTY_PATH_ID = "path_id"
    const val PROPERTY_RATING = "rating"

    fun ratingPathToFeatures(path: MapRatingPath): List<Feature> =
        segmentsToFeatures(path.pathId, path.pathSegments)

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    /**
     * Groups consecutive segments that share a rating and are connected
     * (previous finish == next start) into a single LineString feature.
     */
    fun segmentsToFeatures(pathId: Long, segments: List<MapPathSegment>): List<Feature> {
        val features = ArrayList<Feature>()
        var runPoints: ArrayList<MapPoint>? = null
        var lastSegment: MapPathSegment? = null

        for (segment in segments) {
            val points = runPoints
            if (points == null || lastSegment == null) {
                runPoints = arrayListOf(segment.startPoint, segment.finishPoint)
            } else if (segment.rating == lastSegment.rating &&
                segment.startPoint == lastSegment.finishPoint
            ) {
                points.add(segment.finishPoint)
            } else {
                features.add(buildFeature(pathId, lastSegment.rating, points))
                runPoints = arrayListOf(segment.startPoint, segment.finishPoint)
            }
            lastSegment = segment
        }

        if (runPoints != null && lastSegment != null && runPoints.size >= 2) {
            features.add(buildFeature(pathId, lastSegment.rating, runPoints))
        }
        return features
    }

    private fun buildFeature(
        pathId: Long,
        rating: SegmentRating,
        points: List<MapPoint>,
    ): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(points.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
            addStringProperty(PROPERTY_RATING, rating.name)
        }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGeoJsonMapper.kt
git commit -m "Add pure PathGeoJsonMapper for domain paths to GeoJSON"
```

---

## Task 4: Add MapLibrePathController

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

Owns three source/layer pairs: saved rating paths (data-driven color by `rating`), saved common paths (single `commonPathColor`), and the current recording path (data-driven color). Keeps in-memory feature state keyed by `pathId` so it can re-apply everything after a style reload (style switch wipes sources/layers).

- [ ] **Step 1: Create the controller**

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
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
    private val lineWidthDp: Float = 6f,
) {
    private var style: Style? = null

    private val savedRatingFeatures = LinkedHashMap<Long, List<Feature>>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    fun onStyleLoaded(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_SAVED_RATING))
        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT))

        style.addLayer(ratingLineLayer(LAYER_SAVED_RATING, SOURCE_SAVED_RATING))
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(ratingLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        for (path in paths) {
            savedRatingFeatures[path.pathId] = PathGeoJsonMapper.ratingPathToFeatures(path)
        }
        pushSavedRating()
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

    /** Stop accumulating into the current run; leave the drawn line until clear(). */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val removed = (savedRatingFeatures.remove(pathId) != null) or
            (commonFeatures.remove(pathId) != null)
        if (removed) {
            pushSavedRating()
            pushCommon()
        }
    }

    fun hidePaths(pathIds: List<Long>) {
        var removed = false
        for (id in pathIds) {
            if (savedRatingFeatures.remove(id) != null) removed = true
            if (commonFeatures.remove(id) != null) removed = true
        }
        if (removed) {
            pushSavedRating()
            pushCommon()
        }
    }

    fun clear() {
        savedRatingFeatures.clear()
        commonFeatures.clear()
        currentSegments.clear()
        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    private fun pushSavedRating() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_RATING)
            ?.setGeoJson(FeatureCollection.fromFeatures(savedRatingFeatures.values.flatten()))
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    PathGeoJsonMapper.segmentsToFeatures(CURRENT_PATH_ID, currentSegments)
                )
            )
    }

    private fun ratingLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(ratingColorExpression()),
            PropertyFactory.lineWidth(lineWidthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun commonLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(commonPathColor),
            PropertyFactory.lineWidth(lineWidthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun ratingColorExpression(): Expression =
        Expression.match(
            Expression.get(PathGeoJsonMapper.PROPERTY_RATING),
            Expression.literal(SegmentRating.PERFECT.name), Expression.color(color(SegmentRating.PERFECT)),
            Expression.literal(SegmentRating.GOOD.name), Expression.color(color(SegmentRating.GOOD)),
            Expression.literal(SegmentRating.NORMAL.name), Expression.color(color(SegmentRating.NORMAL)),
            Expression.literal(SegmentRating.BADLY.name), Expression.color(color(SegmentRating.BADLY)),
            Expression.color(color(SegmentRating.NONE)),
        )

    @ColorInt
    private fun color(rating: SegmentRating): Int =
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)

    companion object {
        private const val CURRENT_PATH_ID = -1L
        private const val SOURCE_SAVED_RATING = "wt-saved-rating-source"
        private const val SOURCE_SAVED_COMMON = "wt-saved-common-source"
        private const val SOURCE_CURRENT = "wt-current-source"
        private const val LAYER_SAVED_RATING = "wt-saved-rating-layer"
        private const val LAYER_SAVED_COMMON = "wt-saved-common-layer"
        private const val LAYER_CURRENT = "wt-current-layer"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt
git commit -m "Add MapLibrePathController for data-driven path rendering"
```

---

## Task 5: Add MapLibreUserLocationMarker

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibreUserLocationMarker.kt`

Replaces `UserLocationOverlay`. One GeoJSON point source + a `SymbolLayer` whose `icon-rotate` reads the per-feature `bearing` property. Re-registers its image/source/layer on each style load.

- [ ] **Step 1: Create the marker controller**

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.Bitmap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

class MapLibreUserLocationMarker(private val iconBitmap: Bitmap) {

    private var style: Style? = null
    private var position: LatLng? = null
    private var bearing: Float = 0f

    fun onStyleLoaded(style: Style) {
        this.style = style
        style.addImage(IMAGE_ID, iconBitmap)
        style.addSource(GeoJsonSource(SOURCE_ID))
        style.addLayer(
            SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                PropertyFactory.iconImage(IMAGE_ID),
                PropertyFactory.iconRotate(Expression.get(PROPERTY_BEARING)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        push()
    }

    fun setPosition(position: LatLng) {
        this.position = position
        push()
    }

    fun setRotation(bearing: Float) {
        this.bearing = if (bearing >= 360f) bearing % 360f else bearing
        push()
    }

    private fun push() {
        val current = position ?: return
        val feature = Feature.fromGeometry(
            Point.fromLngLat(current.longitude, current.latitude)
        ).apply { addNumberProperty(PROPERTY_BEARING, bearing) }
        style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(feature)
    }

    companion object {
        private const val IMAGE_ID = "wt-user-marker-image"
        private const val SOURCE_ID = "wt-user-marker-source"
        private const val LAYER_ID = "wt-user-marker-layer"
        private const val PROPERTY_BEARING = "bearing"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibreUserLocationMarker.kt
git commit -m "Add MapLibreUserLocationMarker symbol-layer marker"
```

---

## Task 6: Cut MainMapFragment and the style layer over to MapLibre

This is the cutover. All edits below are tightly coupled (the `mapView` field type changes), so they are done together and verified with a single build at the end. osmdroid is still on the classpath, so leftover osmdroid code in `UserLocationOverlay.kt` / ext files still compiles; it is deleted in Task 7.

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/model/TileSource.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/model/TileSourceType.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/usecases/TileSourceInteractor.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/repositories/TileSourceRepository.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

- [ ] **Step 1: Make `TileSource` hold a style URL**

Replace the entire contents of `model/TileSource.kt` with:

```kotlin
package ru.lobotino.walktraveller.model

data class TileSource(val styleUrl: String)
```

- [ ] **Step 2: Replace the style types**

Replace the entire contents of `model/TileSourceType.kt` with:

```kotlin
package ru.lobotino.walktraveller.model

enum class TileSourceType(val simpleName: String) {
    LIBERTY("Liberty"), POSITRON("Positron")
}
```

- [ ] **Step 3: Map types to OpenFreeMap style URLs**

Replace the entire contents of `usecases/TileSourceInteractor.kt` with:

```kotlin
package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.TileSource
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.ITileSourceRepository
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor

class TileSourceInteractor(
    private val tileSourceRepository: ITileSourceRepository,
) : ITileSourceInteractor {

    override fun getCurrentTileSource(): TileSource =
        TileSource(styleUrlFor(tileSourceRepository.getCurrentTileSourceType()))

    override fun getCurrentTileSourceType(): TileSourceType =
        tileSourceRepository.getCurrentTileSourceType()

    override fun setCurrentTileSourceType(tileSourceType: TileSourceType) {
        tileSourceRepository.setCurrentTileSourceType(tileSourceType)
    }

    private fun styleUrlFor(type: TileSourceType): String = when (type) {
        TileSourceType.LIBERTY -> "https://tiles.openfreemap.org/styles/liberty"
        TileSourceType.POSITRON -> "https://tiles.openfreemap.org/styles/positron"
    }
}
```

- [ ] **Step 4: Default to LIBERTY and survive old saved values**

In `repositories/TileSourceRepository.kt`, replace the `getCurrentTileSourceType()` method body and the `DefaultTileSource` constant.

Replace this method:

```kotlin
    override fun getCurrentTileSourceType(): TileSourceType {
        val savedValue =
            sharedPreferences.getString(KEY_CURRENT_TILE_SOURCE, DefaultTileSource.name)
                ?: DefaultTileSource.name

        return TileSourceType.valueOf(savedValue)
    }
```

with:

```kotlin
    override fun getCurrentTileSourceType(): TileSourceType {
        val savedValue =
            sharedPreferences.getString(KEY_CURRENT_TILE_SOURCE, DefaultTileSource.name)
                ?: DefaultTileSource.name

        return try {
            TileSourceType.valueOf(savedValue)
        } catch (e: IllegalArgumentException) {
            DefaultTileSource
        }
    }
```

and replace:

```kotlin
        private val DefaultTileSource = TileSourceType.OSM_MAPNIK
```

with:

```kotlin
        private val DefaultTileSource = TileSourceType.LIBERTY
```

- [ ] **Step 5: Swap osmdroid imports for MapLibre in `MainMapFragment.kt`**

Remove these imports (the osmdroid ones, the now-unused `ArrayMap`, and the three path-model imports that were only referenced by the soon-deleted render methods):

```kotlin
import android.graphics.Paint
import android.util.ArrayMap
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.utils.ext.toGeoPoint
```

Add these imports (keep them alphabetized with the existing groups):

```kotlin
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import ru.lobotino.walktraveller.ui.maplibre.MapLibrePathController
import ru.lobotino.walktraveller.ui.maplibre.MapLibreUserLocationMarker
import ru.lobotino.walktraveller.utils.ext.toLatLng
```

(The existing `import ru.lobotino.walktraveller.utils.ext.toMapPoint` now resolves to the new `LatLng.toMapPoint()`; keep it.)

- [ ] **Step 6: Replace the map/render fields**

Replace these fields:

```kotlin
    private lateinit var mapView: MapView
```

with:

```kotlin
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var currentStyleUrl: String? = null
    private lateinit var pathController: MapLibrePathController
    private lateinit var userLocationMarker: MapLibreUserLocationMarker
```

Replace this field:

```kotlin
    private lateinit var userLocationOverlay: UserLocationOverlay
```

with nothing (delete the line).

Delete these fields entirely:

```kotlin
    private val showingPathsPolylines = ArrayMap<Long, List<Polyline>>()
    private val currentPathPolylines = ArrayList<Polyline>()
    private var currentPathPolyline: Polyline? = null
    private var lastCurrentPathRating: SegmentRating? = null
```

Delete this field (it moves into the controller) and its assignment in `initColors` (next step):

```kotlin
    private var commonPathColor by Delegates.notNull<Int>()
```

- [ ] **Step 7: Drop the unused common-color read in `initColors`**

In `initColors()`, delete this line:

```kotlin
            commonPathColor = ContextCompat.getColor(context, R.color.common_path_color)
```

(The five `rating*Color` reads stay — they are still used for the rating buttons.)

- [ ] **Step 8: Build the MapLibre MapView, controllers, and listeners in `initViews`**

Replace this block:

```kotlin
            mapView = MapView(context).apply {
                controller.setZoom(DEFAULT_COMFORT_ZOOM)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent): Boolean {
                        mapViewModel.onMapScrolled(event.source.mapCenter.toMapPoint())
                        return true
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        mapViewModel.onMapZoomed()
                        return true
                    }
                })
            }
            mapViewContainer.addView(mapView)

            userLocationOverlay = UserLocationOverlay(requireContext())
```

with:

```kotlin
            pathController = MapLibrePathController(
                ratingColors = mapOf(
                    SegmentRating.PERFECT to ContextCompat.getColor(context, R.color.rating_perfect),
                    SegmentRating.GOOD to ContextCompat.getColor(context, R.color.rating_good),
                    SegmentRating.NORMAL to ContextCompat.getColor(context, R.color.rating_normal),
                    SegmentRating.BADLY to ContextCompat.getColor(context, R.color.rating_badly),
                    SegmentRating.NONE to ContextCompat.getColor(context, R.color.rating_none),
                ),
                commonPathColor = ContextCompat.getColor(context, R.color.common_path_color),
            )
            userLocationMarker = MapLibreUserLocationMarker(
                AppCompatResources.getDrawable(context, R.drawable.ic_user_marker)!!.toBitmapCompat()
            )

            mapView = MapView(context)
            mapViewContainer.addView(mapView)
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                mapLibreMap = map
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { target ->
                        mapViewModel.onMapScrolled(target.toMapPoint())
                    }
                }
                currentStyleUrl?.let { applyStyle(map, it) }
            }
```

Note: the local `val mapViewContainer = ...` line directly above this block stays as-is.

- [ ] **Step 9: Replace the map-center observer**

Replace:

```kotlin
                    observeNewMapCenter.onEach { newMapCenter ->
                        mapView.controller?.let { mapViewController ->
                            mapViewController.setCenter(newMapCenter.toGeoPoint())
                            if (mapView.zoomLevelDouble < DEFAULT_COMFORT_ZOOM) {
                                mapViewController.setZoom(DEFAULT_COMFORT_ZOOM)
                            }
                        }
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

with:

```kotlin
                    observeNewMapCenter.onEach { newMapCenter ->
                        val map = mapLibreMap ?: return@onEach
                        val targetZoom = maxOf(map.cameraPosition.zoom, DEFAULT_COMFORT_ZOOM)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(newMapCenter.toLatLng(), targetZoom)
                        )
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 10: Delegate the path observers to the controller**

Replace:

```kotlin
                    observeNewCurrentPathSegments.onEach { pathSegments ->
                        paintNewCurrentPathSegments(pathSegments)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewCommonPath.onEach { pathList ->
                        paintNewCommonPaths(pathList, commonPathColor)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewRatingPath.onEach { pathList ->
                        paintNewRatingPaths(pathList)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

with:

```kotlin
                    observeNewCurrentPathSegments.onEach { pathSegments ->
                        pathController.appendCurrentPathSegments(pathSegments)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewCommonPath.onEach { pathList ->
                        pathController.showCommonPaths(pathList)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewRatingPath.onEach { pathList ->
                        pathController.showRatingPaths(pathList)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 11: Delegate hide/clear and user-location observers**

Replace:

```kotlin
                    observeHidePath.onEach { pathsToHide ->
                        when (pathsToHide) {
                            PathsToAction.All -> {
                                this@MainMapFragment.clearMap()
                            }

                            is PathsToAction.Single -> {
                                hidePathById(pathsToHide.pathId)
                            }

                            is PathsToAction.Multiple -> {
                                hidePathsList(pathsToHide.pathIds)
                            }
                        }
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewCurrentUserLocation.onEach { newUserLocation ->
                        userLocationOverlay.setPosition(newUserLocation.toGeoPoint())
                        refreshMapNow()
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

with:

```kotlin
                    observeHidePath.onEach { pathsToHide ->
                        when (pathsToHide) {
                            PathsToAction.All -> pathController.clear()
                            is PathsToAction.Single -> pathController.hidePath(pathsToHide.pathId)
                            is PathsToAction.Multiple -> pathController.hidePaths(pathsToHide.pathIds)
                        }
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewCurrentUserLocation.onEach { newUserLocation ->
                        userLocationMarker.setPosition(newUserLocation.toLatLng())
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 12: Update the rotation observer**

Replace:

```kotlin
                    observeNewUserRotation().onEach { newUserRotation ->
                        userLocationOverlay.setRotation(newUserRotation)
                        refreshMapNow()
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

with:

```kotlin
                    observeNewUserRotation().onEach { newUserRotation ->
                        userLocationMarker.setRotation(newUserRotation)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 13: Replace `syncTileSource` and add `applyStyle` + bitmap helper**

Replace:

```kotlin
    private fun syncTileSource(tileSource: TileSource) {
        when (tileSource) {
            is TileSource.OSMTileSource -> mapView.setTileSource(tileSource.tileSource)
            // TODO 2gis, yandex, google...
        }
    }
```

with:

```kotlin
    private fun syncTileSource(tileSource: TileSource) {
        currentStyleUrl = tileSource.styleUrl
        mapLibreMap?.let { applyStyle(it, tileSource.styleUrl) }
    }

    private fun applyStyle(map: MapLibreMap, styleUrl: String) {
        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            pathController.onStyleLoaded(style)
            userLocationMarker.onStyleLoaded(style)
        }
    }

    private fun Drawable.toBitmapCompat(): Bitmap {
        val width = intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
```

- [ ] **Step 14: Point `setLastPathFinished` at the controller**

Replace:

```kotlin
    private fun setLastPathFinished() {
        currentPathPolylines.clear()
        currentPathPolyline = null
        lastCurrentPathRating = null
    }
```

with:

```kotlin
    private fun setLastPathFinished() {
        pathController.finishCurrentPath()
    }
```

- [ ] **Step 15: Remove the leftover `refreshMapNow()` call, then delete the dead render methods**

First, in `updateMapUiState(...)`, delete the trailing call (MapLibre repaints automatically when a source changes, so no manual invalidate is needed):

```kotlin
        refreshMapNow()
```

(It is the last statement of `updateMapUiState`, right after `syncRatingButtons(mapUiState.newRating)`. Leave `syncRatingButtons(...)` in place.)

Then delete these methods entirely from `MainMapFragment.kt`: `clearMap`, `paintNewCommonPaths`, `paintNewRatingPaths`, `createRatingSegmentPolyline`, `paintNewCurrentPathSegments`, `hidePathById`, `hidePathsList`, `getRatingColor`, `refreshMapNow`, `addUserLocationTracker`.

All call sites of these methods were replaced in Steps 10–12 and 14, plus the `refreshMapNow()` call just removed above. The Step 17 build confirms nothing dangles. (Note: `mapViewModel.clearMap()` at the `showPathsMenuButton` click handler is a *ViewModel* method — leave it; only the fragment's private `clearMap()` is deleted.)

- [ ] **Step 16: Forward the remaining MapView lifecycle calls**

In `onStart()`, add as the first line inside the method (before the service binding):

```kotlin
        mapView.onStart()
```

In `onStop()`, add as the first line inside the method (before `activity?.unbindService(...)`):

```kotlin
        mapView.onStop()
```

(`onResume()` already calls `mapView.onResume()`; `onPause()` already calls `mapView.onPause()` — leave them.)

Add these new overrides to the fragment (place them next to the other lifecycle overrides):

```kotlin
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) {
            mapView.onSaveInstanceState(outState)
        }
    }

    override fun onDestroyView() {
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
        mapLibreMap = null
        super.onDestroyView()
    }
```

- [ ] **Step 17: Build the debug APK**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL with no unresolved references.

Then run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 18: Confirm no stray osmdroid references remain in live code**

Run: `grep -rn "osmdroid\|GeoPoint\|Polyline\|UserLocationOverlay\|OSMTileSource" app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt app/src/main/java/ru/lobotino/walktraveller/usecases/TileSourceInteractor.kt app/src/main/java/ru/lobotino/walktraveller/model/TileSource.kt`
Expected: no output.

- [ ] **Step 19: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt \
        app/src/main/java/ru/lobotino/walktraveller/model/TileSource.kt \
        app/src/main/java/ru/lobotino/walktraveller/model/TileSourceType.kt \
        app/src/main/java/ru/lobotino/walktraveller/usecases/TileSourceInteractor.kt \
        app/src/main/java/ru/lobotino/walktraveller/repositories/TileSourceRepository.kt
git commit -m "Cut MainMapFragment and map-style layer over to MapLibre"
```

---

## Task 7: Remove osmdroid

**Files:**
- Delete: `app/src/main/java/ru/lobotino/walktraveller/ui/UserLocationOverlay.kt`
- Delete: `app/src/main/java/ru/lobotino/walktraveller/utils/ext/IGeoPointExt.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapPointExt.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/App.kt`
- Modify: `app/build.gradle`

- [ ] **Step 1: Confirm the osmdroid-dependent files are now unused**

Run: `grep -rn "UserLocationOverlay\|IGeoPoint\|\.toGeoPoint(" app/src/main/java`
Expected: no output (all usages were removed in Task 6). If anything appears, fix that call site before continuing.

- [ ] **Step 2: Delete the dead osmdroid files**

```bash
git rm app/src/main/java/ru/lobotino/walktraveller/ui/UserLocationOverlay.kt
git rm app/src/main/java/ru/lobotino/walktraveller/utils/ext/IGeoPointExt.kt
```

- [ ] **Step 3: Remove `toGeoPoint()` from `MapPointExt.kt`**

In `utils/ext/MapPointExt.kt`, delete the osmdroid import and the `toGeoPoint` function:

```kotlin
import org.osmdroid.util.GeoPoint
```

```kotlin
fun MapPoint.toGeoPoint(): GeoPoint {
    return GeoPoint(latitude, longitude)
}
```

Leave `toCoordinatePoint()` and `toText()` intact.

- [ ] **Step 4: Remove osmdroid init from `App.kt`**

Delete these imports:

```kotlin
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration
```

Delete this block from `onCreate()`:

```kotlin
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
```

(Keep `MapLibre.getInstance(this)` and the `StrictMode` line.)

- [ ] **Step 5: Remove the osmdroid dependency**

In `app/build.gradle`, delete the line:

```groovy
    implementation 'org.osmdroid:osmdroid-android:6.1.20'
```

- [ ] **Step 6: Verify no osmdroid references remain anywhere**

Run: `grep -rn "osmdroid" app/src app/build.gradle`
Expected: no output.

- [ ] **Step 7: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle app/src/main/java/ru/lobotino/walktraveller/App.kt \
        app/src/main/java/ru/lobotino/walktraveller/utils/ext/MapPointExt.kt
git commit -m "Remove osmdroid dependency and dead code"
```

---

## Task 8: Verify release (R8) build

MapLibre ships consumer ProGuard rules, so no app-side rules should be needed. This task confirms the minified release build succeeds and the map classes survive shrinking.

**Files:** none (build only).

- [ ] **Step 1: Assemble the release build**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: If R8 reports missing/kept-class warnings for `org.maplibre.*`, add app ProGuard rules**

Only if Step 1 fails or warns about MapLibre classes, append to `app/proguard-rules.pro`:

```proguard
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn org.maplibre.**
```

Then re-run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (only if proguard-rules.pro changed)**

```bash
git add app/proguard-rules.pro
git commit -m "Add R8 keep rules for MapLibre"
```

---

## Task 9: Manual verification on device/emulator

**Files:** none.

- [ ] **Step 1: Install and launch**

Run: `./gradlew installDebug` and open the app on a device/emulator with internet.
Verify: the base map renders (OpenFreeMap Liberty) with no blank tiles.

- [ ] **Step 2: Record a path with mixed ratings**

Start a walk, change ratings (Perfect/Good/Normal/Badly/None) while moving.
Verify: the live current-path line draws and each segment shows the correct rating color, with rounded joins/caps.

- [ ] **Step 3: Finish and re-show**

Stop the walk; open My Paths and show the saved path.
Verify: the saved rating path renders with the same per-segment colors.

- [ ] **Step 4: Show/hide saved paths**

Show several paths, then hide one and hide multiple.
Verify: only the targeted paths disappear; others remain.

- [ ] **Step 5: Common (outer) paths**

Show outer/shared paths.
Verify: they render in the single common color (`common_path_color`).

- [ ] **Step 6: Switch map style**

In Settings, switch between Liberty and Positron while paths are shown.
Verify: the basemap changes and all paths + the user marker reappear after the switch (style reload re-applies layers).

- [ ] **Step 7: User marker position + rotation**

Verify: the user marker tracks location and rotates with heading.

- [ ] **Step 8: Camera follow**

Tap find-my-location.
Verify: the camera animates to the user and zooms to at least the comfort zoom; the find-location button returns to its default state after the move.

- [ ] **Step 9: Lifecycle**

Background/foreground the app and rotate the device.
Verify: no crash; the map and paths restore.

- [ ] **Step 10: Confirm existing unit tests still pass**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (domain tests unaffected).

---

## Post-implementation cleanup

Per the project owner's standing preference, design/spec working artifacts are deleted from git once implementation is finished. After this plan is fully implemented and merged, delete `docs/superpowers/specs/2026-05-29-maplibre-migration-design.md` and `docs/superpowers/plans/2026-05-29-maplibre-migration.md`.

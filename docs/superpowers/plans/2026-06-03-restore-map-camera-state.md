# Restore Map Camera State (center + zoom) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the map camera state (center + zoom) on every camera idle, and apply the saved state as the initial camera on next launch (with Moscow + zoom 15.0 as the first-launch default).

**Architecture:** Generalize the existing "last seen point" persistence into a "map camera state" persistence. Introduce a `MapCameraState(center, zoom)` model, rename `LastSeenPointRepository` → `MapCameraStateRepository`, and update `MapStateInteractor` to read/write this combined state. ViewModel exposes a new `observeRestoreCameraState` flow for startup restoration; the fragment subscribes and calls `moveCamera` (no animation). Camera idle in the fragment passes both target and zoom into `MapViewModel.onCameraIdle`, which replaces the old `onMapScrolled` and removes the no-op `onMapZoomed`.

**Tech Stack:** Kotlin, Android, MapLibre Native, SharedPreferences, JUnit4 + MockK + kotlinx-coroutines-test.

---

## File Structure

**Create:**
- `app/src/main/java/ru/lobotino/walktraveller/model/map/MapCameraState.kt`
- `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IMapCameraStateRepository.kt`
- `app/src/main/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepository.kt`
- `app/src/test/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepositoryTest.kt`
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelCameraStateTest.kt`

**Delete:**
- `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/ILastSeenPointRepository.kt`
- `app/src/main/java/ru/lobotino/walktraveller/repositories/LastSeenPointRepository.kt`

**Modify:**
- `app/src/main/java/ru/lobotino/walktraveller/usecases/interfaces/IMapStateInteractor.kt`
- `app/src/main/java/ru/lobotino/walktraveller/usecases/MapStateInteractor.kt`
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`
- `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`
- `app/src/test/java/ru/lobotino/walktraveller/usecases/MapStateInteractorTest.kt`

---

## Task 1: Introduce `MapCameraState` model

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/model/map/MapCameraState.kt`

- [ ] **Step 1: Create the data class**

```kotlin
package ru.lobotino.walktraveller.model.map

data class MapCameraState(val center: MapPoint, val zoom: Double)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/model/map/MapCameraState.kt
git commit -m "Add MapCameraState model"
```

---

## Task 2: New repository interface + tests for the implementation

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IMapCameraStateRepository.kt`
- Create: `app/src/test/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepositoryTest.kt`

This task introduces the interface and the failing tests for the implementation. The implementation arrives in Task 3.

- [ ] **Step 1: Create the interface**

```kotlin
package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapCameraState

interface IMapCameraStateRepository {

    fun setLastCameraState(state: MapCameraState)

    fun getLastCameraState(): MapCameraState?
}
```

- [ ] **Step 2: Write the failing repository tests**

Create `app/src/test/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepositoryTest.kt`:

```kotlin
package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint

class MapCameraStateRepositoryTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var sut: MapCameraStateRepository

    @Before
    fun setUp() {
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        sut = MapCameraStateRepository(sharedPreferences)
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `getLastCameraState returns full state when all three keys present`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.5"

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 16.5), result)
    }

    @Test
    fun `getLastCameraState falls back to default zoom when only point is stored`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns null

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 15.0), result)
    }

    @Test
    fun `getLastCameraState falls back to default zoom on unparseable zoom value`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "not-a-number"

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 15.0), result)
    }

    @Test
    fun `getLastCameraState returns null when latitude missing`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns null
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.0"

        // when
        val result = sut.getLastCameraState()

        // then
        assertNull(result)
    }

    @Test
    fun `getLastCameraState returns null when longitude missing`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns null
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.0"

        // when
        val result = sut.getLastCameraState()

        // then
        assertNull(result)
    }

    @Test
    fun `setLastCameraState writes all three keys`() {
        // given
        val latSlot = slot<String>()
        val lngSlot = slot<String>()
        val zoomSlot = slot<String>()
        every { editor.putString("last_seen_point_latitude", capture(latSlot)) } returns editor
        every { editor.putString("last_seen_point_longitude", capture(lngSlot)) } returns editor
        every { editor.putString("last_seen_zoom", capture(zoomSlot)) } returns editor

        // when
        sut.setLastCameraState(MapCameraState(MapPoint(1.5, 2.5), 17.25))

        // then
        assertEquals("1.5", latSlot.captured)
        assertEquals("2.5", lngSlot.captured)
        assertEquals("17.25", zoomSlot.captured)
        verify(exactly = 1) { editor.apply() }
    }
}
```

- [ ] **Step 3: Verify tests fail to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `MapCameraStateRepository` is unresolved (intentional; implementation in Task 3).

- [ ] **Step 4: Commit**

```bash
git add \
  app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IMapCameraStateRepository.kt \
  app/src/test/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepositoryTest.kt
git commit -m "Add IMapCameraStateRepository and failing repository tests"
```

---

## Task 3: Implement `MapCameraStateRepository`

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepository.kt`

- [ ] **Step 1: Implement the repository**

```kotlin
package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository

class MapCameraStateRepository(private val sharedPreferences: SharedPreferences) :
    IMapCameraStateRepository {

    companion object {
        private const val LAST_SEEN_POINT_LATITUDE_TAG = "last_seen_point_latitude"
        private const val LAST_SEEN_POINT_LONGITUDE_TAG = "last_seen_point_longitude"
        private const val LAST_SEEN_ZOOM_TAG = "last_seen_zoom"

        // Migration fallback: legacy installs store only the point.
        private const val DEFAULT_ZOOM = 15.0
    }

    override fun getLastCameraState(): MapCameraState? {
        val latitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LATITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        val longitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LONGITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        val zoom =
            sharedPreferences.getString(LAST_SEEN_ZOOM_TAG, null)?.toDoubleOrNull()
                ?: DEFAULT_ZOOM

        return MapCameraState(MapPoint(latitude, longitude), zoom)
    }

    override fun setLastCameraState(state: MapCameraState) {
        sharedPreferences.edit().apply {
            putString(LAST_SEEN_POINT_LATITUDE_TAG, state.center.latitude.toString())
            putString(LAST_SEEN_POINT_LONGITUDE_TAG, state.center.longitude.toString())
            putString(LAST_SEEN_ZOOM_TAG, state.zoom.toString())
            apply()
        }
    }
}
```

- [ ] **Step 2: Run repository tests**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.repositories.MapCameraStateRepositoryTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/repositories/MapCameraStateRepository.kt
git commit -m "Implement MapCameraStateRepository with zoom persistence"
```

---

## Task 4: Update `IMapStateInteractor` + `MapStateInteractor` and its tests

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/usecases/interfaces/IMapStateInteractor.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/usecases/MapStateInteractor.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/usecases/MapStateInteractorTest.kt`

- [ ] **Step 1: Rewrite the test file (it will fail to compile until impl is updated)**

Replace the entire contents of `app/src/test/java/ru/lobotino/walktraveller/usecases/MapStateInteractorTest.kt` with:

```kotlin
package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository

class MapStateInteractorTest {

    private lateinit var mapCameraStateRepository: IMapCameraStateRepository
    private lateinit var sut: MapStateInteractor

    @Before
    fun setUp() {
        mapCameraStateRepository = mockk()
        sut = MapStateInteractor(mapCameraStateRepository)
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `getLastCameraState returns stored state when present`() {
        // given
        val stored = MapCameraState(MapPoint(10.0, 20.0), 17.5)
        every { mapCameraStateRepository.getLastCameraState() } returns stored

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(stored, result)
    }

    @Test
    fun `getLastCameraState falls back to Moscow with default zoom when nothing stored`() {
        // given
        every { mapCameraStateRepository.getLastCameraState() } returns null

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(55.7522200, 37.6155600), 15.0), result)
    }

    @Test
    fun `setLastCameraState delegates to repository`() {
        // given
        val state = MapCameraState(MapPoint(1.0, 2.0), 14.0)
        every { mapCameraStateRepository.setLastCameraState(any()) } just Runs

        // when
        sut.setLastCameraState(state)

        // then
        verify(exactly = 1) { mapCameraStateRepository.setLastCameraState(state) }
    }
}
```

- [ ] **Step 2: Rewrite the interface**

Replace `app/src/main/java/ru/lobotino/walktraveller/usecases/interfaces/IMapStateInteractor.kt` with:

```kotlin
package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCameraState

interface IMapStateInteractor {

    fun setLastCameraState(state: MapCameraState)

    fun getLastCameraState(): MapCameraState
}
```

- [ ] **Step 3: Rewrite the interactor**

Replace `app/src/main/java/ru/lobotino/walktraveller/usecases/MapStateInteractor.kt` with:

```kotlin
package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor

class MapStateInteractor(private val mapCameraStateRepository: IMapCameraStateRepository) :
    IMapStateInteractor {

    companion object {
        const val DEFAULT_ZOOM = 15.0

        // Moscow city coordinates
        private val defaultLastSeenPoint = MapPoint(55.7522200, 37.6155600)
        private val defaultCameraState = MapCameraState(defaultLastSeenPoint, DEFAULT_ZOOM)
    }

    override fun getLastCameraState(): MapCameraState {
        return mapCameraStateRepository.getLastCameraState() ?: defaultCameraState
    }

    override fun setLastCameraState(state: MapCameraState) {
        mapCameraStateRepository.setLastCameraState(state)
    }
}
```

- [ ] **Step 4: Run the interactor tests**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.usecases.MapStateInteractorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/java/ru/lobotino/walktraveller/usecases/interfaces/IMapStateInteractor.kt \
  app/src/main/java/ru/lobotino/walktraveller/usecases/MapStateInteractor.kt \
  app/src/test/java/ru/lobotino/walktraveller/usecases/MapStateInteractorTest.kt
git commit -m "Switch MapStateInteractor to MapCameraState (center + zoom)"
```

---

## Task 5: Update `MapViewModel` API + add unit tests for camera state

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`
- Create: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelCameraStateTest.kt`

The viewmodel changes are:
1. Add a new `restoreCameraStateFlow` (replay=1) and public `observeRestoreCameraState`.
2. Replace the body of `setupMapCenterToLastSeenLocation()` (and rename to `setupMapCameraToLastSeenState()`) to emit on the new flow with `mapStateInteractor.getLastCameraState()`.
3. Replace `fun onMapScrolled(mapPoint: MapPoint)` with `fun onCameraIdle(center: MapPoint, zoom: Double)` — keep the existing find-my-location button reset logic, but call `setLastCameraState`.
4. Delete `fun onMapZoomed()` (was a no-op TODO).

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelCameraStateTest.kt`:

```kotlin
package ru.lobotino.walktraveller.viewmodels

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IFinishPathWritingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.IResourceManager

class MapViewModelCameraStateTest {

    private lateinit var mapStateInteractor: IMapStateInteractor
    private lateinit var sut: MapViewModel

    @Before
    fun setUp() {
        mapStateInteractor = mockk(relaxed = true)
        sut = MapViewModel(
            notificationsPermissionsUseCase = mockk<IPermissionsUseCase>(relaxed = true),
            geoPermissionsUseCase = mockk<GeoPermissionsUseCase>(relaxed = true),
            finishPathWritingUseCase = mockk<IFinishPathWritingUseCase>(relaxed = true),
            userLocationInteractor = mockk<IUserLocationInteractor>(relaxed = true),
            mapPathsInteractor = mockk<IMapPathsInteractor>(relaxed = true),
            mapStateInteractor = mapStateInteractor,
            tileSourceInteractor = mockk<ITileSourceInteractor>(relaxed = true),
            writingPathStatesRepository = mockk<IWritingPathStatesRepository>(relaxed = true),
            pathRatingUseCase = mockk<IPathRatingUseCase>(relaxed = true),
            userRotationRepository = mockk<IUserRotationRepository>(relaxed = true),
            userInfoRepository = mockk<IUserInfoRepository>(relaxed = true),
            resourceManager = mockk<IResourceManager>(relaxed = true),
            analyticsTracker = mockk<IAnalyticsTracker>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `onCameraIdle saves the camera state via interactor`() {
        // given
        val center = MapPoint(12.0, 34.0)
        val zoom = 17.5

        // when
        sut.onCameraIdle(center, zoom)

        // then
        verify(exactly = 1) {
            mapStateInteractor.setLastCameraState(MapCameraState(center, zoom))
        }
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `onCameraIdle` and `MapCameraState` flow unresolved.

- [ ] **Step 3: Modify `MapViewModel.kt` — add the restore flow**

Open `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`.

Add this import next to the other `model.map` imports (alphabetical):

```kotlin
import ru.lobotino.walktraveller.model.map.MapCameraState
```

Add the private flow declaration with the other `MutableSharedFlow` declarations (group right after `newMapCenterFlow`):

```kotlin
    private val restoreCameraStateFlow =
        MutableSharedFlow<MapCameraState>(1, 0, BufferOverflow.DROP_OLDEST)
```

Add the public observer with the other `observeNewMapCenter` line (group near `observeNewMapCenter`):

```kotlin
    val observeRestoreCameraState: Flow<MapCameraState> = restoreCameraStateFlow
```

- [ ] **Step 4: Modify `MapViewModel.kt` — replace `setupMapCenterToLastSeenLocation`**

Find this code (around line 128):

```kotlin
    fun onInitFinish() {
        setupMapCenterToLastSeenLocation()
        startBackgroundCachingPaths()
        checkGeoPermissions()
        clearMap()
        syncWritingPathState()
        isInitialized = true
    }
```

Change `setupMapCenterToLastSeenLocation()` to `setupMapCameraToLastSeenState()` so `onInitFinish` reads:

```kotlin
    fun onInitFinish() {
        setupMapCameraToLastSeenState()
        startBackgroundCachingPaths()
        checkGeoPermissions()
        clearMap()
        syncWritingPathState()
        isInitialized = true
    }
```

Then find this function (around line 153):

```kotlin
    private fun setupMapCenterToLastSeenLocation() {
        newMapCenterFlow.tryEmit(mapStateInteractor.getLastSeenPoint())
    }
```

Replace it with:

```kotlin
    private fun setupMapCameraToLastSeenState() {
        restoreCameraStateFlow.tryEmit(mapStateInteractor.getLastCameraState())
    }
```

- [ ] **Step 5: Modify `MapViewModel.kt` — replace `onMapScrolled` and delete `onMapZoomed`**

Find this block (around line 416):

```kotlin
    fun onMapScrolled(mapPoint: MapPoint) {
        mapStateInteractor.setLastSeenPoint(mapPoint)

        if (mapUiStateFlow.value.findMyLocationButtonState == FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION) {
            mapUiStateFlow.update { mapUiState ->
                mapUiState.copy(
                    findMyLocationButtonState = FindMyLocationButtonState.DEFAULT
                )
            }
        }
    }

    fun onMapZoomed() {
        // TODO
    }
```

Replace with:

```kotlin
    fun onCameraIdle(center: MapPoint, zoom: Double) {
        mapStateInteractor.setLastCameraState(MapCameraState(center, zoom))

        if (mapUiStateFlow.value.findMyLocationButtonState == FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION) {
            mapUiStateFlow.update { mapUiState ->
                mapUiState.copy(
                    findMyLocationButtonState = FindMyLocationButtonState.DEFAULT
                )
            }
        }
    }
```

- [ ] **Step 6: Run the ViewModel tests**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.MapViewModelCameraStateTest"`
Expected: PASS.

- [ ] **Step 7: Run the existing fit-camera test to ensure no regression**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.MapViewModelFitCameraTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt \
  app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelCameraStateTest.kt
git commit -m "MapViewModel: restore/save camera state (center + zoom)"
```

---

## Task 6: Wire `MainMapFragment` to the new API

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

- [ ] **Step 1: Update the import**

In `MainMapFragment.kt`, find:

```kotlin
import ru.lobotino.walktraveller.repositories.LastSeenPointRepository
```

Replace with:

```kotlin
import ru.lobotino.walktraveller.repositories.MapCameraStateRepository
```

- [ ] **Step 2: Update the DI wiring**

Find this block (around line 666):

```kotlin
                        mapStateInteractor = MapStateInteractor(
                            LastSeenPointRepository(
                                sharedPreferences
                            )
                        ),
```

Replace with:

```kotlin
                        mapStateInteractor = MapStateInteractor(
                            MapCameraStateRepository(
                                sharedPreferences
                            )
                        ),
```

- [ ] **Step 3: Update the camera-idle listener**

Find (around line 285):

```kotlin
            mapView.getMapAsync { map ->
                mapLibreMap = map
                // onMapScrolled also resets the find-my-location button out of its
                // "center on current location" state, so this must fire after camera moves.
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { target ->
                        mapViewModel.onMapScrolled(target.toMapPoint())
                    }
                    val bounds = map.projection.visibleRegion.latLngBounds
                    pathController.onCameraIdle(
                        PathBounds(
                            minLat = bounds.latitudeSouth,
                            maxLat = bounds.latitudeNorth,
                            minLng = bounds.longitudeWest,
                            maxLng = bounds.longitudeEast,
                        )
                    )
                }
```

Replace the inner `target.let` block (preserving the comment, which still describes the find-my-location reset) with a call that passes zoom too:

```kotlin
            mapView.getMapAsync { map ->
                mapLibreMap = map
                // onCameraIdle also resets the find-my-location button out of its
                // "center on current location" state, so this must fire after camera moves.
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { target ->
                        mapViewModel.onCameraIdle(target.toMapPoint(), map.cameraPosition.zoom)
                    }
                    val bounds = map.projection.visibleRegion.latLngBounds
                    pathController.onCameraIdle(
                        PathBounds(
                            minLat = bounds.latitudeSouth,
                            maxLat = bounds.latitudeNorth,
                            minLng = bounds.longitudeWest,
                            maxLng = bounds.longitudeEast,
                        )
                    )
                }
```

- [ ] **Step 4: Subscribe to `observeRestoreCameraState`**

Find the `observeNewMapCenter.onEach { ... }.launchIn(...)` subscription block (around line 718):

```kotlin
                    observeNewMapCenter.onEach { newMapCenter ->
                        val map = mapLibreMap ?: return@onEach
                        val targetZoom = maxOf(map.cameraPosition.zoom, DEFAULT_COMFORT_ZOOM)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(newMapCenter.toLatLng(), targetZoom)
                        )
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

Immediately above it, insert the new restore subscription so it is registered before `observeNewMapCenter`:

```kotlin
                    observeRestoreCameraState.onEach { state ->
                        val map = mapLibreMap ?: return@onEach
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(state.center.toLatLng(), state.zoom)
                        )
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    observeNewMapCenter.onEach { newMapCenter ->
                        val map = mapLibreMap ?: return@onEach
                        val targetZoom = maxOf(map.cameraPosition.zoom, DEFAULT_COMFORT_ZOOM)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(newMapCenter.toLatLng(), targetZoom)
                        )
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

(Use `moveCamera` — no animation — on restore. The first paint should not look like a fly-in from the world view.)

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Wire MainMapFragment to restore camera state and save zoom on idle"
```

---

## Task 7: Delete the old `LastSeenPointRepository` files

**Files:**
- Delete: `app/src/main/java/ru/lobotino/walktraveller/repositories/LastSeenPointRepository.kt`
- Delete: `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/ILastSeenPointRepository.kt`

These are now fully unused (Tasks 1–6 migrated all references).

- [ ] **Step 1: Verify nothing references the old types**

Run:

```bash
grep -rn "LastSeenPointRepository\|ILastSeenPointRepository" app/src
```

Expected: no matches. If matches remain, fix the call sites before proceeding.

- [ ] **Step 2: Delete the files**

Run:

```bash
git rm \
  app/src/main/java/ru/lobotino/walktraveller/repositories/LastSeenPointRepository.kt \
  app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/ILastSeenPointRepository.kt
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git commit -m "Remove legacy LastSeenPointRepository (replaced by MapCameraStateRepository)"
```

---

## Task 8: Full verification

- [ ] **Step 1: Run the entire JVM unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL (no new errors against the existing baseline).

- [ ] **Step 3: Manual smoke check (on emulator or device)**

1. Fresh install / clear app data → launch.
2. Expected: map opens centered on Moscow at zoom ≈ 15 (close to street level, not the whole world).
3. Pan to another area, zoom in or out, fully close the app (swipe from recents).
4. Reopen.
5. Expected: map opens at the last panned location and the last zoom level.

(No commit needed for this step — verification only.)

- [ ] **Step 4: Final reference grep**

Run:

```bash
grep -rn "onMapScrolled\|onMapZoomed\|LastSeenPoint\|ILastSeenPoint\|getLastSeenPoint\|setLastSeenPoint" app/src
```

Expected: no matches in code (matches in `docs/` or commit history are fine).

---

## Self-Review Notes

- **Spec coverage:** Model (T1), Repository interface + impl + tests (T2, T3), Interactor + tests (T4), ViewModel + tests (T5), Fragment wiring (T6), Legacy cleanup (T7), Verification (T8). All spec sections covered.
- **Placeholders:** None — every test and edit shows the actual code.
- **Type/method consistency:** `MapCameraState(center, zoom)`, `getLastCameraState`/`setLastCameraState`, `onCameraIdle(center, zoom)`, `observeRestoreCameraState`, `setupMapCameraToLastSeenState`, `LAST_SEEN_ZOOM_TAG = "last_seen_zoom"`, `DEFAULT_ZOOM = 15.0` — names match across tasks.
- **Migration:** legacy installs that only have latitude/longitude keep their saved point and get `DEFAULT_ZOOM = 15.0` (Task 3, Step 1).

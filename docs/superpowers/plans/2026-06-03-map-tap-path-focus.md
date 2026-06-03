# Map-Tap Path Focus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a saved/outer path on the MapLibre map focuses it the same way a list short-tap does — amber row in the list, halo + focused-top on the map — and smooth-scrolls the list to the tapped row.

**Architecture:** Hit-test lives in `MapLibrePathController.findClosestPathAt(point, tolerancePx, projection)`, which queries `queryRenderedFeatures` over installed per-path layers and picks the polyline with minimum screen-pixel distance to the tap. `MainMapFragment` wires an `OnMapClickListener`, then forwards the resolved `pathId` to `PathsMenuViewModel.onPathTappedOnMap(pathId)`. That VM method routes through the existing `setFocusedPath(...)` (which already drives halo + row state) and additionally emits a new `MapEvent.ScrollListToPath(menuType, pathId)` that the fragment dispatches to the corresponding paths view's `scrollToPath(id)`.

**Tech Stack:** Kotlin, MapLibre Android SDK (`OnMapClickListener`, `Projection.toScreenLocation`, `Style.queryRenderedFeatures`), JUnit + mockk for VM tests, Android RecyclerView (`smoothScrollToPosition`).

---

## File Structure

- **Create**
  - `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTest.kt` — pure helper to pick the closest polyline among candidates in screen space (testable on JVM).
  - `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTestTest.kt` — unit tests for the helper.
  - `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt` — VM tests for `onPathTappedOnMap`.

- **Modify**
  - `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt` — new event `ScrollListToPath`.
  - `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt` — track `currentBottomMenuState`, add `onPathTappedOnMap`.
  - `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt` — add `findClosestPathAt` + small helper structures.
  - `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt` — add `indexOfPath(pathId)`.
  - `app/src/main/java/ru/lobotino/walktraveller/ui/view/MyPathsMenuView.kt` — add `scrollToPath(pathId)`.
  - `app/src/main/java/ru/lobotino/walktraveller/ui/view/OuterPathsMenuView.kt` — add `scrollToPath(pathId)`.
  - `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt` — install `OnMapClickListener`, handle `MapEvent.ScrollListToPath`.

---

## Task 1: Add `MapEvent.ScrollListToPath`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt`

- [ ] **Step 1: Add the event class**

In `MapEvent.kt`, add inside the sealed class (after `SetFocusedPath`):

```kotlin
class ScrollListToPath(val pathsMenuType: PathsMenuType, val pathId: Long) : MapEvent()
```

Add the import at the top of the file:

```kotlin
import ru.lobotino.walktraveller.ui.model.PathsMenuType
```

(If `PathsMenuType` is already in `ui.model`, the import resolves trivially — verify by opening the file.)

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt
git commit -m "Add MapEvent.ScrollListToPath"
```

---

## Task 2: Track `currentBottomMenuState` in `PathsMenuViewModel`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt:145-153`

This field will be used by `onPathTappedOnMap` to decide which menu's focus to mutate. Update at the *start* of `emitBottomMenuStateChange` so any read after the call sees the new state.

- [ ] **Step 1: Add the private field**

Inside the `PathsMenuViewModel` class, alongside other private state (next to `focusedPathByMenu`):

```kotlin
private var currentBottomMenuState: BottomMenuState? = null
```

- [ ] **Step 2: Set the field at the top of `emitBottomMenuStateChange`**

Edit `emitBottomMenuStateChange(newState: BottomMenuState)` so it reads:

```kotlin
private fun emitBottomMenuStateChange(newState: BottomMenuState) {
    currentBottomMenuState = newState
    val leavingMyPaths = focusedPathByMenu[PathsMenuType.MY_PATHS] != null &&
        newState != BottomMenuState.MY_PATHS_MENU
    val leavingOuter = focusedPathByMenu[PathsMenuType.OUTER_PATHS] != null &&
        newState != BottomMenuState.OUTER_PATHS_MENU
    if (leavingMyPaths) setFocusedPath(PathsMenuType.MY_PATHS, null)
    if (leavingOuter) setFocusedPath(PathsMenuType.OUTER_PATHS, null)
    newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(newState))
}
```

(Only the first line — `currentBottomMenuState = newState` — is new.)

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt
git commit -m "Track currentBottomMenuState in PathsMenuViewModel"
```

---

## Task 3: VM test — map-tap when no menu open is a no-op

**Files:**
- Create: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Write the failing test scaffolding + first test**

```kotlin
package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class PathsMenuViewModelMapTapTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pathsSaverRepository: IPathsSaverRepository
    private lateinit var externalStoragePermissionsUseCase: IPermissionsUseCase
    private lateinit var mapPathsInteractor: IMapPathsInteractor
    private lateinit var outerPathsInteractor: IOuterPathsInteractor
    private lateinit var pathRedactor: IPathRedactor
    private lateinit var analyticsTracker: IAnalyticsTracker
    private lateinit var sut: PathsMenuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pathsSaverRepository = mockk(relaxed = true)
        externalStoragePermissionsUseCase = mockk(relaxed = true)
        mapPathsInteractor = mockk(relaxed = true)
        outerPathsInteractor = mockk(relaxed = true)
        pathRedactor = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        sut = PathsMenuViewModel(
            pathsSaverRepository,
            externalStoragePermissionsUseCase,
            mapPathsInteractor,
            outerPathsInteractor,
            pathRedactor,
            analyticsTracker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `map-tap when no menu open emits nothing`() = runTest(testDispatcher) {
        // given: VM in initial state (no menu opened yet — currentBottomMenuState is null)
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }
        advanceUntilIdle()
        val snapshot = collected.size

        // when
        sut.onPathTappedOnMap(pathId = 42L)
        advanceUntilIdle()
        job.cancel()

        // then
        val after = collected.drop(snapshot)
        assertTrue(after.none { it is MapEvent.SetFocusedPath })
        assertTrue(after.none { it is MapEvent.ScrollListToPath })
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL (compile error: `onPathTappedOnMap` does not exist)**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: COMPILE FAIL — `Unresolved reference: onPathTappedOnMap`.

- [ ] **Step 3: Add minimal `onPathTappedOnMap` to `PathsMenuViewModel` to make compile work**

In `PathsMenuViewModel.kt`, add (anywhere alongside other public methods, e.g. right after `onPathInListLongTap`):

```kotlin
fun onPathTappedOnMap(pathId: Long) {
    val menuType = activeMenuTypeForFocus() ?: return
    if (!isPathShown(menuType, pathId)) return

    setFocusedPath(menuType, pathId)

    val newFocus = focusedPathByMenu[menuType]
    if (newFocus != null) {
        newMapEventChannel.trySend(MapEvent.ScrollListToPath(menuType, pathId))
    }
}

private fun activeMenuTypeForFocus(): PathsMenuType? = when (currentBottomMenuState) {
    BottomMenuState.MY_PATHS_MENU -> PathsMenuType.MY_PATHS
    BottomMenuState.OUTER_PATHS_MENU -> PathsMenuType.OUTER_PATHS
    BottomMenuState.DEFAULT -> null
    null -> null
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Add PathsMenuViewModel.onPathTappedOnMap with no-menu no-op guard"
```

---

## Task 4: VM test — map-tap focuses shown MY path + emits ScrollListToPath

**Files:**
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Add helper for "menu open + shown path" arrangement**

At the bottom of the test class, add:

```kotlin
    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )

    /** Opens MY_PATHS_MENU and marks `pathId` as shown by routing through the
     *  standard show-button flow (same arrangement as PathsMenuViewModelFocusedPathTest). */
    private fun TestScope.givenShownRatingPathInOpenMyMenu(pathId: Long) {
        sut.onShowPathsMenuButtonClick()
        val path = ratingPath(pathId)
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
    }
```

- [ ] **Step 2: Add the test**

Append inside the test class:

```kotlin
    @Test
    fun `map-tap on shown MY path with MY menu open emits SetFocusedPath then ScrollListToPath`() =
        runTest(testDispatcher) {
            // given
            givenShownRatingPathInOpenMyMenu(42L)
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvent = after.filterIsInstance<MapEvent.SetFocusedPath>().single()
            val scrollEvent = after.filterIsInstance<MapEvent.ScrollListToPath>().single()
            assertEquals(42L, focusEvent.pathId)
            assertEquals(PathsMenuType.MY_PATHS, scrollEvent.pathsMenuType)
            assertEquals(42L, scrollEvent.pathId)
            assertTrue(
                "Expected SetFocusedPath before ScrollListToPath",
                after.indexOf(focusEvent) < after.indexOf(scrollEvent),
            )
            assertTrue(
                "Expected no FitCameraToBounds on map-tap",
                after.none { it is MapEvent.FitCameraToBounds },
            )
        }
```

- [ ] **Step 3: Run the test — expect PASS** (implementation already exists from Task 3)

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: both tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Test: map-tap on shown MY path emits SetFocusedPath + ScrollListToPath"
```

---

## Task 5: VM test — toggle-off on re-tap does not scroll

**Files:**
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Add the test**

```kotlin
    @Test
    fun `map-tap on already-focused path clears focus and does not scroll`() =
        runTest(testDispatcher) {
            // given: path 42 is focused via a prior map-tap
            givenShownRatingPathInOpenMyMenu(42L)
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: tap again on the same path
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
            assertTrue(
                "Expected no ScrollListToPath on toggle-off",
                after.none { it is MapEvent.ScrollListToPath },
            )
        }
```

- [ ] **Step 2: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Test: map-tap on already-focused path clears focus without scrolling"
```

---

## Task 6: VM test — map-tap swaps focus to second shown path

**Files:**
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Add the test**

```kotlin
    @Test
    fun `map-tap on second shown path swaps focus and scrolls to new`() =
        runTest(testDispatcher) {
            // given: path 42 focused, path 43 shown
            givenShownRatingPathInOpenMyMenu(42L)
            givenShownRatingPathInOpenMyMenu(43L)
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(43L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(43L), focusEvents.map { it.pathId })
            val scrollEvents = after.filterIsInstance<MapEvent.ScrollListToPath>()
            assertEquals(1, scrollEvents.size)
            assertEquals(43L, scrollEvents.single().pathId)
        }
```

- [ ] **Step 2: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Test: map-tap swaps focus and scrolls to the new path"
```

---

## Task 7: VM test — map-tap on hidden path / wrong menu is no-op

**Files:**
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Add tests covering both cases**

```kotlin
    @Test
    fun `map-tap on path that is not shown in active menu is no-op`() =
        runTest(testDispatcher) {
            // given: MY menu open, no shown paths
            sut.onShowPathsMenuButtonClick()
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(99L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            assertTrue(after.none { it is MapEvent.SetFocusedPath })
            assertTrue(after.none { it is MapEvent.ScrollListToPath })
        }

    @Test
    fun `map-tap on path shown only in OUTER while MY menu open is no-op`() =
        runTest(testDispatcher) {
            // given: MY menu open with one shown MY path; an OUTER path with id 7
            // was previously shown but OUTER menu is not currently open.
            sut.onShowPathsMenuButtonClick()
            advanceUntilIdle()
            // Make pathId 7 known to OUTER (would be shown if its menu were open).
            every { outerPathsInteractor.getCachedOuterPath(7L) } returns ratingPath(7L)

            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: user taps the position of an outer path
            sut.onPathTappedOnMap(7L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            assertTrue(after.none { it is MapEvent.SetFocusedPath })
            assertTrue(after.none { it is MapEvent.ScrollListToPath })
        }
```

- [ ] **Step 2: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Test: map-tap on hidden or other-menu path is a no-op"
```

---

## Task 8: VM test — map-tap works for OUTER_PATHS menu

**Files:**
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt`

- [ ] **Step 1: Add helper and test for OUTER_PATHS**

Add helper at the bottom (after the MY helper):

```kotlin
    /** Opens OUTER_PATHS_MENU via shared-uri flow and marks `pathId` as shown. */
    private fun TestScope.givenShownOuterPathInOpenOuterMenu(pathId: Long) {
        val sharedUri = mockk<android.net.Uri>()
        coEvery { outerPathsInteractor.getAllPaths(sharedUri) } returns emptyList()
        sut.onResume(sharedUri)
        advanceUntilIdle()
        every { outerPathsInteractor.getCachedOuterPath(pathId) } returns ratingPath(pathId)
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.OUTER_PATHS,
        )
        advanceUntilIdle()
    }
```

Add test:

```kotlin
    @Test
    fun `map-tap on shown OUTER path with OUTER menu open emits ScrollListToPath OUTER`() =
        runTest(testDispatcher) {
            // given
            givenShownOuterPathInOpenOuterMenu(7L)
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(7L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvent = after.filterIsInstance<MapEvent.SetFocusedPath>().single()
            val scrollEvent = after.filterIsInstance<MapEvent.ScrollListToPath>().single()
            assertEquals(7L, focusEvent.pathId)
            assertEquals(PathsMenuType.OUTER_PATHS, scrollEvent.pathsMenuType)
            assertEquals(7L, scrollEvent.pathId)
        }
```

Note: `android.net.Uri` is a stable mock target on JVM under mockk; the existing `PathsMenuViewModelFocusedPathTest` already mocks it the same way.

- [ ] **Step 2: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelMapTapTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelMapTapTest.kt
git commit -m "Test: map-tap routes through OUTER_PATHS when OUTER menu open"
```

---

## Task 9: Pure helper — `PolylineHitTest`

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTest.kt`
- Create: `app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTestTest.kt`

The hit-test for "closest polyline within tolerance" is pure math on screen-space points. Extracting it lets us unit-test on JVM without MapLibre.

- [ ] **Step 1: Write the failing tests**

Create `PolylineHitTestTest.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolylineHitTestTest {

    @Test
    fun `returns null when no candidates`() {
        // given
        val tap = PointF(10f, 10f)

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = emptyList(),
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }

    @Test
    fun `returns null when all candidates beyond tolerance`() {
        // given
        val tap = PointF(0f, 0f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(PointF(100f, 100f), PointF(200f, 200f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }

    @Test
    fun `returns the id whose polyline is closest to the tap`() {
        // given: two candidates — one far (id 1), one near (id 2)
        val tap = PointF(50f, 50f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(PointF(0f, 0f), PointF(10f, 10f)),
            ),
            PolylineHitTest.Candidate(
                pathId = 2L,
                vertices = listOf(PointF(40f, 40f), PointF(60f, 60f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertEquals(2L, result)
    }

    @Test
    fun `returns id when tap lies on the segment within tolerance`() {
        // given: a horizontal segment from (0,10) to (100,10); tap at (50,12)
        val tap = PointF(50f, 12f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 99L,
                vertices = listOf(PointF(0f, 10f), PointF(100f, 10f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertEquals(99L, result)
    }

    @Test
    fun `ignores a single-vertex candidate (no segment)`() {
        // given
        val tap = PointF(0f, 0f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(PointF(0f, 0f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }
}
```

`android.graphics.PointF` is available on JVM under the Android stub jar that already supports the existing JVM tests in this module. If a test fails with `RuntimeException: Method ... not mocked` on `PointF`, switch the helper signature to take two `Float`s per point and adapt these tests (the production call site converts `LatLng → PointF`, so the conversion to plain `Float` happens there). Verify in step 2 below.

- [ ] **Step 2: Run the test — expect compile FAIL (`PolylineHitTest` not defined)**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PolylineHitTestTest"`
Expected: COMPILE FAIL.

- [ ] **Step 3: Write `PolylineHitTest`**

Create `PolylineHitTest.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Pure screen-space hit-test for picking the polyline closest to a tap point.
 * Inputs are in pixels; the caller converts LatLng → PointF via MapLibre's projection.
 */
object PolylineHitTest {

    data class Candidate(val pathId: Long, val vertices: List<PointF>)

    /**
     * Returns the pathId whose polyline has the smallest perpendicular distance
     * to [tap], provided that distance is ≤ [tolerancePx]. Candidates with fewer
     * than two vertices are skipped (no segment to measure against).
     */
    fun closestPathIdWithin(
        tap: PointF,
        candidates: List<Candidate>,
        tolerancePx: Float,
    ): Long? {
        var bestId: Long? = null
        var bestDist = Float.MAX_VALUE
        for (candidate in candidates) {
            val vertices = candidate.vertices
            if (vertices.size < 2) continue
            var dist = Float.MAX_VALUE
            for (i in 0 until vertices.size - 1) {
                val d = distancePointToSegment(tap, vertices[i], vertices[i + 1])
                if (d < dist) dist = d
            }
            if (dist < bestDist) {
                bestDist = dist
                bestId = candidate.pathId
            }
        }
        return if (bestDist <= tolerancePx) bestId else null
    }

    private fun distancePointToSegment(p: PointF, a: PointF, b: PointF): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq == 0f) {
            val dx = p.x - a.x
            val dy = p.y - a.y
            return sqrt(dx * dx + dy * dy)
        }
        val t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
        val tc = t.coerceIn(0f, 1f)
        val cx = a.x + tc * abx
        val cy = a.y + tc * aby
        val dx = p.x - cx
        val dy = p.y - cy
        return sqrt(dx * dx + dy * dy)
    }
}
```

- [ ] **Step 4: Run the tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.maplibre.PolylineHitTestTest"`
Expected: PASS.

If you hit a "PointF not mocked" runtime error, edit the file to declare:

```kotlin
// at top of test file
@org.junit.Before
fun stubPointF() {
    // not needed — fallback only if PointF instantiation fails on JVM
}
```

That stub does nothing, but the more likely fix is to replace `PointF(x, y)` in tests with a tiny local wrapper. If that becomes necessary: change `Candidate.vertices: List<PointF>` to `List<Pair<Float, Float>>` and update `closestPathIdWithin` accordingly. Re-run the tests until green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTest.kt \
        app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PolylineHitTestTest.kt
git commit -m "Add PolylineHitTest helper with screen-space distance unit tests"
```

---

## Task 10: `MapLibrePathController.findClosestPathAt`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

Glue: collect candidate paths whose layers are installed, ask MapLibre which ones the tap intersects, convert vertices to screen space, and delegate to `PolylineHitTest`.

- [ ] **Step 1: Add the supporting imports**

Add at the top of `MapLibrePathController.kt` (alongside existing imports):

```kotlin
import android.graphics.PointF
import android.graphics.RectF
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Projection
```

- [ ] **Step 2: Add the public method on `MapLibrePathController` (place near `setFocusedPath`)**

`queryRenderedFeatures` is a `MapLibreMap` method (not `Style`), so the controller takes the query as a functional parameter to stay decoupled from `MapLibreMap`.

```kotlin
/**
 * Returns the pathId of the saved rating or common path whose rendered polyline
 * is closest to [screenPoint], within [tolerancePx] (screen pixels). Returns null
 * if no candidate is within tolerance or no style is loaded yet.
 *
 * [queryRenderedFeatures] is a function (typically `map::queryRenderedFeatures`
 * with the layer-id list curried in by the caller) that returns the features
 * MapLibre considers intersected within the hit rectangle. The controller knows
 * the candidate layer ids and passes them; the caller supplies the live map
 * reference because it lives on MapLibreMap, not Style.
 *
 * Must be called on Main (touches Style + Projection).
 */
fun findClosestPathAt(
    screenPoint: PointF,
    tolerancePx: Float,
    projection: Projection,
    queryRenderedFeatures: (hitRect: RectF, layerIds: List<String>) -> List<Feature>,
): Long? {
    if (this.style == null) return null

    val candidateLayerIds = buildList {
        for (id in installedRatingIds) add(ratingLayerId(id))
        add(LAYER_SAVED_COMMON)
        add(LAYER_FOCUSED_TOP)
    }
    if (candidateLayerIds.isEmpty()) return null

    val hitRect = RectF(
        screenPoint.x - tolerancePx,
        screenPoint.y - tolerancePx,
        screenPoint.x + tolerancePx,
        screenPoint.y + tolerancePx,
    )

    val featureIds = LinkedHashSet<Long>()
    for (feature in queryRenderedFeatures(hitRect, candidateLayerIds)) {
        val id = feature.getNumberProperty(PathGeoJsonMapper.PROPERTY_PATH_ID)
            ?.toLong() ?: continue
        featureIds.add(id)
    }
    if (featureIds.isEmpty()) return null

    val candidates = ArrayList<PolylineHitTest.Candidate>(featureIds.size)
    for (id in featureIds) {
        val vertices = pathVerticesInScreenSpace(id, projection) ?: continue
        if (vertices.size >= 2) {
            candidates.add(PolylineHitTest.Candidate(pathId = id, vertices = vertices))
        }
    }

    return PolylineHitTest.closestPathIdWithin(
        tap = screenPoint,
        candidates = candidates,
        tolerancePx = tolerancePx,
    )
}

private fun pathVerticesInScreenSpace(
    pathId: Long,
    projection: Projection,
): List<PointF>? {
    val rating = savedRatingPaths[pathId]
    if (rating != null) {
        val points = ArrayList<PointF>(rating.pathSegments.size + 1)
        for ((index, seg) in rating.pathSegments.withIndex()) {
            val start = projection.toScreenLocation(
                LatLng(seg.startPoint.latitude, seg.startPoint.longitude)
            )
            if (index == 0) {
                points.add(start)
            } else if (points.isEmpty() || start != points.last()) {
                points.add(start)
            }
            val finish = projection.toScreenLocation(
                LatLng(seg.finishPoint.latitude, seg.finishPoint.longitude)
            )
            if (points.isEmpty() || finish != points.last()) {
                points.add(finish)
            }
        }
        return points
    }
    val common = savedCommonPaths[pathId] ?: return null
    val points = ArrayList<PointF>(common.pathPoints.size)
    for (p in common.pathPoints) {
        points.add(projection.toScreenLocation(LatLng(p.latitude, p.longitude)))
    }
    return points
}
```

Add the import for `Feature` if not already present (it is — the file already uses `org.maplibre.geojson.Feature`).

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt
git commit -m "Add MapLibrePathController.findClosestPathAt hit-test"
```

---

## Task 11: `PathsInfoAdapter.indexOfPath`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt`

- [ ] **Step 1: Add the method**

Insert after `getAllPathsItemsIds()`:

```kotlin
fun indexOfPath(pathId: Long): Int =
    pathsItems.indexOfFirst { it.pathInfo.pathId == pathId }
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt
git commit -m "Add PathsInfoAdapter.indexOfPath"
```

---

## Task 12: `MyPathsMenuView.scrollToPath` and `OuterPathsMenuView.scrollToPath`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/view/MyPathsMenuView.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/view/OuterPathsMenuView.kt`

- [ ] **Step 1: Add `scrollToPath` to `MyPathsMenuView`**

Append, alongside other public methods (e.g. just after `syncPathInfoItemState`):

```kotlin
fun scrollToPath(pathId: Long) {
    val index = pathsInfoListAdapter.indexOfPath(pathId)
    if (index >= 0) pathsInfoList.smoothScrollToPosition(index)
}
```

- [ ] **Step 2: Add `scrollToPath` to `OuterPathsMenuView`**

```kotlin
fun scrollToPath(pathId: Long) {
    val index = pathsInfoListAdapter.indexOfPath(pathId)
    if (index >= 0) pathsInfoList.smoothScrollToPosition(index)
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/view/MyPathsMenuView.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/view/OuterPathsMenuView.kt
git commit -m "Add scrollToPath to MyPathsMenuView and OuterPathsMenuView"
```

---

## Task 13: Wire `OnMapClickListener` + handle `ScrollListToPath` in `MainMapFragment`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

- [ ] **Step 1: Add imports**

Near the existing MapLibre imports:

```kotlin
import android.graphics.RectF
import org.maplibre.android.geometry.LatLng
```

(`PointF` is already in scope transitively, but if needed: `import android.graphics.PointF`.)

- [ ] **Step 2: Add a private constant for tap tolerance**

In `MainMapFragment`, near other private constants (search for `companion object` or for a similar `DEFAULT_COMFORT_ZOOM`):

If there is a `companion object`, add:

```kotlin
private const val MAP_TAP_TOLERANCE_DP = 24f
```

Otherwise, add a `companion object` to the class:

```kotlin
companion object {
    private const val MAP_TAP_TOLERANCE_DP = 24f
}
```

- [ ] **Step 3: Register the click listener inside `getMapAsync`**

Inside the existing `mapView.getMapAsync { map -> ... }` block (around `MainMapFragment.kt:281`), after the existing `addOnCameraIdleListener { ... }` block and before `currentStyleUrl?.let { applyStyle(map, it) }`, add:

```kotlin
map.addOnMapClickListener { latLng ->
    val ctx = context ?: return@addOnMapClickListener false
    val screenPoint = map.projection.toScreenLocation(latLng)
    val tolerancePx = Utils.convertDpToPixel(ctx, MAP_TAP_TOLERANCE_DP)
    val pathId = pathController.findClosestPathAt(
        screenPoint = screenPoint,
        tolerancePx = tolerancePx,
        projection = map.projection,
        queryRenderedFeatures = { hitRect, layerIds ->
            map.queryRenderedFeatures(hitRect, *layerIds.toTypedArray())
        },
    )
    if (pathId != null) {
        menuViewModel.onPathTappedOnMap(pathId)
    }
    false
}
```

Add the import for `Utils` if it isn't already imported (it is — `MainMapFragment` already uses `Utils.convertDpToPixel` elsewhere; verify via the existing imports list).

- [ ] **Step 4: Handle `MapEvent.ScrollListToPath` in `observeNewMapEvent`**

Inside the `when (mapEvent) { ... }` block at `MainMapFragment.kt:514`, add a new branch (place it near `SetFocusedPath`):

```kotlin
is MapEvent.ScrollListToPath -> {
    when (mapEvent.pathsMenuType) {
        PathsMenuType.MY_PATHS -> myPathsMenu.scrollToPath(mapEvent.pathId)
        PathsMenuType.OUTER_PATHS -> outerPathsMenu.scrollToPath(mapEvent.pathId)
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS. If any pre-existing test fails, stop and investigate before continuing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Wire OnMapClickListener and ScrollListToPath handling in MainMapFragment"
```

---

## Task 14: Manual verification on emulator

**Goal:** Make sure the end-to-end flow works in the real app — type checking and unit tests verify code, not behaviour.

- [ ] **Step 1: Build and install the debug APK**

Run: `./gradlew :app:installDebug`
Expected: installation succeeds. (Use the project's existing emulator workflow.)

- [ ] **Step 2: Verify MY_PATHS map-tap**

Steps:
1. Open the app, open the my-paths menu (the bottom menu button), tap "show" on 2–3 saved rating paths.
2. Tap directly on one of the paths on the map.

Expected:
- That path gets the white halo and the focused-top render.
- Its row in the list turns amber (`focused_path_background`).
- The list smooth-scrolls to that row.
- The camera does **not** move.

- [ ] **Step 3: Verify toggle-off**

Steps: tap the focused path again on the map.
Expected: halo + amber disappear; list does **not** scroll.

- [ ] **Step 4: Verify focus swap**

Steps: tap another shown path on the map.
Expected: halo + amber move to the new path; list smooth-scrolls to the new row.

- [ ] **Step 5: Verify empty-area tap is no-op**

Steps: tap somewhere far from any path.
Expected: nothing happens. Focus, halo, scroll, camera all unchanged.

- [ ] **Step 6: Verify DEFAULT-state map-tap is no-op**

Steps: press the back button on the my-paths menu to collapse it (bottom-menu becomes DEFAULT). Then tap on a path's position on the map (paths may still be visible).
Expected: nothing happens.

- [ ] **Step 7: Verify OUTER_PATHS map-tap**

Steps: import a path via share intent (or load via the test-only mechanism the project uses), so OUTER_PATHS_MENU opens. Show outer paths. Tap on one on the map.
Expected: same as Step 2 but in the outer menu.

- [ ] **Step 8: Verify tap-tolerance feels right**

Steps: try tapping a few pixels off thin lines.
Expected: 24dp tolerance feels accurate. If consistently misses, increase to 32f and re-test; if often selects wrong path, decrease to 16f. Tuning value goes in `MainMapFragment.MAP_TAP_TOLERANCE_DP`.

- [ ] **Step 9: Commit any tolerance tuning**

If `MAP_TAP_TOLERANCE_DP` changed:

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Tune MAP_TAP_TOLERANCE_DP after manual verification"
```

Otherwise skip.

---

## Task 15: Final cleanup

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 2: Verify no leftover TODOs in the new code**

Run: `git diff main...HEAD -- app/src/main app/src/test | grep -iE 'TODO|FIXME|XXX' || echo "clean"`
Expected: `clean`.

- [ ] **Step 3: Confirm the branch state**

Run: `git log --oneline main..HEAD`
Expected: ~12–14 small commits, one per task step, in the order from this plan.

The spec and this plan will be removed in a separate cleanup commit by the user after the feature ships (per the project convention).

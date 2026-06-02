# Focused Path Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Highlight the path the user just tapped in the list — both on the map (white halo beneath the line) and in the list (amber row background) — so that with many shown paths, the focused one is immediately identifiable. One focused path per menu, mutually exclusive with multi-select.

**Architecture:** A new `focusedPathByMenu: Map<PathsMenuType, Long?>` lives in `PathsMenuViewModel`. State is projected one-way to (a) list rows via the existing `newPathInfoListItemStateFlow` (a new `isFocused` field is added to `PathInfoItemModel` / `PathInfoItemState`) and (b) the map via a new `MapEvent.SetFocusedPath(Long?)` → `MapViewModel.setFocusedPathOnMap` Flow → `MapLibrePathController.setFocusedPath`. The map renders a single shared halo layer (wider white line) beneath the per-path rating/common layers.

**Tech Stack:** Kotlin, Android, MapLibre Android SDK, Kotlin coroutines (Flow / Channel), JUnit 4, MockK, kotlinx-coroutines-test.

---

## Files

**Create:**
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt` — unit tests for focus state transitions

**Modify:**
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemModel.kt` — add `isFocused` field
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemState.kt` — add `isFocused: Boolean?` field
- `app/src/main/res/values/colors.xml` — add `focused_path_background`
- `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt` — apply `isFocused`, new background color, priority logic
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt` — add `SetFocusedPath`
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt` — `focusedPathByMenu` state and all lifecycle transitions
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt` — `setFocusedPathOnMap` + new Flow
- `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt` — handle `MapEvent.SetFocusedPath`, observe the new Flow
- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt` — halo source/layer, `setFocusedPath`, retain `savedCommonPaths`
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt` — update existing short-tap expectations (now also emits `SetFocusedPath` before `FitCameraToBounds`)

---

## Task 1: Add `SetFocusedPath` event and fragment when-branch (compile scaffolding)

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

This unblocks compilation for the next tasks. Implementation is a no-op stub for now.

- [ ] **Step 1: Add `SetFocusedPath` to the `MapEvent` sealed class**

In `MapEvent.kt`, alongside the other `MapEvent` subclasses, add:

```kotlin
class SetFocusedPath(val pathId: Long?) : MapEvent()
```

- [ ] **Step 2: Add stub `when` branch in `MainMapFragment.observeNewMapEvent`**

In `MainMapFragment.kt`, inside the `observeNewMapEvent.onEach { mapEvent -> when (mapEvent) { ... } }` block, add (placement: next to `is MapEvent.FitCameraToBounds`):

```kotlin
is MapEvent.SetFocusedPath -> {
    // wired up in Task 9
}
```

- [ ] **Step 3: Build to confirm compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Add MapEvent.SetFocusedPath with stub fragment branch"
```

---

## Task 2: Add `isFocused` to list item model and state

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemModel.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemState.kt`

- [ ] **Step 1: Add `isFocused` to `PathInfoItemModel`**

```kotlin
data class PathInfoItemModel(
    val pathInfo: MapPathInfo,
    var showButtonState: PathInfoItemShowButtonState = PathInfoItemShowButtonState.DEFAULT,
    var shareButtonState: PathInfoItemShareButtonState = PathInfoItemShareButtonState.DEFAULT,
    var isSelected: Boolean = false,
    var isFocused: Boolean = false,
)
```

- [ ] **Step 2: Add `isFocused: Boolean?` to `PathInfoItemState`**

```kotlin
data class PathInfoItemState(
    val pathsToAction: PathsToAction,
    val showButtonState: PathInfoItemShowButtonState? = null,
    val shareButtonState: PathInfoItemShareButtonState? = null,
    val isSelected: Boolean? = null,
    val isFocused: Boolean? = null,
)
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemModel.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/model/PathInfoItemState.kt
git commit -m "Add isFocused to PathInfoItemModel and PathInfoItemState"
```

---

## Task 3: List background color resource + adapter wiring

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt`

- [ ] **Step 1: Add new color resource**

In `app/src/main/res/values/colors.xml`, alongside `primary_green_light`, add:

```xml
<color name="focused_path_background">#FFF4D6</color>
```

- [ ] **Step 2: Add `focusedItemBackgroundColor` field and load it**

In `PathsInfoAdapter.kt`, alongside `defaultItemBackgroundColor` / `selectedItemBackgroundColor`:

```kotlin
protected var focusedItemBackgroundColor by Delegates.notNull<Int>()
```

In the `init` block, after the existing color loads:

```kotlin
focusedItemBackgroundColor = ContextCompat.getColor(context, R.color.focused_path_background)
```

- [ ] **Step 3: Update `ViewHolder.bind` background priority**

Replace the existing `itemBackground.setBackgroundColor(if (path.isSelected) ... else ...)` block with:

```kotlin
itemBackground.setBackgroundColor(
    when {
        path.isSelected -> selectedItemBackgroundColor
        path.isFocused  -> focusedItemBackgroundColor
        else            -> defaultItemBackgroundColor
    }
)
```

- [ ] **Step 4: Update the partial-update applier**

In `PathsInfoAdapter.kt`, in the `updatePaths(...)` method's per-item update block (next to the existing `if (pathInfoItemState.isSelected != null) item.isSelected = pathInfoItemState.isSelected`):

```kotlin
if (pathInfoItemState.isFocused != null) {
    item.isFocused = pathInfoItemState.isFocused
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/colors.xml \
        app/src/main/java/ru/lobotino/walktraveller/ui/adapter/PathsInfoAdapter.kt
git commit -m "Render focused path row with amber background"
```

---

## Task 4: VM state + short-tap focus transitions (TDD core)

**Files:**
- Create: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt`

**Convention** (per repo memory):
- SUT named `sut`
- `@Before` / `@After` setup/teardown
- Test bodies split into `given` / `when` / `then` blocks via comments

- [ ] **Step 1: Create new test file with the boilerplate**

Create `PathsMenuViewModelFocusedPathTest.kt`. Copy the `@Before`/`@After` setUp block verbatim from `PathsMenuViewModelShortTapTest.kt` (same mocks, same SUT construction, same `Dispatchers.setMain`/`resetMain`). The class declaration, imports, and lifecycle methods mirror that file 1:1.

- [ ] **Step 2: Write failing test — short-tap on shown non-focused emits SetFocusedPath then FitCameraToBounds**

Add to the new file:

```kotlin
@Test
fun `short-tap on shown non-focused path emits SetFocusedPath then FitCameraToBounds`() =
    runTest(testDispatcher) {
        // given: a shown path with id=42 in MY_PATHS, no current focus
        givenShownRatingPath(pathId = 42L)
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvent = collected.filterIsInstance<MapEvent.SetFocusedPath>().single()
        val fitEvent   = collected.filterIsInstance<MapEvent.FitCameraToBounds>().single()
        assertEquals(42L, focusEvent.pathId)
        assertTrue(
            collected.indexOf(focusEvent) < collected.indexOf(fitEvent)
        )
    }
```

Add a private helper `givenShownRatingPath(pathId: Long)` that stubs the repository/`pathRedactor` mocks so `isPathShown` returns true and `resolveShownPath` returns a `MapRatingPath` with non-empty bounds. Mirror how `PathsMenuViewModelShortTapTest` already arranges a shown path — copy that snippet.

- [ ] **Step 3: Run test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest*"`
Expected: FAIL — `MapEvent.SetFocusedPath` is never emitted (filterIsInstance returns empty list, `.single()` throws).

- [ ] **Step 4: Add state field to `PathsMenuViewModel`**

Near `shownPathIdsByMenu`:

```kotlin
private val focusedPathByMenu: MutableMap<PathsMenuType, Long?> = mutableMapOf(
    PathsMenuType.MY_PATHS to null,
    PathsMenuType.OUTER_PATHS to null,
)
```

- [ ] **Step 5: Add private helper for emitting list-row focus state**

In `PathsMenuViewModel`, add (near the other private helpers that use `newPathInfoListItemStateFlow`):

```kotlin
private fun emitListItemFocus(type: PathsMenuType, pathId: Long, isFocused: Boolean) {
    newPathInfoListItemStateFlow.tryEmit(
        NewPathInfoItemState(
            type,
            PathInfoItemState(
                pathsToAction = PathsToAction.Single(pathId),
                isFocused = isFocused,
            ),
        )
    )
}
```

- [ ] **Step 6: Add private `setFocusedPath` helper**

```kotlin
private fun setFocusedPath(type: PathsMenuType, newId: Long?) {
    val oldId = focusedPathByMenu[type]
    if (oldId == newId) return
    focusedPathByMenu[type] = newId
    if (oldId != null) emitListItemFocus(type, oldId, isFocused = false)
    if (newId != null) emitListItemFocus(type, newId, isFocused = true)
    newMapEventChannel.trySend(MapEvent.SetFocusedPath(newId))
}
```

- [ ] **Step 7: Wire focus into `onPathInListShortTap`**

Replace the existing body of `onPathInListShortTap(pathId, pathsMenuType)` with:

```kotlin
fun onPathInListShortTap(pathId: Long, pathsMenuType: PathsMenuType) {
    val inSelectMode = when (pathsMenuType) {
        PathsMenuType.MY_PATHS    -> myPathsMenuUiStateFlow.value.inSelectMode
        PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
    }
    if (inSelectMode) {
        toggleMenuItemSelect(pathId, pathsMenuType)
        return
    }
    if (!isPathShown(pathsMenuType, pathId)) {
        return
    }

    val currentFocused = focusedPathByMenu[pathsMenuType]
    if (currentFocused == pathId) {
        // toggle off — no camera move
        setFocusedPath(pathsMenuType, null)
        return
    }

    setFocusedPath(pathsMenuType, pathId)

    viewModelScope.launch {
        val path = resolveShownPath(pathId, pathsMenuType) ?: return@launch
        val bounds = pathBounds(path) ?: return@launch
        newMapEventChannel.trySend(MapEvent.FitCameraToBounds(bounds))
    }
}
```

- [ ] **Step 8: Run test to verify passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest*"`
Expected: PASS.

- [ ] **Step 9: Add the remaining short-tap focus tests**

Add to the same test file. Use the same `givenShownRatingPath` helper.

```kotlin
@Test
fun `tap on already-focused path clears focus and does not move camera`() =
    runTest(testDispatcher) {
        // given
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS) // first tap → focuses
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when: second tap on the same path
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
        assertTrue(collected.none { it is MapEvent.FitCameraToBounds })
    }

@Test
fun `tap on second shown path swaps focus and fits camera to new one`() =
    runTest(testDispatcher) {
        // given
        givenShownRatingPath(42L)
        givenShownRatingPath(43L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onPathInListShortTap(43L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(43L), focusEvents.map { it.pathId })
        val fitEvents = collected.filterIsInstance<MapEvent.FitCameraToBounds>()
        assertEquals(1, fitEvents.size)
    }

@Test
fun `tap on hidden path leaves focus unchanged and emits nothing`() =
    runTest(testDispatcher) {
        // given a hidden path (no givenShownRatingPath call)
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onPathInListShortTap(99L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        assertTrue(collected.none { it is MapEvent.SetFocusedPath })
        assertTrue(collected.none { it is MapEvent.FitCameraToBounds })
    }

@Test
fun `focus in MY_PATHS does not affect OUTER_PATHS focus state`() =
    runTest(testDispatcher) {
        // given
        givenShownRatingPath(42L)              // MY_PATHS
        givenShownCommonPath(7L)               // OUTER_PATHS
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onPathInListShortTap(7L, PathsMenuType.OUTER_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusIds = collected.filterIsInstance<MapEvent.SetFocusedPath>()
            .map { it.pathId }
        assertEquals(listOf<Long?>(7L), focusIds)  // only OUTER focus event
    }
```

Add a `givenShownCommonPath(pathId)` helper mirroring the rating helper but for `MapCommonPath` / `PathsMenuType.OUTER_PATHS`. Refer to existing test arrangements for the exact mock-stubbing pattern.

- [ ] **Step 10: Update `PathsMenuViewModelShortTapTest` to account for the new event**

For any existing test in that file that asserts the exact contents of `observeNewMapEvent` after a short tap (e.g., `assertEquals(listOf(FitCameraToBounds(...)), collected)`), the assertion must now also account for the preceding `SetFocusedPath(id)` event. The minimal change: filter to the event type being asserted on before comparing, e.g. `collected.filterIsInstance<MapEvent.FitCameraToBounds>()`.

Open `PathsMenuViewModelShortTapTest.kt`, run the test class, find the failing tests, adjust assertions to filter-by-type rather than match the whole list.

- [ ] **Step 11: Run all VM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModel*"`
Expected: all PASS.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt
git commit -m "Track focused path per menu and emit SetFocusedPath on short-tap"
```

---

## Task 5: Clear focus when entering multi-select

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt`

- [ ] **Step 1: Write failing test**

In `PathsMenuViewModelFocusedPathTest.kt`:

```kotlin
@Test
fun `long-tap entering multi-select clears focus and emits SetFocusedPath null`() =
    runTest(testDispatcher) {
        // given: a focused path
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when: long-tap on the same (or any) path enters select mode
        sut.onPathInListLongTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
    }
```

- [ ] **Step 2: Run test to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.long-tap*"`
Expected: FAIL.

- [ ] **Step 3: Add focus clearing inside `toggleMenuItemSelect`**

`toggleMenuItemSelect` is called from both long-tap (entering select mode) and short-tap (toggling within select mode). We only need to clear on the transition into select mode. Locate the line that sets `inSelectMode = true` (in `syncMenuSelectMode`). Add the clear just before that flip happens, *inside* `syncMenuSelectMode` so we cover all entry paths. Concretely, at the top of `syncMenuSelectMode(pathsMenuType)`, capture the previous `inSelectMode` value, run the existing logic, and if the value transitions `false → true`, call `setFocusedPath(pathsMenuType, null)`.

Pseudocode-anchored to actual structure:

```kotlin
private fun syncMenuSelectMode(pathsMenuType: PathsMenuType) {
    val wasInSelectMode = when (pathsMenuType) {
        PathsMenuType.MY_PATHS    -> myPathsMenuUiStateFlow.value.inSelectMode
        PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
    }
    // ... existing body that updates state flows ...
    val isInSelectModeNow = when (pathsMenuType) {
        PathsMenuType.MY_PATHS    -> myPathsMenuUiStateFlow.value.inSelectMode
        PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
    }
    if (!wasInSelectMode && isInSelectModeNow) {
        setFocusedPath(pathsMenuType, null)
    }
}
```

If the existing body of `syncMenuSelectMode` cannot easily be split before/after the flip, alternatively add the clear at the top of `toggleMenuItemSelect` *only when called from long-tap entry*. Concretely: in `onPathInListLongTap`, call `setFocusedPath(pathsMenuType, null)` before `toggleMenuItemSelect(...)`. This is simpler and equally correct because `onPathInListLongTap` is the only entry path that transitions into select mode (short-tap toggle inside an already-active select mode doesn't change the flag).

Prefer the simpler approach. Edit `onPathInListLongTap`:

```kotlin
fun onPathInListLongTap(pathId: Long, pathsMenuType: PathsMenuType) {
    setFocusedPath(pathsMenuType, null)
    toggleMenuItemSelect(pathId, pathsMenuType)
}
```

`setFocusedPath` is a no-op if focus was already null, so no spurious event.

- [ ] **Step 4: Run test to confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.long-tap*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt
git commit -m "Clear focused path when entering multi-select mode"
```

---

## Task 6: Clear focus when the focused path is hidden or deleted

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun `hiding the focused path emits SetFocusedPath null`() =
    runTest(testDispatcher) {
        // given: focused 42
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.hidePathFromMap(42L, PathsMenuType.MY_PATHS)
            // call whichever public API hides a single path; see VM for the exact name
            // (in this repo it routes through hideSelectedPaths / similar)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
    }

@Test
fun `hiding a non-focused path does not emit SetFocusedPath`() =
    runTest(testDispatcher) {
        // given: focused 42, also shown 43 (non-focused)
        givenShownRatingPath(42L)
        givenShownRatingPath(43L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.hidePathFromMap(43L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        assertTrue(collected.none { it is MapEvent.SetFocusedPath })
    }

@Test
fun `deleting the focused path emits SetFocusedPath null`() =
    runTest(testDispatcher) {
        // given
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onConfirmMyPathDelete(42L)
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
    }
```

Adjust the hide-call name to whichever single-path-hide API exists (`hideSelectedPaths` takes a menu type only — there may be a different entry; check `PathsMenuViewModel` for a function that accepts a single id, otherwise use `hideSelectedPaths` after selecting `42L`).

- [ ] **Step 2: Run tests to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.hiding*" --tests "*PathsMenuViewModelFocusedPathTest.deleting*"`
Expected: FAIL.

- [ ] **Step 3: Clear focus inside `hideSelectedPaths` and `onConfirmMyPathDelete`**

In `hideSelectedPaths(pathsMenuType)`, after the existing logic that determines which paths are being hidden but before emitting `MapEvent.HidePath` / `ClearMap`, add:

```kotlin
val focused = focusedPathByMenu[pathsMenuType]
if (focused != null && focused in pathsBeingHidden) {
    setFocusedPath(pathsMenuType, null)
}
```

Where `pathsBeingHidden` is the local variable holding the ids about to be hidden (in the current code that's the list passed to `MapEvent.HidePath`).

In `onConfirmMyPathDelete(pathId)`, near the top:

```kotlin
if (focusedPathByMenu[PathsMenuType.MY_PATHS] == pathId) {
    setFocusedPath(PathsMenuType.MY_PATHS, null)
}
```

If a similar `onConfirmMyPathListDelete(pathIds)` exists, mirror the logic with `pathIds.contains(...)`.

- [ ] **Step 4: Run tests to confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.hiding*" --tests "*PathsMenuViewModelFocusedPathTest.deleting*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt
git commit -m "Clear focused path when it is hidden or deleted"
```

---

## Task 7: Clear focus on `ClearMap` for MY_PATHS

This mirrors commit `97747de` which clears `shownPathIds` on `ClearMap` for MY_PATHS.

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `ClearMap emit for MY_PATHS also clears focus`() =
    runTest(testDispatcher) {
        // given: MY_PATHS has a focused path
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when: trigger whatever public API drives ClearMap for MY_PATHS
        // (mirror existing tests in PathsMenuViewModelShortTapTest covering shownPathIds clearing)
        sut.hideAllShownPaths(PathsMenuType.MY_PATHS)  // adjust name to actual API
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
        assertTrue(collected.any { it is MapEvent.ClearMap })
    }
```

The exact API name for "clear all MY_PATHS" should be derived from the test that already covers `97747de` (search test file for `ClearMap`). Use the same trigger.

- [ ] **Step 2: Run test to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.ClearMap*"`
Expected: FAIL.

- [ ] **Step 3: Clear focus next to `shownPathIds.clear()` for MY_PATHS**

Find the code path added in commit `97747de` (search PathsMenuViewModel for `shownPathIdsByMenu[PathsMenuType.MY_PATHS]?.clear()` or similar). Immediately before/after that clear, add:

```kotlin
setFocusedPath(PathsMenuType.MY_PATHS, null)
```

- [ ] **Step 4: Run test to confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.ClearMap*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt
git commit -m "Clear focused path on ClearMap for MY_PATHS"
```

---

## Task 8: Clear focus when a menu is hidden or switched

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt`

Approach: every time PathsMenuViewModel emits `MapEvent.BottomMenuStateChange(newState)`, it implicitly knows that any menu type **not** in `newState` is leaving visibility. Centralize that: route every existing emit through a new private helper that clears focus for the outgoing menus first.

- [ ] **Step 1: Write failing test for menu collapse → DEFAULT**

```kotlin
@Test
fun `transitioning to BottomMenuState DEFAULT clears focus in MY_PATHS`() =
    runTest(testDispatcher) {
        // given: MY_PATHS active and focused
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when: collapse MY_PATHS menu — pick the existing API the VM exposes
        sut.onCloseMyPathsMenu()  // adjust name to actual API
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
    }

@Test
fun `switching from MY_PATHS to OUTER_PATHS clears MY_PATHS focus`() =
    runTest(testDispatcher) {
        // given
        givenShownRatingPath(42L)
        sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }

        // when
        sut.onOpenOuterPathsMenu()  // adjust to actual API
        advanceUntilIdle()
        job.cancel()

        // then
        val focusEvents = collected.filterIsInstance<MapEvent.SetFocusedPath>()
        assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
    }
```

API names: find the existing public methods in `PathsMenuViewModel` that drive the four `BottomMenuStateChange` emit sites (search `newMapEventChannel.trySend(MapEvent.BottomMenuStateChange`). Use those.

- [ ] **Step 2: Run tests to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest.*menu*" --tests "*PathsMenuViewModelFocusedPathTest.*switching*"`
Expected: FAIL.

- [ ] **Step 3: Add `emitBottomMenuStateChange(newState)` helper**

```kotlin
private fun emitBottomMenuStateChange(newState: BottomMenuState) {
    val leavingMyPaths   = focusedPathByMenu[PathsMenuType.MY_PATHS]    != null && newState != BottomMenuState.MY_PATHS_MENU
    val leavingOuter     = focusedPathByMenu[PathsMenuType.OUTER_PATHS] != null && newState != BottomMenuState.OUTER_PATHS_MENU
    if (leavingMyPaths) setFocusedPath(PathsMenuType.MY_PATHS, null)
    if (leavingOuter)   setFocusedPath(PathsMenuType.OUTER_PATHS, null)
    newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(newState))
}
```

- [ ] **Step 4: Replace existing emit sites**

Search `PathsMenuViewModel.kt` for every `newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(...))` (findings note: 4 sites at approximately lines 490, 528, 838, 874). Replace each with `emitBottomMenuStateChange(...)`.

- [ ] **Step 5: Run tests to confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathsMenuViewModelFocusedPathTest*"`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelFocusedPathTest.kt
git commit -m "Clear focused path when leaving its menu"
```

---

## Task 9: Plumb `SetFocusedPath` MapEvent → MapViewModel Flow → fragment

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

No new VM-level tests here — the wiring is single-line and is exercised by manual verification once the halo is in.

- [ ] **Step 1: Add Flow + method to `MapViewModel`**

Alongside the other internal flows (e.g., `fitCameraToBoundsFlow`), add:

```kotlin
private val focusedPathFlow = MutableSharedFlow<Long?>(1, 0, BufferOverflow.DROP_OLDEST)
val observeFocusedPath: Flow<Long?> = focusedPathFlow

fun setFocusedPathOnMap(pathId: Long?) {
    focusedPathFlow.tryEmit(pathId)
}
```

`Long?` is intentionally nullable to carry the "clear focus" signal.

- [ ] **Step 2: Wire `MapEvent.SetFocusedPath` in `MainMapFragment.observeNewMapEvent`**

Replace the Task-1 stub:

```kotlin
is MapEvent.SetFocusedPath -> {
    mapViewModel.setFocusedPathOnMap(mapEvent.pathId)
}
```

- [ ] **Step 3: Observe the new Flow and forward to controller**

In `MainMapFragment`, alongside the existing `mapViewModel.observe*` collectors (and following their pattern of `.onEach { ... }.launchIn(viewLifecycleOwner.lifecycleScope)`):

```kotlin
mapViewModel.observeFocusedPath.onEach { pathId ->
    pathController.setFocusedPath(pathId)
}.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 4: Build (this will fail until Task 10 adds `setFocusedPath`)**

This is expected — we add the controller method in Task 10. Skip the build check here and proceed to Task 10.

- [ ] **Step 5: Commit (in combined commit at end of Task 10)**

Defer the commit; bundle with Task 10's controller changes since they don't build independently.

---

## Task 10: Halo layer in `MapLibrePathController`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

The halo is a single shared MapLibre source + LineLayer added below `LAYER_SAVED_COMMON`. We reuse `PathGeoJsonMapper.ratingPathToFeature` / `commonPathToFeature` to build the Feature for the halo source — same code path the regular layers already use.

Because `MapCommonPath` is currently dropped after conversion to a Feature, we add a retention map for it.

- [ ] **Step 1: Add constants near the top of the controller**

```kotlin
private const val SOURCE_FOCUSED_HALO = "source-focused-halo"
private const val LAYER_FOCUSED_HALO  = "layer-focused-halo"
```

- [ ] **Step 2: Add fields**

Near `savedRatingPaths` / `commonFeatures`:

```kotlin
private val savedCommonPaths = LinkedHashMap<Long, MapCommonPath>()
private var pendingFocusedPathId: Long? = null
private var currentFocusedPathId: Long? = null
```

- [ ] **Step 3: Retain `MapCommonPath` on add**

Find the existing function that accepts common paths (the one that currently calls `PathGeoJsonMapper.commonPathToFeature` and stores into `commonFeatures`). Right next to the `commonFeatures[path.pathId] = ...` line, add:

```kotlin
savedCommonPaths[path.pathId] = path
```

Find the corresponding cleanup / hide / clear paths for common (e.g. wherever `commonFeatures.remove(id)` or `commonFeatures.clear()` is called) and mirror with `savedCommonPaths`.

- [ ] **Step 4: Install halo layer on style ready**

Find `onStyleLoaded(style)` (or the function that does the one-time layer/source setup for `LAYER_SAVED_COMMON`). After `LAYER_SAVED_COMMON` exists, add:

```kotlin
private fun installHaloLayer(style: Style) {
    if (style.getSource(SOURCE_FOCUSED_HALO) != null) return
    style.addSource(
        GeoJsonSource(SOURCE_FOCUSED_HALO, FeatureCollection.fromFeatures(emptyArray()))
    )
    val layer = LineLayer(LAYER_FOCUSED_HALO, SOURCE_FOCUSED_HALO).withProperties(
        PropertyFactory.lineWidth(lineWidth * 2f),
        PropertyFactory.lineColor(Color.parseColor("#FFFFFF")),
        PropertyFactory.lineOpacity(0.9f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayerBelow(layer, LAYER_SAVED_COMMON)
}
```

Call `installHaloLayer(style)` from `onStyleLoaded(style)` right after `LAYER_SAVED_COMMON` is added.

After the install call, apply any pending focus:

```kotlin
pendingFocusedPathId?.let { id ->
    setFocusedPath(id)
    pendingFocusedPathId = null
}
```

- [ ] **Step 5: Add the `setFocusedPath` public method**

```kotlin
fun setFocusedPath(pathId: Long?) {
    val style = this.style ?: run {
        pendingFocusedPathId = pathId
        return
    }
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO) ?: return

    if (pathId == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        currentFocusedPathId = null
        return
    }

    val ratingPath = savedRatingPaths[pathId]
    val commonPath = savedCommonPaths[pathId]
    val feature: Feature? = when {
        ratingPath != null -> PathGeoJsonMapper.ratingPathToFeature(ratingPath)
        commonPath != null -> PathGeoJsonMapper.commonPathToFeature(commonPath)
        else -> null
    }
    if (feature == null) return  // unknown id — leave halo untouched

    source.setGeoJson(feature)
    currentFocusedPathId = pathId
}
```

Note: `PathGeoJsonMapper.ratingPathToFeature` returns `Feature?` (null when path has no usable segments); `commonPathToFeature` returns `Feature`. The `when` block accounts for nullability.

- [ ] **Step 6: Clear halo and `savedCommonPaths` on full clear**

Find the existing "clear everything" path in the controller (e.g. a `clearAll()` or the path triggered by `MapEvent.ClearMap`). Add:

```kotlin
savedCommonPaths.clear()
this.style?.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO)
    ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
currentFocusedPathId = null
```

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run all unit tests as regression**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 9: Commit (bundled with Task 9 changes)**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt \
        app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Render white halo beneath focused path on the map"
```

---

## Task 11: Manual on-device verification

This feature changes UI; type-checking and unit tests don't prove it looks/works right.

- [ ] **Step 1: Install on emulator**

Run: `./gradlew :app:installDebug`
Expected: APK installed.

- [ ] **Step 2: Verify MY_PATHS golden path**

Steps:
1. Open the app, record/save 2-3 paths.
2. Open the MY_PATHS menu, tap "show" on all of them.
3. Tap the first row. **Expect:** that row turns amber; that path on the map gets a white halo; camera fits to it.
4. Tap a second row. **Expect:** amber moves to second row; halo moves to second path; camera fits to it.
5. Tap the same (second) row again. **Expect:** amber gone; halo gone; camera stays put.

- [ ] **Step 3: Verify multi-select interaction**

Steps:
1. With one path focused (amber + halo), long-tap any row. **Expect:** focus is cleared (amber gone, halo gone). Multi-select mode begins with green on the long-tapped row.

- [ ] **Step 4: Verify hide/delete clearing**

Steps:
1. Focus a path (amber + halo visible).
2. Hide that path via its eye icon (or swipe). **Expect:** halo gone, no amber row.
3. Focus another path, then delete it. **Expect:** halo gone, row gone.

- [ ] **Step 5: Verify menu hide/switch**

Steps:
1. Focus a path in MY_PATHS.
2. Collapse the MY_PATHS panel. **Expect:** halo disappears immediately.
3. Re-open MY_PATHS. **Expect:** no row is amber; user can focus again.
4. Focus a path in MY_PATHS, then open OUTER_PATHS directly. **Expect:** MY_PATHS halo disappears.

- [ ] **Step 6: Verify OUTER_PATHS**

Repeat the golden path test (Step 2) using OUTER_PATHS with at least 2 outer paths.

- [ ] **Step 7: Verify halo visible at multiple zoom levels**

Pinch in/out. The halo should remain visible — if it's washed out on light tiles, note for follow-up (Risk note in spec).

- [ ] **Step 8: Take a screenshot showing two shown paths with one focused (halo + amber row)**

Use the screenshot tool to capture proof. Attach to PR description.

- [ ] **Step 9: If any step fails — fix, re-run all unit tests, re-verify on device, then commit fixes**

```bash
git add <fixed-files>
git commit -m "Fix <observed issue> in focused-path highlight"
```

---

## Self-Review

After writing the plan, I checked against the spec:

- **State (focusedPathByMenu)** — Task 4 Step 4 ✓
- **MapEvent.SetFocusedPath** — Task 1 ✓
- **isFocused on model + state + adapter applier + priority** — Tasks 2, 3 ✓
- **New color resource** — Task 3 ✓
- **Halo layer below LAYER_SAVED_COMMON, white, opacity 0.9, lineWidth*2** — Task 10 ✓
- **Pending focused id applied on style ready** — Task 10 Step 4 ✓
- **Common path retention** — Task 10 Step 2/3 ✓
- **Lifecycle: tap focused → clear, no camera** — Task 4 Step 7 ✓
- **Lifecycle: tap different → swap + fit** — Task 4 Step 7 ✓
- **Lifecycle: tap hidden → no-op** — Task 4 Step 7 ✓
- **Lifecycle: enter multi-select → clear** — Task 5 ✓
- **Lifecycle: hide focused → clear** — Task 6 ✓
- **Lifecycle: delete focused → clear** — Task 6 ✓
- **Lifecycle: ClearMap MY_PATHS → clear** — Task 7 ✓
- **Lifecycle: menu hide/switch → clear** — Task 8 ✓
- **Tests: unit per VM transition** — Tasks 4–8 ✓
- **Tests: manual on-device** — Task 11 ✓
- **Existing short-tap test fix-up for new event** — Task 4 Step 10 ✓

Names used consistently across tasks: `setFocusedPath` (controller method + private VM helper), `setFocusedPathOnMap` (MapViewModel), `observeFocusedPath` (MapViewModel flow), `focusedPathByMenu` (VM state), `focused_path_background` (color), `SOURCE_FOCUSED_HALO`/`LAYER_FOCUSED_HALO`. No naming drift.

Placeholder scan: One genuine unknown remains — the names of "single-path hide" and "menu close" public APIs in `PathsMenuViewModel` (Tasks 6, 8). Plan tells the implementer where to look (search `hideSelectedPaths` and `newMapEventChannel.trySend(MapEvent.BottomMenuStateChange`) and uses placeholder names with comments. This is acceptable given the implementer is inside the file at that point.

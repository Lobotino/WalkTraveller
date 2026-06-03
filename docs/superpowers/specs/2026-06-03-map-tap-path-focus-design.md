# Map-Tap Path Focus — Design

## Problem

Today, focusing a path (amber row + white halo on the map) is reachable only via short-tap on its row in the paths list. When the user already sees the path on the map, going back to the list to focus it is awkward. We add the mirror entry point: tap a path on the map → focus it exactly as a list short-tap would, and additionally scroll the list to the tapped row.

## Scope

In scope:

- Map-tap focuses a saved rating path (MY_PATHS) or a cached outer path (OUTER_PATHS) drawn on the map.
- Selecting focus from map mirrors all side-effects of the list short-tap path (amber row, halo, focused-top layer, toggle-off on repeated tap on the same path, swap focus on tap on another path).
- Smooth-scroll the paths list to the newly focused row when focus is set from the map.

Out of scope:

- Map-tap doing anything when no paths menu is open (DEFAULT bottom-menu state) — no-op.
- Map-tap on the currently recording path (`CURRENT_PATH_ID`) — out of scope.
- Map-tap on a path that belongs to the *other* menu than the one currently open (e.g. MY paths menu open, user taps an outer path that is still drawn) — no-op.
- Camera movement on map-tap: explicitly **no** `FitCameraToBounds`.

## Architecture

State and focus mutation stay where they already are: `PathsMenuViewModel.setFocusedPath(type, id)` remains the single mutator. The new entry point feeds into it.

```
MainMapFragment (mapLibreMap.addOnMapClickListener)
        │  PointF (screen point)
        ▼
MapLibrePathController.findClosestPathAt(point, tolerancePx) : Long?
        │  pathId or null
        ▼
MainMapFragment.onMapPathTapped(pathId)
        │
        ▼
PathsMenuViewModel.onPathTappedOnMap(pathId)
        │
        ├── resolveActiveMenuType() → MY_PATHS | OUTER_PATHS | null
        │       (driven by the menu's own ui-state flow it already maintains)
        │
        ├── if menuType == null → no-op
        ├── if !isPathShown(menuType, pathId) → no-op
        │
        ├── setFocusedPath(menuType, pathId)          (existing — toggles off
        │                                              if pathId == current)
        │
        └── if new focus is non-null:
                emit MapEvent.ScrollListToPath(menuType, pathId)
                        ▼
                MainMapFragment → MyPathsMenuView.scrollToPath(id) /
                                  OuterPathsMenuView.scrollToPath(id)
                        ▼
                recyclerView.smoothScrollToPosition(adapter.indexOf(id))
```

Only one focus is set at any moment; the existing `setFocusedPath` continues to handle the swap from an old focus to a new one inside the same menu (it emits two list-item state changes and one `SetFocusedPath` event).

The map controller already keeps a per-path source/layer plus the focused-top layer, and already knows which path ids are installed. The new hit-test extends the controller because that knowledge is local to it.

## Components

### 1. `MapLibrePathController.findClosestPathAt`

New public method, called on Main from the map click listener.

```kotlin
/**
 * Returns the pathId of the saved rating/common path closest to [screenPoint]
 * within [tolerancePx] (screen-pixel distance from the rendered line).
 * Returns null if no candidate is within tolerance.
 *
 * Implementation:
 * 1. queryRenderedFeatures(boundingBoxAround(screenPoint, tolerancePx), candidateLayerIds)
 *    — candidate layers are: installed rating layers (LAYER_SAVED_RATING_PREFIX + id),
 *      LAYER_SAVED_COMMON, LAYER_FOCUSED_TOP.
 * 2. For each returned feature, recover pathId:
 *      - rating layer: pathId is parsed from the layer id suffix.
 *      - common feature: pathId is read from the feature property "pathId" added
 *        by PathGeoJsonMapper.commonPathToFeature (extend the mapper to set it).
 *      - focused-top feature: skip (it's a duplicate of an already-installed path;
 *        if it was reachable that path was reachable too — keeps logic simpler).
 * 3. For each unique candidate pathId, compute the minimum screen-pixel distance
 *    from screenPoint to its polyline (use map.projection.toScreenLocation on
 *    each segment vertex; segment distance = point-to-line-segment formula).
 * 4. Return the pathId with the smallest distance ≤ tolerancePx; null otherwise.
 */
fun findClosestPathAt(screenPoint: PointF, tolerancePx: Float): Long?
```

Notes:

- We need to read `MapLibreMap.projection` for screen-space conversion. The controller does not currently hold a reference to `MapLibreMap`, only to `Style`. Solution: accept the projection as a parameter — `findClosestPathAt(point, tolerancePx, projection: Projection)`. Keeps controller's dependency surface unchanged outside of the call.
- `PathGeoJsonMapper` already sets the `PROPERTY_PATH_ID` numeric property on both rating and common features (via `segmentsToFeature` / `commonPathToFeature`). The hit-test reads it directly from the feature, no parsing of layer ids is needed.
- Tolerance is **24dp** converted to px at call site (`Utils.convertDpToPixel(context, 24f)` — pattern already used in the project).

### 2. `MapEvent.ScrollListToPath`

New event added to the existing sealed class:

```kotlin
class ScrollListToPath(val pathsMenuType: PathsMenuType, val pathId: Long) : MapEvent()
```

Note: this event flows on `PathsMenuViewModel.newMapEventChannel` like the other view-driving events, despite the name "MapEvent" — it triggers a list scroll, not a map mutation. Naming is kept as `MapEvent` for consistency with the existing channel-based UI event pipeline (the same channel already carries `SetFocusedPath`, `FitCameraToBounds`, `BottomMenuStateChange`, which is also list/UI-state-affecting). No separate channel is added — keeps things uniform.

### 3. `MainMapFragment` — click listener wiring

After style is loaded (where it is safe to call `queryRenderedFeatures`), add:

```kotlin
map.addOnMapClickListener { latLng ->
    val screenPoint = map.projection.toScreenLocation(latLng)
    val tolerancePx = Utils.convertDpToPixel(requireContext(), TAP_TOLERANCE_DP)
    val pathId = pathController.findClosestPathAt(
        screenPoint = screenPoint,
        tolerancePx = tolerancePx,
        projection = map.projection,
    )
    if (pathId != null) {
        menuViewModel.onPathTappedOnMap(pathId)
    }
    // Return false to let MapLibre keep its default click handling
    // (none today, but future-proof).
    false
}
```

Constant `TAP_TOLERANCE_DP = 24f` declared in the fragment (or in a small constants holder if more values join later — for now keep local).

Where to put the call: the existing `mapView.getMapAsync { map -> ... }` block already runs on the main thread and is the canonical wiring spot. Add `map.addOnMapClickListener { ... }` there, **after** `currentStyleUrl?.let { applyStyle(map, it) }` is not required — the click listener is safe to register at any time; the click handler itself checks layer presence implicitly via `queryRenderedFeatures` (returns empty on unloaded layers).

New handler in `observeNewMapEvent`:

```kotlin
is MapEvent.ScrollListToPath -> when (mapEvent.pathsMenuType) {
    PathsMenuType.MY_PATHS    -> myPathsMenu.scrollToPath(mapEvent.pathId)
    PathsMenuType.OUTER_PATHS -> outerPathsMenu.scrollToPath(mapEvent.pathId)
}
```

### 4. `MyPathsMenuView` / `OuterPathsMenuView` — `scrollToPath`

```kotlin
fun scrollToPath(pathId: Long) {
    val index = pathsInfoListAdapter.indexOfPath(pathId)
    if (index >= 0) pathsInfoList.smoothScrollToPosition(index)
}
```

### 5. `PathsInfoAdapter` / `OuterPathsInfoAdapter` — `indexOfPath`

```kotlin
fun indexOfPath(pathId: Long): Int =
    pathsItems.indexOfFirst { it.pathInfo.pathId == pathId }
```

### 6. `PathsMenuViewModel.onPathTappedOnMap`

```kotlin
fun onPathTappedOnMap(pathId: Long) {
    val menuType = activeMenuTypeForFocus() ?: return
    if (!isPathShown(menuType, pathId)) return

    val previousFocus = focusedPathByMenu[menuType]
    setFocusedPath(menuType, pathId)  // toggles off if previousFocus == pathId

    val newFocus = focusedPathByMenu[menuType]
    if (newFocus != null) {
        // Only scroll when the result is a non-null focus.
        // A toggle-off (newFocus == null) means the user re-tapped the same path;
        // no scroll is necessary (the row is already focused-visible if it was scrolled to
        // last time, and scrolling to an un-focused row would be misleading).
        newMapEventChannel.trySend(MapEvent.ScrollListToPath(menuType, pathId))
    }
}

/**
 * Returns the menu type whose list is the active focus target right now,
 * or null if no menu is open.
 *
 * Driven by `currentBottomMenuState`, a local field PathsMenuViewModel tracks.
 * It is updated at the top of every `emitBottomMenuStateChange(newState)` call
 * (before the existing focus-clear branches) so any map tap that follows reads
 * the up-to-date value.
 */
private fun activeMenuTypeForFocus(): PathsMenuType? = when (currentBottomMenuState) {
    BottomMenuState.MY_PATHS_MENU    -> PathsMenuType.MY_PATHS
    BottomMenuState.OUTER_PATHS_MENU -> PathsMenuType.OUTER_PATHS
    BottomMenuState.DEFAULT          -> null
    null                              -> null
}
```

Add private field:

```kotlin
private var currentBottomMenuState: BottomMenuState? = null
```

Set it at the top of `emitBottomMenuStateChange(newState)` **before** the focus-clear branches, so any read after the call sees the new state.

### 7. `PathGeoJsonMapper`

No changes — `segmentsToFeature` and `commonPathToFeature` already set `PROPERTY_PATH_ID` on each feature. `findClosestPathAt` reads it directly from `feature.getNumberProperty(PROPERTY_PATH_ID)`.

## Lifecycle & focus-clear interactions

All clear triggers are unchanged. Map-tap behaviour interacts with them:

| Trigger | Map-tap behaviour |
|---|---|
| User taps non-focused shown path on map | focus set + halo + amber + list scroll-to |
| User taps focused path on map | focus cleared (toggle-off) + halo gone + amber gone + **no scroll** |
| User taps shown path A, then taps shown path B on map | focus moves A→B + halo moves + amber moves + scroll to B |
| User taps a hidden path's *position* on map | hit-test returns null (the path is not installed as a layer) → no-op |
| User taps a path belonging to *other* menu than the one open | hit-test may return the id, but `isPathShown(activeMenu, id)` returns false → no-op |
| Menu is collapsed/swapped while focus exists | existing `emitBottomMenuStateChange` clears focus + emits `SetFocusedPath(null)`; new state field is updated, future map taps no-op until menu reopens |
| Path deleted / hidden / ClearMap | existing logic — focus clears, future map tap on its position no-op |

## Testing

### Unit — `PathsMenuViewModel` (JVM)

Convention: SUT named `sut`, `@Before`/`@After`, given/when/then bodies (matches `PathsMenuViewModelFocusedPathTest`).

- map-tap when no menu is open → no emit
- map-tap with MY_PATHS_MENU open, on shown MY path → emits `SetFocusedPath(id)`, then `ScrollListToPath(MY_PATHS, id)`; no `FitCameraToBounds`
- map-tap on already-focused path → emits `SetFocusedPath(null)`; no `ScrollListToPath`
- map-tap on a second shown path while another is focused → emits `SetFocusedPath(newId)` + `ScrollListToPath(MY_PATHS, newId)`
- map-tap on path id that is not in `shownPathIdsByMenu[activeMenu]` → no emit (covers both "hidden" and "belongs to other menu")
- map-tap with OUTER_PATHS_MENU open, on shown OUTER path → emits `SetFocusedPath(id)` + `ScrollListToPath(OUTER_PATHS, id)`
- existing focus-clear paths (delete, hide, menu collapse, ClearMap) still clear and still don't fire `ScrollListToPath`

### Unit — `MapLibrePathController` (where reachable)

- `findClosestPathAt` returns null when no layers are installed
- `findClosestPathAt` returns the pathId whose polyline is closest within tolerance, when two paths overlap
- `findClosestPathAt` returns null when the closest path is beyond tolerance

If pure-JVM exercising of `findClosestPathAt` is impractical (depends on MapLibre `Projection` and `Style`), extract the *distance computation* into a pure helper (`PolylineHitTest.closestPathId(screenPoint, candidates: List<PathPolylineScreen>, tolerancePx): Long?`) and unit-test that. The controller method becomes glue.

### Manual / on-device

- Open MY_PATHS_MENU with several paths shown; tap one on the map → it gets halo + amber row + list scrolls to that row
- Tap the same path again → toggle-off; no scroll
- Tap another path → focus swaps; list scrolls to new row
- Tap empty area → nothing happens
- Tap a path with the bottom menu in DEFAULT (collapsed via back) → nothing happens (sanity)
- Repeat in OUTER_PATHS_MENU with imported outer paths
- Verify hit-test tolerance feels right (24dp) — tweak if needed

## Risk notes

- `queryRenderedFeatures` does not include features outside the current viewport. The list of candidate layers covers visible saved-rating paths (controlled by viewport culling in `pushSavedRating`). Map-tap therefore implicitly aligns with what is on screen — that is the desired behaviour. No additional culling check needed in the hit-test path.
- `LAYER_FOCUSED_TOP` is included in the candidate layer set so that tapping directly on the currently focused path (drawn on top of others) still hits it. The focused-top feature is then skipped during id extraction because the corresponding original layer is also a candidate and yields the same id.
- The hit-test runs on Main and traverses polyline vertices. For very large paths (thousands of points) per-pixel distance computation could feel jittery on low-end devices. Mitigation: use the `MapLibreMap.projection` to convert *only the vertices returned by `queryRenderedFeatures`* (which are already simplified by the GeoJsonOptions tolerance the controller sets — `SIMPLIFICATION_TOLERANCE = 1.0f`). Bench during manual verification; if needed, fall back to "first feature returned" with a TODO.
- New `currentBottomMenuState` field in VM must be set before `emitBottomMenuStateChange` performs focus-clear (which itself emits events read by tests). Update order matters — set state field, then run the existing branches. Cover with a unit test that taps a path right after switching menus.
- `addOnMapClickListener` interacts with the camera-idle listener flow indirectly: clicks do not move the camera (we explicitly skip `FitCameraToBounds`), so no risk of triggering `onCameraIdle`/`pushSavedRating` re-entry from a tap.

# Focused Path Highlight — Design

## Problem

Tapping a path in the path list animates the camera to fit that path's bounds. When many paths are shown on the map, the user can't tell which one they zoomed to: there is no visual link between the tapped row in the list and the corresponding path on the map.

We add a single-track "focus" highlight:

- **On the map:** a halo (wider line in an accent color) drawn beneath the focused path, preserving the path's own line-gradient.
- **In the list:** the focused row's background is filled with a distinct color (not the multi-select green).

Only one path is focused per menu at any time. Multi-select (long-tap) and focus are mutually exclusive.

## Scope

- Both `PathsMenuType.MY_PATHS` (rating paths, line-gradient) and `PathsMenuType.OUTER_PATHS` (common paths, single color).
- In-memory only — focus is lost across app restarts.

Out of scope: persisting focus, focusing a hidden path (tap on a hidden row remains no-op), multi-focus.

## Architecture

State lives in `PathsMenuViewModel` and is projected one-way to both the list adapter (via the existing `pathListChannel` partial-update infrastructure) and the map (via a new `MapEvent`). This matches the existing pattern used for `shownPathIdsByMenu` and `FitCameraToBounds`.

```
PathsMenuViewModel  (focusedPathByMenu)
        │
        ├── pathListChannel ──► PathsInfoAdapter (item.isFocused → background color)
        │
        └── newMapEventChannel ──► MapEvent.SetFocusedPath ──► MapViewModel
                                                                   │
                                                                   ▼
                                                          MapLibrePathController
                                                          (halo source/layer)
```

## Components

### 1. State — `PathsMenuViewModel`

```kotlin
private val focusedPathByMenu: MutableMap<PathsMenuType, Long?> = mutableMapOf(
    PathsMenuType.MY_PATHS to null,
    PathsMenuType.OUTER_PATHS to null,
)
```

Invariants:
- At most one focused path per menu type. Across menus, both can be non-null, but only one menu is visible at a time.
- A path is never `isSelected` (multi-select) and `isFocused` simultaneously.

### 2. New event — `MapEvent.SetFocusedPath`

```kotlin
sealed class MapEvent {
    ...
    class SetFocusedPath(val pathId: Long?) : MapEvent()  // null = clear focus
}
```

Handled in `MainMapFragment.observeNewMapEvent`:

```kotlin
is MapEvent.SetFocusedPath -> mapViewModel.setFocusedPathOnMap(mapEvent.pathId)
```

`MapViewModel.setFocusedPathOnMap(id: Long?)` delegates to `MapLibrePathController.setFocusedPath(id)`.

### 3. List item model and adapter

`PathInfoItemModel` gets a new field:

```kotlin
var isFocused: Boolean = false
```

`PathInfoItemState` (the partial-update payload, already used for `isSelected`) gets:

```kotlin
val isFocused: Boolean? = null
```

Applier in `PathsInfoAdapter` mirrors the existing `isSelected` block:

```kotlin
if (pathInfoItemState.isFocused != null) {
    item.isFocused = pathInfoItemState.isFocused
}
```

`PathsInfoAdapter` adds:

```kotlin
protected var focusedItemBackgroundColor by Delegates.notNull<Int>()

init {
    ...
    focusedItemBackgroundColor = ContextCompat.getColor(context, R.color.focused_path_background)
}
```

`ViewHolder.bind` picks background by priority:

```kotlin
itemBackground.setBackgroundColor(
    when {
        path.isSelected -> selectedItemBackgroundColor
        path.isFocused  -> focusedItemBackgroundColor
        else            -> defaultItemBackgroundColor
    }
)
```

New color resource in `app/src/main/res/values/colors.xml`:

```xml
<color name="focused_path_background">#FFF4D6</color>  <!-- pale amber -->
```

The chosen color must visually differ from `primary_green_light` (multi-select).

### 4. Map — halo layer in `MapLibrePathController`

Constants:

```kotlin
private const val SOURCE_FOCUSED_HALO = "source-focused-halo"
private const val LAYER_FOCUSED_HALO  = "layer-focused-halo"
```

Installed once when the style is ready:

```kotlin
private fun installHaloLayer(style: Style) {
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

Halo is added below `LAYER_SAVED_COMMON`, which means it sits below every per-path rating/common layer (those are also added below `LAYER_SAVED_COMMON` and thus end up above the halo by insertion order). The halo's wider line "leaks out" from beneath the main path line, producing the glow.

API:

```kotlin
private var pendingFocusedPathId: Long? = null
private var currentFocusedPathId: Long? = null

fun setFocusedPath(pathId: Long?) {
    val style = mapStyle ?: run {
        pendingFocusedPathId = pathId
        return
    }
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO) ?: return
    if (pathId == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        currentFocusedPathId = null
        return
    }
    val geometry = savedRatingPaths[pathId]?.toLineString()
        ?: savedCommonPaths[pathId]?.toLineString()
        ?: return  // unknown id — leave halo untouched
    source.setGeoJson(Feature.fromGeometry(geometry))
    currentFocusedPathId = pathId
}
```

`onStyleReady` (or the existing style-ready path) applies `pendingFocusedPathId` once `installHaloLayer` has run.

The same call works for both rating and common paths because only geometry matters here.

### 5. ViewModel logic — focus transitions

`onPathInListShortTap(pathId, pathsMenuType)`:

```kotlin
// existing: select-mode short-circuit
if (inSelectMode) {
    toggleMenuItemSelect(pathId, pathsMenuType)
    return
}
// existing: ignore hidden paths
if (!isPathShown(pathsMenuType, pathId)) return

val currentFocused = focusedPathByMenu[pathsMenuType]
if (currentFocused == pathId) {
    // toggle off — no camera move
    focusedPathByMenu[pathsMenuType] = null
    updatePathItemFocus(pathsMenuType, oldId = pathId, newId = null)
    newMapEventChannel.trySend(MapEvent.SetFocusedPath(null))
    return
}

focusedPathByMenu[pathsMenuType] = pathId
updatePathItemFocus(pathsMenuType, oldId = currentFocused, newId = pathId)
newMapEventChannel.trySend(MapEvent.SetFocusedPath(pathId))

// existing: emit FitCameraToBounds
viewModelScope.launch {
    val path = resolveShownPath(pathId, pathsMenuType) ?: return@launch
    val bounds = pathBounds(path) ?: return@launch
    newMapEventChannel.trySend(MapEvent.FitCameraToBounds(bounds))
}
```

Private helper `updatePathItemFocus(type, oldId, newId)` emits two partial `PathInfoItemState` updates (clearing focus on old, setting on new) through the existing `pathListChannel`.

### 6. Lifecycle — all focus-clearing triggers

| Trigger | Action |
|---|---|
| Tap focused path | `focusedPathByMenu[type] = null`; `SetFocusedPath(null)`; no camera move |
| Tap another shown path | swap focus + `SetFocusedPath(newId)` + `FitCameraToBounds` |
| Tap hidden path | no-op |
| Enter multi-select (long-tap) | if focused: clear focus and emit `SetFocusedPath(null)` before normal multi-select logic |
| Path hidden via eye / swipe | if hidden id == focused id: clear focus + `SetFocusedPath(null)` |
| `ClearMap` for MY_PATHS | clear `focusedPathByMenu[MY_PATHS]` + emit `SetFocusedPath(null)` (mirrors the existing `shownPathIds.clear()` line added in commit `97747de`) |
| Path deleted from DB | if deleted id == focused id in either menu: clear focus + emit |
| Menu panel collapse/hide | clear focus for the menu being hidden + emit `SetFocusedPath(null)`. *Signal source TBD by implementation plan — the VM needs a hook (e.g. a new `onMenuHidden(type)` callback invoked from the fragment, or wiring into the existing `BottomMenuState` observation).* |
| Switch between MY_PATHS ↔ OUTER_PATHS | hide-side clears its focus and emits `null`; show-side starts from whatever it remembers (typically `null`) |

The map halo always reflects the most recent `SetFocusedPath(...)` event; clearing means setting the halo source to an empty feature collection.

## Testing

### Unit — `PathsMenuViewModel` (JVM)

Convention: SUT named `sut`, `@Before`/`@After`, given/when/then bodies.

- short-tap on shown non-focused path → emits `SetFocusedPath(id)` and `FitCameraToBounds`; focus stored
- short-tap on focused path → emits `SetFocusedPath(null)`; **no** `FitCameraToBounds`; focus cleared
- short-tap on another shown path → focus moves; emits `SetFocusedPath(newId)` and `FitCameraToBounds`
- short-tap on hidden path → no focus emit (existing behavior preserved)
- long-tap when focused → emits `SetFocusedPath(null)`, then enters multi-select
- hiding the focused path → emits `SetFocusedPath(null)`
- hiding a non-focused path → no focus emit
- ClearMap on MY_PATHS while focused → emits `SetFocusedPath(null)`
- Menu panel hide/collapse while focused → emits `SetFocusedPath(null)`
- MY_PATHS and OUTER_PATHS focus tracked independently (focus in one doesn't clear the other)

### Unit — `MapLibrePathController` (if tested)

- `setFocusedPath(id)` updates halo source to the path's geometry
- `setFocusedPath(null)` clears halo source to empty FeatureCollection
- `setFocusedPath(unknownId)` leaves the source unchanged
- `pendingFocusedPathId` is applied in `onStyleReady`

### Adapter

- Applier sets `item.isFocused` from `PathInfoItemState.isFocused`
- (Visual background verified manually on emulator)

### Manual / on-device

- Show several paths; tap row → halo appears around that path + row turns amber + camera fits
- Tap the same row again → halo gone, amber gone, camera still
- Tap another row → halo moves, only one row amber at a time
- Long-tap → amber gone (multi-select takes over with green)
- Collapse panel → halo gone
- Hide focused path via eye/swipe → halo gone
- Repeat in OUTER_PATHS

## Risk notes

- Z-order: halo must end up below per-path layers. Currently `addLayerBelow(layer, LAYER_SAVED_COMMON)` is used for rating layers, so adding halo via the same anchor before any rating layer exists places it at the bottom of that group. The implementation plan must verify this on a style with no paths and on a style mid-load.
- `lineWidth * 2f` may visually dominate at extreme zoom levels; tune during manual verification.
- Halo color is white; on light tiles it may be hard to see. If so, switch to a darker accent (e.g. deep amber) or add a thin contrasting outline above. Defer to manual verification.
- The signal "menu panel collapsed/hidden" is not yet identified in the spec. The implementation plan must locate (or add) the right hook in `PathsMenuViewModel`. Without it, focus would survive panel collapse and reappear visually on the next panel open while halo is gone — a real desync.

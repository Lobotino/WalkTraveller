# Restore Map Camera State (center + zoom) on App Start

## Problem

On app launch the map currently restores only the last seen center point. The zoom level is not persisted, so the camera always starts at the MapLibre default zoom (≈0), showing the whole world. The user has to manually zoom in every time.

Existing infrastructure already saves the last center point via `LastSeenPointRepository` / `MapStateInteractor`. The fragment then emits this point into `newMapCenterFlow`, and the subscriber picks zoom as `maxOf(map.cameraPosition.zoom, DEFAULT_COMFORT_ZOOM)` — which, on first frame, evaluates to `DEFAULT_COMFORT_ZOOM` only if the current zoom is below it; but it also runs on every other "go to point" emission (e.g., find-my-location), so this path mixes two semantics. There is a `MapViewModel.onMapZoomed()` method that is a no-op TODO.

## Goal

Persist the camera state (center + zoom) at runtime and restore it as the initial camera position on the next launch.

## Non-goals

- No persistence of bearing/tilt.
- No persistence across users or devices (SharedPreferences only).
- No new "save now" UI affordance — saving happens implicitly on every camera idle.

## Design

### Model

New data class:

```
data class MapCameraState(val center: MapPoint, val zoom: Double)
```

Lives in `model/map/`. Represents a single, indivisible camera position used both when saving and when restoring.

### Repository

Rename `ILastSeenPointRepository` → `IMapCameraStateRepository` and `LastSeenPointRepository` → `MapCameraStateRepository`. New interface:

```
fun getLastCameraState(): MapCameraState?
fun setLastCameraState(state: MapCameraState)
```

Implementation reads/writes three keys in the same `SharedPreferences`:
- `last_seen_point_latitude` (existing)
- `last_seen_point_longitude` (existing)
- `last_seen_zoom` (new)

Migration: if latitude/longitude are present but zoom is missing or unparseable, return `MapCameraState(center, DEFAULT_ZOOM)`. No separate migration step needed — first save after that will fill the zoom.

If latitude or longitude is missing, return `null` (same as today).

### Interactor

Update `IMapStateInteractor`:

```
fun getLastCameraState(): MapCameraState
fun setLastCameraState(state: MapCameraState)
```

Default when repository returns `null`:

```
MapCameraState(MapPoint(55.7522200, 37.6155600), DEFAULT_ZOOM)
```

`DEFAULT_ZOOM = 15.0` is a constant on `MapStateInteractor` (the interactor owns the default — the UI layer is not the source of truth for the startup zoom). The existing UI-side `DEFAULT_COMFORT_ZOOM = 15.0` in `MainMapFragment` stays for its other usage (clamping zoom when the "find-my-location" flow emits to `newMapCenterFlow`).

### ViewModel

- New flow: `private val restoreCameraStateFlow = MutableSharedFlow<MapCameraState>(1, 0, BufferOverflow.DROP_OLDEST)` with public `val observeRestoreCameraState: Flow<MapCameraState> = restoreCameraStateFlow`.
- Replace `setupMapCenterToLastSeenLocation()`:
  ```
  private fun setupMapCameraToLastSeenState() {
      restoreCameraStateFlow.tryEmit(mapStateInteractor.getLastCameraState())
  }
  ```
- Replace `fun onMapScrolled(mapPoint: MapPoint)` with `fun onCameraIdle(center: MapPoint, zoom: Double)`. Body:
  ```
  mapStateInteractor.setLastCameraState(MapCameraState(center, zoom))
  // existing find-my-location button reset logic stays
  ```
- Delete `fun onMapZoomed()` (was a no-op TODO).

`newMapCenterFlow` and its semantics ("go to this point, clamp zoom to comfortable") are unchanged — it is used by find-my-location and similar flows.

### Fragment

In `MainMapFragment`:

- `addOnCameraIdleListener` now reads both target and zoom and calls `mapViewModel.onCameraIdle(target.toMapPoint(), map.cameraPosition.zoom)`.
- New subscription:
  ```
  observeRestoreCameraState.onEach { state ->
      val map = mapLibreMap ?: return@onEach
      map.moveCamera(
          CameraUpdateFactory.newLatLngZoom(state.center.toLatLng(), state.zoom)
      )
  }.launchIn(viewLifecycleOwner.lifecycleScope)
  ```
  Uses `moveCamera` (no animation) so the very first frame is correct — restoring camera at startup should not feel like a fly-in.
- Existing `observeNewMapCenter` subscription remains as-is; its zoom-clamping logic still applies to non-restore use cases.

### Data flow summary

Save path (every camera idle): `MapView idle → fragment reads (target, zoom) → MapViewModel.onCameraIdle → MapStateInteractor.setLastCameraState → MapCameraStateRepository writes 3 keys`.

Restore path (init): `MapViewModel.init → setupMapCameraToLastSeenState → restoreCameraStateFlow emits → fragment moveCamera(center, zoom)`.

## Error handling and edge cases

| Case | Behavior |
|---|---|
| First launch, nothing saved | Repository returns `null` → interactor returns Moscow + 15.0 |
| Old install: lat/lng saved, no zoom key | Repository returns `MapCameraState(savedCenter, 15.0)` |
| Corrupt value in SharedPreferences | `toDoubleOrNull()` yields `null` → fallback to default |
| `mapLibreMap` not yet ready when restore emits | Subscriber checks `mapLibreMap ?: return@onEach`; `restoreCameraStateFlow` is replay=1, so the value is delivered as soon as collection starts |

## Tests

Unit:
- `MapStateInteractorTest` (existing): update to assert `getLastCameraState` returns stored state, falls back to Moscow + 15.0, and `setLastCameraState` delegates.
- `MapCameraStateRepositoryTest` (new or renamed): persists and reads back center + zoom; missing zoom returns center + 15.0; missing center returns null; corrupt values fall back.
- `MapViewModel` test (new, JVM): `onCameraIdle(point, zoom)` calls `setLastCameraState(MapCameraState(point, zoom))`; on init, `observeRestoreCameraState` emits the interactor's value.

Follow existing test conventions: SUT named `sut`, `@Before`/`@After`, given/when/then layout.

## Out-of-scope considerations

- Bearing/tilt are not persisted. Users almost never rotate this map; adding them later is straightforward by extending `MapCameraState`.
- No migration of zoom key value range — MapLibre zoom is a double; we trust the writer.

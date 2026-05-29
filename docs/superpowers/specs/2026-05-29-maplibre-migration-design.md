# MapLibre GL migration — design

**Date:** 2026-05-29
**Status:** Approved (design); pending implementation plan

## Goal

Replace osmdroid with MapLibre GL Native (Android) as the map engine. Motivation:
the current raster tiles come from `tile.openstreetmap.org` (MAPNIK) and OpenTopoMap,
which throttle/block app traffic under the OSMF tile usage policy. Moving to MapLibre
with free, keyless vector tiles (OpenFreeMap) removes the blocking problem and lets the
rating-colored path rendering use efficient data-driven layers.

## Decisions (locked)

- **Scope:** full replacement of osmdroid. osmdroid dependency removed.
- **Tile/style provider:** OpenFreeMap (free, no API key, no rate limits, vector).
- **Map styles exposed in Settings:** two OpenFreeMap styles — `Liberty` (default) and
  `Positron`. No topographic style (OpenFreeMap has none).
- **Path rendering approach:** data-driven GeoJSON — one `GeoJsonSource` + one `LineLayer`
  per category; segment color driven by a `rating` feature property via a `match`/`step`
  expression.
- **Unit tests:** authored separately by the user before the plan runs, following their
  convention (SUT named `sut`, `@Before`/`@After`, given/when/then). Not part of this plan.

## Principle: domain layer is untouched

`MapPoint`, `MapPathSegment`, `MapRatingPath`, `MapCommonPath`, `SegmentRating`, and all
ViewModels stay as-is. Only the map render layer in `MainMapFragment` and the map-style
selection layer change. Existing JVM unit tests must stay green.

## Dependencies & initialization

- `app/build.gradle`: remove `org.osmdroid:osmdroid-android:6.1.20`; add
  `org.maplibre.gl:android-sdk:11.x`.
- `App.kt`: remove osmdroid `Configuration.load(...)`; add `MapLibre.getInstance(this)`
  before any `MapView` is created.
- minSdk is 24 — above MapLibre's minimum (21). No change needed.

## Map-style selection layer (reuse existing `TileSource*` types)

Keep the `TileSourceType` / `TileSourceInteractor` / `TileSourceRepository` names to
minimize churn across `SettingsFragment` / `SettingsViewModel` / DI; change their contents:

- `TileSourceType` enum values become `LIBERTY("Liberty")`, `POSITRON("Positron")`;
  default `LIBERTY`.
- The `TileSource` sealed class collapses to a plain holder of a style URL string. Exact
  name/shape (keep `TileSource`, or rename to `MapStyle`) is an implementation detail for
  the plan; today's `TileSource.OSMTileSource(ITileSource)` is no longer needed.
- `TileSourceInteractor` maps the chosen type to an OpenFreeMap style URL
  (`https://tiles.openfreemap.org/styles/liberty`, `.../positron`).
- `TileSourceRepository` unchanged (stores chosen type in SharedPreferences); default
  becomes `LIBERTY`.
- `SettingsFragment` / `SettingsViewModel` / DI: no structural change, only new enum values.

## New render components

### `PathGeoJsonMapper` (pure, no map dependency)
Converts domain paths to a list of GeoJSON `Feature` (LineString) with a `rating`/`color`
property. Hosts the "merge adjacent segments of the same rating" logic currently inlined in
`MainMapFragment` (`paintNewRatingPaths`, `paintNewCurrentPathSegments`). Pure function, so
it can be unit-tested without the map.

### `MapLibrePathController`
Owns the `Style` and two `GeoJsonSource` + `LineLayer` pairs (saved paths; current
recording path). API mirrors today's fragment functions: `showRatingPaths`,
`showCommonPaths`, `appendCurrentPathSegments`, `hidePath(s)`, `clear`, `finishCurrentPath`.
Holds an in-memory `Map<Long, List<Feature>>` keyed by `pathId` (mirrors current
`showingPathsPolylines`). Line color from a `match`/`step` expression on the `rating`
property; `lineCap`/`lineJoin = round`; width may interpolate by zoom.

### `UserLocationMarker` (replaces `UserLocationOverlay`)
A `GeoJsonSource` with a single point feature + a `SymbolLayer`. The `ic_user_marker`
drawable is registered as a style image; rotation via `icon-rotate` from a `bearing`
property. `setPosition` / `setRotation` update the feature.

## `MainMapFragment` changes

- `org.osmdroid.views.MapView` → `org.maplibre.android.maps.MapView`.
- Init: `getMapAsync { map -> map.setStyle(styleUrl) { style -> controllers.onStyleLoaded(style) } }`.
- Camera: `controller.setCenter/setZoom` → `map.animateCamera(CameraUpdateFactory…)`.
- Map events: `addOnCameraMoveListener` / `addOnCameraIdleListener` →
  `onMapScrolled(center)` / `onMapZoomed()`.
- `paintNew*` / `hide*` delegate to the new controllers.

## Coordinates

`MapPoint.toGeoPoint()` (osmdroid `GeoPoint`) → `MapPoint.toLatLng()` (MapLibre `LatLng`).
`MapPoint` itself unchanged.

## Data flow

ViewModel (unchanged) emits domain models → fragment observes the same Flows → forwards to
`MapLibrePathController` / `UserLocationMarker` → mappers build GeoJSON →
`source.setGeoJson(...)`.

## Style switching (critical)

`map.setStyle(newUrl)` recreates the style and **wipes** all programmatically added sources,
layers, and registered images. Therefore:

- All map setup (creating the path sources/layers, registering `ic_user_marker`, adding the
  marker `SymbolLayer`) happens **only** inside the `setStyle { style -> … }` callback.
- Controllers keep their state in memory and, in `onStyleLoaded(style)`, recreate layers and
  **re-apply** all current state (shown paths, current path, marker position/rotation).
- On Liberty↔Positron switch: fragment calls `setStyle`; controllers restore paths and
  marker themselves. Existing Flows do not need to re-emit.

## MapView lifecycle

Forward all events in `MainMapFragment`: `onCreate(savedInstanceState)`, `onStart`,
`onResume`, `onPause`, `onStop`, `onSaveInstanceState(outState)`, `onLowMemory`,
`onDestroy`. Today only `onResume`/`onPause` are forwarded — add the rest.

## Error handling / edge cases

- **Events before style ready:** controllers buffer state and apply it in `onStyleLoaded`;
  layer access before readiness is a no-op. Removes the "show path before map ready" race.
- **Tile load failure / OpenFreeMap unavailable:** MapLibre shows a blank/partial
  background; no crash. No special handling (accepted risk of the free provider).
- **Empty / single-segment paths:** mapper returns an empty `FeatureCollection` or a single
  feature correctly.
- **R8/ProGuard:** MapLibre ships consumer rules; verify with a release build
  (`app/release/` exists in the repo).

## Verification (tests authored separately by the user)

The migration plan relies on:
- existing JVM unit tests staying green (domain untouched);
- manual verification on emulator/device: map loads; recording a path with rating changes
  produces correct segment colors; show/hide saved paths; Liberty↔Positron switch preserves
  paths and marker; marker position and rotation; camera follow; release build with R8.

`PathGeoJsonMapper` is designed as a pure function specifically so the user can cover it with
unit tests without the map.

## Out of scope

- Topographic map style.
- Offline tile caching (not currently used).
- Tapping paths on the map (not currently supported).
- Any domain/ViewModel changes.

# Render Focused Path on Top — Design

## Problem

The focused-path-highlight feature draws a white halo beneath the focused path and renders its row with an amber background. The halo currently sits at the BOTTOM of the saved-paths layer group (`addLayerBelow(LAYER_SAVED_COMMON)`), so any other rating or common path that overlaps it visually obscures both the halo and the focused path itself.

The user wants the focused path and its halo to render ON TOP of all other saved paths (but still below `LAYER_CURRENT`, the user's active recording, which represents "right now" and should stay topmost).

## Scope

- `MapLibrePathController` only. No VM changes, no list-row changes, no new MapEvents.
- Works for both rating (per-path gradient layers) and common (single shared layer) saved paths.
- In-memory only — no persistence implications.

Out of scope: removing or reordering the path's *original* rendering. We render the focused path *additionally* on top, accepting one extra draw pass for visual correctness.

## Architecture

Two new "top" layers, both reading from the existing focused-halo source. The source is updated on every `setFocusedPath(...)` call to carry the focused path's geometry. The top layer applies the path's gradient (rating) or a uniform-color "gradient" (common) via `lineGradient`, so both path types use the same property path on the same layer.

```
                                LAYER_CURRENT          (active recording, topmost)
top of saved-paths group →   LAYER_FOCUSED_TOP         (focused path, colored)
                             LAYER_FOCUSED_HALO        (focused path, white wider)
                             LAYER_SAVED_COMMON        (outer paths, single color)
                             per-path rating layers    (gradient, viewport-culled)
```

## Components

### 1. `SOURCE_FOCUSED_HALO` — recreate with line metrics

The current source is constructed without options:

```kotlin
GeoJsonSource(SOURCE_FOCUSED_HALO, FeatureCollection.fromFeatures(emptyArray()))
```

`lineGradient` with `Expression.lineProgress()` requires the source to compute line metrics. Replace with:

```kotlin
GeoJsonSource(
    SOURCE_FOCUSED_HALO,
    FeatureCollection.fromFeatures(emptyArray()),
    ratingSourceOptions(withMetrics = true),
)
```

(`ratingSourceOptions(withMetrics = true)` is already used by per-path rating sources, so no new helper needed.)

### 2. `LAYER_FOCUSED_HALO` — move from bottom to top

Position changes:

```kotlin
// before
style.addLayerBelow(layer, LAYER_SAVED_COMMON)

// after
style.addLayerAbove(layer, LAYER_SAVED_COMMON)
```

Properties (`lineWidth * 2f`, white, opacity 0.9, round cap/join) stay the same.

### 3. `LAYER_FOCUSED_TOP` — new layer for the colored focused line

New constant near the existing layer-id constants:

```kotlin
private const val LAYER_FOCUSED_TOP = "wt-focused-top-layer"
```

Installed inside `installHaloLayer` (rename optional — see "Naming" below), right after the halo layer:

```kotlin
val topLayer = LineLayer(LAYER_FOCUSED_TOP, SOURCE_FOCUSED_HALO).withProperties(
    PropertyFactory.lineWidth(lineWidth),
    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
)
style.addLayerAbove(topLayer, LAYER_FOCUSED_HALO)
```

No initial color/gradient — `setFocusedPath(...)` applies it on every focus change.

### 4. `setFocusedPath(pathId: Long?)` — extend to apply gradient

Current body sets the source GeoJson. Extended body also applies the gradient/color to `LAYER_FOCUSED_TOP` using `lineGradient` for both rating and common paths (so the property path is uniform and the rating gradient infrastructure can be reused for the rating case).

Add a small private helper for the uniform-color "gradient" used by common paths:

```kotlin
private fun uniformGradientExpression(@ColorInt argb: Int): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        Expression.literal(0f), Expression.color(argb),
        Expression.literal(1f), Expression.color(argb),
    )
```

Then the extended `setFocusedPath`:

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

    val feature: Feature?
    val gradientExpr: Expression
    when {
        ratingPath != null -> {
            feature = PathGeoJsonMapper.ratingPathToFeature(ratingPath)
            val stops = PathGradientStopsBuilder.build(ratingPath, blendMeters).ifEmpty {
                listOf(
                    GradientStop(0f, SegmentRating.NONE),
                    GradientStop(1f, SegmentRating.NONE),
                )
            }
            gradientExpr = gradientExpression(stops)
        }
        commonPath != null -> {
            feature = PathGeoJsonMapper.commonPathToFeature(commonPath)
            gradientExpr = uniformGradientExpression(commonPathColor)
        }
        else -> return  // unknown id — leave source/layer untouched
    }
    if (feature == null) return

    source.setGeoJson(feature)
    style.getLayerAs<LineLayer>(LAYER_FOCUSED_TOP)?.setProperties(
        PropertyFactory.lineGradient(gradientExpr)
    )
    currentFocusedPathId = pathId
}
```

The `.ifEmpty { ... }` fallback mirrors the existing `applyGradient` defensive default — keeps `lineGradient` always set so it can never be `null` and shadow a fallback `lineColor`.

### 5. Replay after style swap — no new code

The existing `onStyleLoaded` already calls `setFocusedPath(replay)` after installing layers and pushing sources. Once `setFocusedPath` is extended (Step 4), the new `LAYER_FOCUSED_TOP` will get the right gradient on replay automatically.

The `installHaloLayer` guard (`if (style.getSource(SOURCE_FOCUSED_HALO) != null) return`) needs verification: after style swap the new `Style` instance is fresh, so the source check passes and both layers reinstall. No change needed.

## Naming

`installHaloLayer` no longer installs only the halo — it now installs both halo and the colored top layer. Rename to `installFocusedLayers` for honesty. Single rename, no behavior change.

## Edge Cases

- **Unknown pathId (race with hide):** Current code returns early when the path is in neither `savedRatingPaths` nor `savedCommonPaths`. We preserve this — the source and layer remain at their previous state, which the VM-side ordering already guarantees is consistent (the VM clears focus before hiding, per T6).

- **Empty rating stops:** A rating path with no usable segments. `PathGradientStopsBuilder.build` returns `emptyList()` in that case. The `.ifEmpty { ... }` fallback applies a uniform NONE-colored gradient — same as the existing `applyGradient` behavior. The path is rendered as a uniform gray line on top. Acceptable.

- **Common path color matches halo color (#FFFFFF):** Hypothetical only; `commonPathColor` is configured elsewhere. If a user-chosen color matches the halo color the focused-top layer becomes invisible against the halo. Out of scope.

## Testing

No unit tests (the controller has none today). On-device manual verification:

1. Show several overlapping paths in MY_PATHS, focus one whose route intersects others → focused path and its halo render unobstructed on top.
2. Same test in OUTER_PATHS with overlapping outer paths.
3. Mixed: focus an OUTER path that crosses several rating paths → still on top.
4. Active recording while focused: confirm `LAYER_CURRENT` still renders above the focused path.
5. Change tile source mid-session while focused → halo and colored top reappear correctly (relies on the existing `currentFocusedPathId` replay).
6. Clear focus → both layers go empty, no stale rendering.

## Risk Notes

- **Source-options change:** `SOURCE_FOCUSED_HALO` is replaced with a metric-enabled source. The source name is the same, so anything caching the previous source reference may stale. The controller fetches via `style.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO)` at use time — no caching — so this is safe.

- **Double-draw of the focused path:** The path is rendered both in its original rating/common layer (where it can be overlapped) and in the new top layer (visible). The visible rendering wins, the obscured one is wasted work. Acceptable cost for visual correctness; could be optimized later by hiding the original layer when focused, but that adds complexity (visibility toggle, viewport-culling interaction). Defer.

- **Per-path rating stop computation:** `PathGradientStopsBuilder.build(path, blendMeters)` ran on Dispatchers.Default in `pushSavedRating`. Here we run it synchronously on Main from `setFocusedPath`. Path sizes are typically small (one path's segments); acceptable on Main. If profiling shows jank for large paths, move to the existing `scope.launch { withContext(Dispatchers.Default) ... }` pattern.

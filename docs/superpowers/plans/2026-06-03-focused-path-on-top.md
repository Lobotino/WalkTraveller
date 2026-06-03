# Focused Path On Top Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the focused path and its halo on top of all other saved paths so the user can clearly see which path they just focused, even when other paths overlap it.

**Architecture:** Move the existing `LAYER_FOCUSED_HALO` from the bottom of the saved-paths group to just above `LAYER_SAVED_COMMON`. Add a new `LAYER_FOCUSED_TOP` directly above the halo that renders the focused path with its own gradient (rating) or a uniform color (common). Both layers read from the existing `SOURCE_FOCUSED_HALO`, which is recreated with line metrics so the top layer's `lineGradient` works.

**Tech Stack:** Kotlin, Android, MapLibre Android SDK.

---

## Files

**Modify:**
- `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

No new files. No test files (the controller has no unit tests; verification is on-device).

---

## Task 1: Recreate source with metrics, add top layer, extend `setFocusedPath`

All controller-side changes in one focused commit. The changes are tightly coupled — the new top layer requires a metric-enabled source, and an empty top layer without a gradient applier in `setFocusedPath` would just be dead code. Bundle them so each intermediate build state is meaningful.

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`

- [ ] **Step 1: Read the controller file to ground on real identifiers**

Open `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt`. Confirm:
- `installHaloLayer(style: Style)` exists with the current single-layer install body
- `LAYER_FOCUSED_HALO` and `SOURCE_FOCUSED_HALO` constants exist in the `companion object` at the bottom
- `ratingSourceOptions(withMetrics: Boolean)` helper exists
- `gradientExpression(stops: List<GradientStop>)` and `applyGradient(...)` helpers exist
- `GradientStop`, `SegmentRating`, `commonPathColor`, `blendMeters` are all accessible from inside the class
- `Expression`, `PropertyFactory`, `LineLayer`, `GeoJsonSource`, `Style`, `FeatureCollection`, `Feature` are imported

If any identifier is named differently in the real code, use the real name throughout the steps below (the rest of the steps assume the names exactly as listed).

- [ ] **Step 2: Add `LAYER_FOCUSED_TOP` constant**

In the `companion object` near the other layer constants, add:

```kotlin
private const val LAYER_FOCUSED_TOP = "wt-focused-top-layer"
```

- [ ] **Step 3: Add `uniformGradientExpression` private helper**

Place it next to `gradientExpression(stops)` (around line 332). Import `androidx.annotation.ColorInt` if not already imported.

```kotlin
private fun uniformGradientExpression(@ColorInt argb: Int): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        Expression.literal(0f), Expression.color(argb),
        Expression.literal(1f), Expression.color(argb),
    )
```

- [ ] **Step 4: Recreate `SOURCE_FOCUSED_HALO` with line metrics and rename + extend `installHaloLayer`**

Replace the body of `installHaloLayer(style: Style)` (around line 196) with this. While at it, rename the function to `installFocusedLayers` for honesty (it now installs two layers).

```kotlin
private fun installFocusedLayers(style: Style) {
    if (style.getSource(SOURCE_FOCUSED_HALO) != null) return
    style.addSource(
        GeoJsonSource(
            SOURCE_FOCUSED_HALO,
            FeatureCollection.fromFeatures(emptyArray()),
            ratingSourceOptions(withMetrics = true),
        )
    )
    val haloLayer = LineLayer(LAYER_FOCUSED_HALO, SOURCE_FOCUSED_HALO).withProperties(
        PropertyFactory.lineWidth(lineWidth * 2f),
        PropertyFactory.lineColor(Color.parseColor("#FFFFFF")),
        PropertyFactory.lineOpacity(0.9f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayerAbove(haloLayer, LAYER_SAVED_COMMON)

    val topLayer = LineLayer(LAYER_FOCUSED_TOP, SOURCE_FOCUSED_HALO).withProperties(
        PropertyFactory.lineWidth(lineWidth),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayerAbove(topLayer, LAYER_FOCUSED_HALO)
}
```

Note three changes from the previous body:
1. Source created with `ratingSourceOptions(withMetrics = true)` instead of no options.
2. Halo layer positioned with `addLayerAbove(haloLayer, LAYER_SAVED_COMMON)` instead of `addLayerBelow(haloLayer, LAYER_SAVED_COMMON)`.
3. New `topLayer` installed via `addLayerAbove(topLayer, LAYER_FOCUSED_HALO)`.

Update the comment at line 69-71 in `onStyleLoaded` to reflect the new z-order (top → bottom: LAYER_CURRENT > LAYER_FOCUSED_TOP > LAYER_FOCUSED_HALO > LAYER_SAVED_COMMON > rating layers):

```kotlin
// Z-order top→bottom: LAYER_CURRENT > LAYER_FOCUSED_TOP > LAYER_FOCUSED_HALO >
// LAYER_SAVED_COMMON > per-path rating layers. Focused-top and halo sit just
// above the saved group so the focused path is visible over other paths.
```

- [ ] **Step 5: Update the `installHaloLayer` call site in `onStyleLoaded`**

The call at line 74 (`installHaloLayer(style)`) becomes `installFocusedLayers(style)`. There are no other call sites to update.

- [ ] **Step 6: Extend `setFocusedPath` to apply gradient to the top layer**

Replace the body of `setFocusedPath(pathId: Long?)` (around line 170) with:

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

The shape change versus the old body: the rating/common dispatch now also computes a `gradientExpr` (either the path's gradient stops via `gradientExpression(stops)` or `uniformGradientExpression(commonPathColor)`), and after writing the source we call `setProperties(lineGradient(...))` on `LAYER_FOCUSED_TOP`.

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL with only pre-existing warnings (no errors, no new warnings about this file).

- [ ] **Step 8: Run all unit tests as regression**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 123 tests pass, 0 failures. (No new tests; the VM-level focused-path tests still pass because nothing about the VM contract changed.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/MapLibrePathController.kt
git commit -m "Render focused path on top of other saved paths"
```

No Co-Authored-By trailer.

---

## Task 2: On-device manual verification

The controller has no unit tests; correctness of layer order and gradient application is verified on the emulator/device. Type-check passing does not prove the halo renders on top.

- [ ] **Step 1: Install the debug build on the device**

From a host shell (or Android Studio Run): `./gradlew :app:installDebug`

If the install fails on `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the existing app first (`adb uninstall ru.lobotino.walktraveller`) then re-install. This loses saved-path data — re-record a few test paths after installing.

- [ ] **Step 2: Arrange overlapping paths**

In MY_PATHS: have at least two saved rating paths whose routes visibly cross each other on the map. If you don't already have such paths, record two short overlapping routes or use existing ones with crossing geometry.

In OUTER_PATHS: have at least two outer paths that cross each other.

- [ ] **Step 3: Verify rating focused-on-top**

Open the MY_PATHS menu, show both paths on the map. Tap one of the rows.

Expect:
- Row turns amber (existing behavior — verifies the focus event reached the list).
- That path on the map renders with its full gradient ON TOP of the other path at every crossing.
- A white halo is visible behind it, also on top of the other path.
- Camera fits to the focused path (existing behavior).

Tap a different row: focus migrates, the new path renders on top.

Tap the focused row again: focus clears, both paths return to their normal rendering (no halo, no on-top layer).

- [ ] **Step 4: Verify common (outer) focused-on-top**

Repeat Step 3 in the OUTER_PATHS menu. The focused outer path should render with its single color on top of the other outer path, with white halo behind it.

- [ ] **Step 5: Verify mixed (focus outer that crosses ratings)**

Show both MY_PATHS and OUTER_PATHS paths simultaneously such that an outer path crosses some rating paths. Focus the outer path → it should render on top of the rating paths at the crossings.

Then focus a rating path that the outer crosses → the rating focused path should render on top of the outer path. (Halo and focused-top sit above `LAYER_SAVED_COMMON`, so both directions work.)

- [ ] **Step 6: Verify active recording stays on top**

Start a new path recording (the user's "current" recording). Focus an existing saved path. The current recording must continue to render ABOVE the focused path at any crossing. (LAYER_CURRENT is still installed last; the new layers sit below it.)

- [ ] **Step 7: Verify style swap**

Change the tile source via Settings (or whichever UI surfaces tile-source selection). The focused path must reappear on top of other paths with its halo, because `onStyleLoaded` replays `setFocusedPath(currentFocusedPathId)` — the new top layer should pick up the gradient via the same replay.

- [ ] **Step 8: Verify clear focus**

Tap the focused row to clear focus → both the halo and the colored top layer disappear; other paths render normally.

- [ ] **Step 9: Take a screenshot showing two crossing paths with one focused, halo and full color visible on top**

Capture and attach to the PR description (or future commit, per repo convention).

- [ ] **Step 10: If any step fails — diagnose, fix, re-run unit tests, re-verify on device, then commit fixes**

Likely failure modes:
- Halo or top layer not visible at all → source/layer install order off, or `lineGradient` not applied → re-read Task 1 Step 6.
- Focused path rendered in normal position but NOT on top → `addLayerAbove` argument wrong (probably referencing a layer that doesn't exist yet at install time) → check the install order in `onStyleLoaded`.
- After style swap, halo white-only (no gradient on top) → `pendingFocusedPathId`/`currentFocusedPathId` replay missing the new layer → re-check Step 6's gradient-apply block.
- Common path focused but renders white-only → `uniformGradientExpression(commonPathColor)` not applied → trace the `commonPath != null` branch.

```bash
git add <fixed-file>
git commit -m "Fix <observed issue> in focused-on-top rendering"
```

---

## Self-Review

After writing the plan, I checked against the spec at `docs/superpowers/specs/2026-06-03-focused-path-on-top-design.md`:

- **§ Architecture (two new top layers, halo moved, single source)** — Task 1 Steps 2–5 ✓
- **§ Component 1: source recreation with `withMetrics = true`** — Task 1 Step 4 ✓
- **§ Component 2: halo position move** — Task 1 Step 4 (the `addLayerAbove` change) ✓
- **§ Component 3: new `LAYER_FOCUSED_TOP` constant + layer install** — Task 1 Steps 2 and 4 ✓
- **§ Component 4: extended `setFocusedPath` with gradient application** — Task 1 Step 6 ✓
- **§ Component 5: replay after style swap (no new code)** — existing `onStyleLoaded` replays via `setFocusedPath(replay)`; Task 1 Step 6's extended body handles the gradient on replay automatically ✓
- **§ Naming: rename `installHaloLayer` → `installFocusedLayers`** — Task 1 Steps 4 and 5 ✓
- **§ Edge cases (unknown pathId, empty rating stops, common-color match)** — handled in Step 6's `when`/`.ifEmpty {}` and called out as "leave source/layer untouched" comment ✓
- **§ Testing (on-device manual)** — Task 2 covers all six spec scenarios plus current-recording z-order ✓
- **§ Risk: source-options change → cache staleness** — no caching in code; the existing pattern is `style.getSourceAs<...>(NAME)` at use time; covered implicitly ✓
- **§ Risk: double-draw** — accepted in spec, no action needed ✓
- **§ Risk: synchronous gradient build on Main** — accepted in spec, no action; if profiling shows jank, fix later ✓

Type consistency: identifiers used in Task 1 — `installFocusedLayers`, `LAYER_FOCUSED_TOP`, `uniformGradientExpression`, `setFocusedPath`, `currentFocusedPathId`, `pendingFocusedPathId` — match the spec and existing code precisely. The rating/common dispatch in `setFocusedPath` uses the same `savedRatingPaths` / `savedCommonPaths` maps populated by the existing feature.

Placeholder scan: No "TBD", "TODO", "implement later", or under-specified blocks. Every code change shows the actual code to type. The "if any step fails" block in Task 2 Step 10 lists concrete diagnoses, not a generic "fix it" prompt.

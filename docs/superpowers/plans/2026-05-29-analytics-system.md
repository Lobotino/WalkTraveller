# Analytics System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an abstract analytics system to WalkTraveller and use Firebase Analytics as one concrete logger, tracking 8 basic usage events.

**Architecture:** A typed `AnalyticsEvent` sealed catalog flows into `IAnalyticsTracker` (the "sending system"), whose `AnalyticsTracker` implementation fans each event out to a list of `IAnalyticsLogger`s. `FirebaseAnalyticsLogger` (GA backend) and a debug logcat logger are the loggers. The event→Firebase mapping is a pure function so it is unit-testable without a Firebase backend.

**Tech Stack:** Kotlin 2.0, Android (manual DI via ViewModel factories), Firebase Analytics (already a dependency), JUnit4.

---

## File Structure

**New files (`app/src/main/java/ru/lobotino/walktraveller/analytics/`):**
- `AnalyticsEvent.kt` — sealed event catalog + `AnalyticsPathType` enum.
- `IAnalyticsTracker.kt` — the "sending system" interface (`track(event)`).
- `IAnalyticsLogger.kt` — abstract logger interface (`log(event)`).
- `AnalyticsTracker.kt` — fan-out implementation with per-logger error isolation.
- `FirebasePayload.kt` — `FirebasePayload` data class + pure `AnalyticsEvent.toFirebasePayload()` mapping (no Firebase imports).
- `FirebaseAnalyticsLogger.kt` — GA logger; turns payload into a `Bundle` and calls `FirebaseAnalytics.logEvent`.
- `DebugLogAnalyticsLogger.kt` — logs each event to logcat (debug builds only).

**New test files (`app/src/test/java/ru/lobotino/walktraveller/analytics/`):**
- `FirebasePayloadMappingTest.kt` — asserts each event maps to the correct name/params.
- `AnalyticsTrackerTest.kt` — fan-out + error isolation.

**Modified files:**
- `app/build.gradle` — enable `testOptions.unitTests.returnDefaultValues` so tested code may call `android.util.Log`.
- `App.kt` — build the app-scoped `AnalyticsTracker` singleton.
- `viewmodels/MapViewModel.kt` + `di/MapViewModelFactory.kt` — inject tracker; emit started/finished/rating/volume events.
- `viewmodels/PathsMenuViewModel.kt` + `di/PathsMenuViewModelFactory.kt` — inject tracker; emit shared/import/path-shown events.
- `ui/MainMapFragment.kt` — pass tracker into both factories; emit `ScreenView("map")`.
- `ui/SettingsFragment.kt` — emit `ScreenView("settings")`.
- `ui/FirstWelcomeFragment.kt` — emit `ScreenView("welcome")`.

**Testing note:** `AnalyticsTracker` and the `toFirebasePayload()` mapping are pure and TDD'd (Tasks 2–3). The ViewModel/fragment call-site wiring (Tasks 6–9) is plain one-line instrumentation; `MapViewModel` cannot be constructed in a pure JVM test (it depends on the Android-coupled concrete `GeoPermissionsUseCase`) and the project has no Robolectric. That wiring is verified by compilation (typed events make a wrong event a compile error) plus a logcat smoke test via `DebugLogAnalyticsLogger` (Task 10). Adding Robolectric/Mockito just to assert one-line emissions is out of scope.

---

## Task 1: Analytics event catalog + interfaces

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/AnalyticsEvent.kt`
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/IAnalyticsLogger.kt`
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/IAnalyticsTracker.kt`

These are pure declarations (no behavior), so there is no test for this task.

- [ ] **Step 1: Create the event catalog**

`AnalyticsEvent.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import ru.lobotino.walktraveller.model.SegmentRating

enum class AnalyticsPathType { RATING, COMMON }

sealed class AnalyticsEvent {
    data object TrackRecordingStarted : AnalyticsEvent()
    data object TrackRecordingFinished : AnalyticsEvent()
    data class TrackShared(val pathsCount: Int) : AnalyticsEvent()
    data class RatingGiven(val rating: SegmentRating) : AnalyticsEvent()
    data class OuterPathImported(val pathsCount: Int) : AnalyticsEvent()
    data class PathShown(val pathsCount: Int, val type: AnalyticsPathType) : AnalyticsEvent()
    data class ScreenView(val screenName: String) : AnalyticsEvent()
    data class VolumeFeatureSuggest(val accepted: Boolean) : AnalyticsEvent()
}
```

- [ ] **Step 2: Create the logger interface**

`IAnalyticsLogger.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

interface IAnalyticsLogger {
    fun log(event: AnalyticsEvent)
}
```

- [ ] **Step 3: Create the tracker interface**

`IAnalyticsTracker.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

interface IAnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/analytics/
git commit -m "Add analytics event catalog and interfaces"
```

---

## Task 2: Firebase payload mapping (TDD)

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/FirebasePayload.kt`
- Test: `app/src/test/java/ru/lobotino/walktraveller/analytics/FirebasePayloadMappingTest.kt`

- [ ] **Step 1: Write the failing test**

`FirebasePayloadMappingTest.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating

class FirebasePayloadMappingTest {

    @Test
    fun `track recording started maps to event name without params`() {
        val payload = AnalyticsEvent.TrackRecordingStarted.toFirebasePayload()
        assertEquals("track_recording_started", payload.name)
        assertEquals(emptyMap<String, Any>(), payload.params)
    }

    @Test
    fun `track recording finished maps to event name without params`() {
        val payload = AnalyticsEvent.TrackRecordingFinished.toFirebasePayload()
        assertEquals("track_recording_finished", payload.name)
        assertEquals(emptyMap<String, Any>(), payload.params)
    }

    @Test
    fun `track shared maps paths count`() {
        val payload = AnalyticsEvent.TrackShared(pathsCount = 3).toFirebasePayload()
        assertEquals("track_shared", payload.name)
        assertEquals(mapOf("paths_count" to 3), payload.params)
    }

    @Test
    fun `rating given maps lowercased rating name`() {
        val payload = AnalyticsEvent.RatingGiven(SegmentRating.PERFECT).toFirebasePayload()
        assertEquals("rating_given", payload.name)
        assertEquals(mapOf("rating" to "perfect"), payload.params)
    }

    @Test
    fun `outer path imported maps paths count`() {
        val payload = AnalyticsEvent.OuterPathImported(pathsCount = 2).toFirebasePayload()
        assertEquals("outer_path_imported", payload.name)
        assertEquals(mapOf("paths_count" to 2), payload.params)
    }

    @Test
    fun `path shown maps count and lowercased type`() {
        val payload = AnalyticsEvent.PathShown(5, AnalyticsPathType.COMMON).toFirebasePayload()
        assertEquals("path_shown", payload.name)
        assertEquals(mapOf("paths_count" to 5, "path_type" to "common"), payload.params)
    }

    @Test
    fun `screen view maps screen name`() {
        val payload = AnalyticsEvent.ScreenView("map").toFirebasePayload()
        assertEquals("screen_view", payload.name)
        assertEquals(mapOf("screen_name" to "map"), payload.params)
    }

    @Test
    fun `volume feature suggest maps accepted as string`() {
        val payload = AnalyticsEvent.VolumeFeatureSuggest(accepted = true).toFirebasePayload()
        assertEquals("volume_feature_suggest", payload.name)
        assertEquals(mapOf("accepted" to "true"), payload.params)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.analytics.FirebasePayloadMappingTest"`
Expected: FAIL — compilation error, `toFirebasePayload` / `FirebasePayload` unresolved.

- [ ] **Step 3: Write the mapping**

`FirebasePayload.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

data class FirebasePayload(
    val name: String,
    val params: Map<String, Any>,
)

fun AnalyticsEvent.toFirebasePayload(): FirebasePayload = when (this) {
    AnalyticsEvent.TrackRecordingStarted ->
        FirebasePayload("track_recording_started", emptyMap())

    AnalyticsEvent.TrackRecordingFinished ->
        FirebasePayload("track_recording_finished", emptyMap())

    is AnalyticsEvent.TrackShared ->
        FirebasePayload("track_shared", mapOf("paths_count" to pathsCount))

    is AnalyticsEvent.RatingGiven ->
        FirebasePayload("rating_given", mapOf("rating" to rating.name.lowercase()))

    is AnalyticsEvent.OuterPathImported ->
        FirebasePayload("outer_path_imported", mapOf("paths_count" to pathsCount))

    is AnalyticsEvent.PathShown ->
        FirebasePayload(
            "path_shown",
            mapOf("paths_count" to pathsCount, "path_type" to type.name.lowercase()),
        )

    is AnalyticsEvent.ScreenView ->
        FirebasePayload("screen_view", mapOf("screen_name" to screenName))

    is AnalyticsEvent.VolumeFeatureSuggest ->
        FirebasePayload("volume_feature_suggest", mapOf("accepted" to accepted.toString()))
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.analytics.FirebasePayloadMappingTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/analytics/FirebasePayload.kt app/src/test/java/ru/lobotino/walktraveller/analytics/FirebasePayloadMappingTest.kt
git commit -m "Add event to Firebase payload mapping with tests"
```

---

## Task 3: AnalyticsTracker fan-out (TDD)

**Files:**
- Modify: `app/build.gradle` (add `testOptions` block)
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/AnalyticsTracker.kt`
- Test: `app/src/test/java/ru/lobotino/walktraveller/analytics/AnalyticsTrackerTest.kt`

- [ ] **Step 1: Allow android.util.Log in unit tests**

`AnalyticsTracker` logs logger failures via `android.util.Log`, which throws "not mocked" in plain JVM tests unless default return values are enabled. In `app/build.gradle`, inside the `android { ... }` block (e.g. directly after the `lint { ... }` block), add:

```groovy
    testOptions {
        unitTests {
            returnDefaultValues = true
        }
    }
```

- [ ] **Step 2: Write the failing test**

`AnalyticsTrackerTest.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTrackerTest {

    private class RecordingAnalyticsLogger : IAnalyticsLogger {
        val events = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) {
            events.add(event)
        }
    }

    private class ThrowingAnalyticsLogger : IAnalyticsLogger {
        override fun log(event: AnalyticsEvent) {
            throw IllegalStateException("boom")
        }
    }

    @Test
    fun `event is delivered to every logger`() {
        val first = RecordingAnalyticsLogger()
        val second = RecordingAnalyticsLogger()
        val tracker = AnalyticsTracker(listOf(first, second))

        tracker.track(AnalyticsEvent.TrackRecordingStarted)

        assertEquals(listOf(AnalyticsEvent.TrackRecordingStarted), first.events)
        assertEquals(listOf(AnalyticsEvent.TrackRecordingStarted), second.events)
    }

    @Test
    fun `a failing logger does not stop the others`() {
        val healthy = RecordingAnalyticsLogger()
        val tracker = AnalyticsTracker(listOf(ThrowingAnalyticsLogger(), healthy))

        tracker.track(AnalyticsEvent.TrackShared(pathsCount = 1))

        assertEquals(listOf(AnalyticsEvent.TrackShared(pathsCount = 1)), healthy.events)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.analytics.AnalyticsTrackerTest"`
Expected: FAIL — compilation error, `AnalyticsTracker` unresolved.

- [ ] **Step 4: Write the implementation**

`AnalyticsTracker.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import android.util.Log

class AnalyticsTracker(
    private val loggers: List<IAnalyticsLogger>,
) : IAnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        for (logger in loggers) {
            try {
                logger.log(event)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Analytics logger failed for event $event", throwable)
            }
        }
    }

    companion object {
        private const val TAG = "AnalyticsTracker"
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.analytics.AnalyticsTrackerTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/src/main/java/ru/lobotino/walktraveller/analytics/AnalyticsTracker.kt app/src/test/java/ru/lobotino/walktraveller/analytics/AnalyticsTrackerTest.kt
git commit -m "Add AnalyticsTracker fan-out with logger error isolation"
```

---

## Task 4: FirebaseAnalyticsLogger

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/FirebaseAnalyticsLogger.kt`

No pure unit test: this class only adapts the (already-tested) payload to the Firebase SDK. Verified by compilation in Task 10 and the logcat smoke test.

**Error handling:** the logger does not add its own try/catch. `AnalyticsTracker` (Task 3) already wraps every `logger.log(event)` call, so a single central catch isolates failures from all loggers. A per-logger try/catch here would be redundant. (This consolidates the two error-handling bullets in the spec into one DRY mechanism with the same guarantee: a logger failure never crashes the app and never blocks other loggers.)

- [ ] **Step 1: Write the implementation**

`FirebaseAnalyticsLogger.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsLogger(
    private val firebaseAnalytics: FirebaseAnalytics,
) : IAnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val payload = event.toFirebasePayload()
        firebaseAnalytics.logEvent(payload.name, payload.params.toBundle())
    }

    private fun Map<String, Any>.toBundle(): Bundle {
        val bundle = Bundle()
        for ((key, value) in this) {
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is String -> bundle.putString(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/analytics/FirebaseAnalyticsLogger.kt
git commit -m "Add Firebase Analytics logger"
```

---

## Task 5: DebugLogAnalyticsLogger

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/analytics/DebugLogAnalyticsLogger.kt`

- [ ] **Step 1: Write the implementation**

`DebugLogAnalyticsLogger.kt`:

```kotlin
package ru.lobotino.walktraveller.analytics

import android.util.Log

class DebugLogAnalyticsLogger : IAnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val payload = event.toFirebasePayload()
        Log.d(TAG, "event=${payload.name} params=${payload.params}")
    }

    companion object {
        private const val TAG = "Analytics"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/analytics/DebugLogAnalyticsLogger.kt
git commit -m "Add debug logcat analytics logger"
```

---

## Task 6: Wire the tracker singleton in App

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/App.kt`

- [ ] **Step 1: Replace App.kt with the wired version**

```kotlin
package ru.lobotino.walktraveller

import android.app.Application
import android.os.StrictMode
import android.preference.PreferenceManager
import com.google.firebase.analytics.FirebaseAnalytics
import org.osmdroid.config.Configuration
import ru.lobotino.walktraveller.analytics.AnalyticsTracker
import ru.lobotino.walktraveller.analytics.DebugLogAnalyticsLogger
import ru.lobotino.walktraveller.analytics.FirebaseAnalyticsLogger
import ru.lobotino.walktraveller.analytics.IAnalyticsLogger
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker

class App : Application() {

    lateinit var analyticsTracker: IAnalyticsTracker
        private set

    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        analyticsTracker = AnalyticsTracker(
            buildList<IAnalyticsLogger> {
                add(FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(this@App)))
                if (BuildConfig.DEBUG) {
                    add(DebugLogAnalyticsLogger())
                }
            }
        )
    }

    companion object {
        const val SHARED_PREFS_TAG = "walk_traveller_shared_prefs"
        const val PATH_DATABASE_NAME = "walk_traveller"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/App.kt
git commit -m "Create app-scoped analytics tracker in App"
```

---

## Task 7: MapViewModel events

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`

- [ ] **Step 1: Add the tracker to the factory**

In `MapViewModelFactory.kt`, add the import near the other imports:

```kotlin
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
```

Add a constructor parameter after `resourceManager`:

```kotlin
    private val resourceManager: IResourceManager,
    private val analyticsTracker: IAnalyticsTracker,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?,
```

Pass it as the last argument to the `MapViewModel(...)` constructor call (after `resourceManager`):

```kotlin
                resourceManager,
                analyticsTracker
            ) as T
```

- [ ] **Step 2: Add the tracker to the ViewModel constructor**

In `MapViewModel.kt`, add the imports near the other imports:

```kotlin
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
```

Add the constructor parameter after `resourceManager`:

```kotlin
    private val resourceManager: IResourceManager,
    private val analyticsTracker: IAnalyticsTracker,
) : ViewModel() {
```

- [ ] **Step 3: Emit "recording started"**

In `startPathTracking()`, inside the `allGranted` lambda, immediately after `writingPathNowState.tryEmit(true)`:

```kotlin
            writingPathStatesRepository.setWritingPathNow(true)
            writingPathNowState.tryEmit(true)
            analyticsTracker.track(AnalyticsEvent.TrackRecordingStarted)
```

- [ ] **Step 4: Emit "recording finished"**

In `onStopPathButtonClicked()`, immediately after `finishPathWritingUseCase.finishPathWriting()`:

```kotlin
    fun onStopPathButtonClicked() {
        finishPathWritingUseCase.finishPathWriting()
        analyticsTracker.track(AnalyticsEvent.TrackRecordingFinished)
```

- [ ] **Step 5: Emit "rating given"**

In `onRatingButtonClicked(ratingGiven: SegmentRating)`, as the first line:

```kotlin
    fun onRatingButtonClicked(ratingGiven: SegmentRating) {
        analyticsTracker.track(AnalyticsEvent.RatingGiven(ratingGiven))
        pathRatingUseCase.setCurrentRating(ratingGiven)
```

- [ ] **Step 6: Emit "volume feature suggest" (accept + decline)**

Update both handlers:

```kotlin
    fun onVolumeFeatureSuggestAccepted() {
        analyticsTracker.track(AnalyticsEvent.VolumeFeatureSuggest(accepted = true))
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        userInfoRepository.setVolumeKeysRatingEnabled(true)
        startPathTracking()
    }

    fun onVolumeFeatureSuggestDecline() {
        analyticsTracker.track(AnalyticsEvent.VolumeFeatureSuggest(accepted = false))
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        userInfoRepository.setVolumeKeysRatingEnabled(false)
        startPathTracking()
    }
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `MainMapFragment` does not yet pass `analyticsTracker` to `MapViewModelFactory`. This is fixed in Task 9. (If you are running tasks strictly in isolation, defer this compile check to Task 9.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt
git commit -m "Emit analytics events from MapViewModel"
```

---

## Task 8: PathsMenuViewModel events

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/di/PathsMenuViewModelFactory.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`

- [ ] **Step 1: Add the tracker to the factory**

In `PathsMenuViewModelFactory.kt`, add the import:

```kotlin
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
```

Add a constructor parameter after `pathRedactor`:

```kotlin
    private val pathRedactor: IPathRedactor,
    private val analyticsTracker: IAnalyticsTracker,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?
```

Pass it as the last argument to the `PathsMenuViewModel(...)` constructor call:

```kotlin
                pathRedactor,
                analyticsTracker
            ) as T
```

- [ ] **Step 2: Add the tracker to the ViewModel constructor**

In `PathsMenuViewModel.kt`, add the imports:

```kotlin
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
import ru.lobotino.walktraveller.analytics.AnalyticsPathType
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
```

Add the constructor parameter after `pathRedactor`:

```kotlin
    private val pathRedactor: IPathRedactor,
    private val analyticsTracker: IAnalyticsTracker
) : ViewModel() {
```

- [ ] **Step 3: Emit "track shared"**

In `onShareSelectedPathsButtonClicked(...)`, immediately after the successful `shareFileChannel.trySend(...)` line inside the `try` block:

```kotlin
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPathList(selectedPaths))
                    analyticsTracker.track(AnalyticsEvent.TrackShared(selectedPaths.size))
                } catch (exception: IOException) {
```

- [ ] **Step 4: Emit "outer path imported"**

Update `onOuterPathsConfirmButtonClicked()` so the coroutine captures the count before saving:

```kotlin
    fun onOuterPathsConfirmButtonClicked() {
        selectedPathIdsInMenuList.clear()

        viewModelScope.launch {
            val importedCount = outerPathsInteractor.getCachedOuterPaths().size
            outerPathsInteractor.saveCachedPaths()
            if (importedCount > 0) {
                analyticsTracker.track(AnalyticsEvent.OuterPathImported(importedCount))
            }
        }
```

(Leave the rest of the method unchanged.)

- [ ] **Step 5: Emit "path shown" for selected rated paths**

In `loadAndShowSelectedRatedPaths()`, immediately after `newMapEventChannel.trySend(MapEvent.ShowRatingPathList(loadedPaths))`:

```kotlin
                if (loadedPaths.isNotEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ShowRatingPathList(loadedPaths))
                    analyticsTracker.track(AnalyticsEvent.PathShown(loadedPaths.size, AnalyticsPathType.RATING))
```

- [ ] **Step 6: Emit "path shown" for selected common paths**

In `loadAndShowSelectedPathsAsCommon()`, immediately after `newMapEventChannel.trySend(MapEvent.ShowCommonPathList(loadedPaths))`:

```kotlin
                if (loadedPaths.isNotEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ShowCommonPathList(loadedPaths))
                    analyticsTracker.track(AnalyticsEvent.PathShown(loadedPaths.size, AnalyticsPathType.COMMON))
```

- [ ] **Step 7: Emit "path shown" for all rated paths**

Replace the body of `loadAndShowAllRatedPaths()` so the list is captured and counted:

```kotlin
    private fun loadAndShowAllRatedPaths() {
        loadPathsJob?.cancel()
        loadPathsJob = viewModelScope.launch {
            val allPaths = mapPathsInteractor.getAllSavedRatingPaths(true)
            for (path in allPaths) {
                newMapEventChannel.trySend(MapEvent.ShowRatingPath(path))
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.Single(path.pathId),
                            PathInfoItemShowButtonState.HIDE
                        )
                    )
                )
            }
            if (allPaths.isNotEmpty()) {
                analyticsTracker.track(AnalyticsEvent.PathShown(allPaths.size, AnalyticsPathType.RATING))
            }
            updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
        }
    }
```

- [ ] **Step 8: Emit "path shown" for all common paths**

Replace the body of `loadAndShowAllPathsAsCommon()` so the list is captured and counted:

```kotlin
    private fun loadAndShowAllPathsAsCommon() {
        loadPathsJob?.cancel()
        loadPathsJob = viewModelScope.launch {
            val allPaths = mapPathsInteractor.getAllSavedPathsAsCommon()
            for (path in allPaths) {
                newMapEventChannel.trySend(MapEvent.ShowCommonPath(path))
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.Single(path.pathId),
                            PathInfoItemShowButtonState.HIDE
                        )
                    )
                )
            }
            if (allPaths.isNotEmpty()) {
                analyticsTracker.track(AnalyticsEvent.PathShown(allPaths.size, AnalyticsPathType.COMMON))
            }
            updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
        }
    }
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/di/PathsMenuViewModelFactory.kt app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt
git commit -m "Emit analytics events from PathsMenuViewModel"
```

---

## Task 9: Fragment screen views + factory wiring

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt`

- [ ] **Step 1: Import the event type in MainMapFragment**

In `MainMapFragment.kt`, add near the other imports:

```kotlin
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
```

(`ru.lobotino.walktraveller.App` is already imported.)

- [ ] **Step 2: Resolve the tracker once in initViewModel**

In `initViewModel(bundle: Bundle?)`, right after the `sharedPreferences = ...` assignment (around line 428), add:

```kotlin
            val analyticsTracker = (requireContext().applicationContext as App).analyticsTracker
```

- [ ] **Step 3: Pass the tracker into PathsMenuViewModelFactory**

In the `PathsMenuViewModelFactory(...)` call, add the argument right before `owner = this`:

```kotlin
                    pathRedactor = pathRedactor,
                    analyticsTracker = analyticsTracker,
                    owner = this,
                    bundle = bundle
```

- [ ] **Step 4: Pass the tracker into MapViewModelFactory**

In the `MapViewModelFactory(...)` call, add the argument right before `owner = this` (after `resourceManager = ...`):

```kotlin
                        resourceManager = ResourceManager(requireContext().applicationContext),
                        analyticsTracker = analyticsTracker,
                        owner = this,
                        bundle = bundle
```

- [ ] **Step 5: Emit ScreenView in MainMapFragment.onResume**

In the existing `onResume()`, after `super.onResume()`:

```kotlin
    override fun onResume() {
        super.onResume()
        (requireContext().applicationContext as App).analyticsTracker
            .track(AnalyticsEvent.ScreenView("map"))
        mapView.onResume()
```

- [ ] **Step 6: Add ScreenView to SettingsFragment**

In `SettingsFragment.kt`, add the imports:

```kotlin
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
```

(If `App` is already imported for `App.SHARED_PREFS_TAG`, do not duplicate it.)

Add an `onResume` override to the `SettingsFragment` class body:

```kotlin
    override fun onResume() {
        super.onResume()
        (requireContext().applicationContext as App).analyticsTracker
            .track(AnalyticsEvent.ScreenView("settings"))
    }
```

- [ ] **Step 7: Add ScreenView to FirstWelcomeFragment**

In `FirstWelcomeFragment.kt`, add the imports:

```kotlin
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
```

(If `App` is already imported, do not duplicate it.)

Add an `onResume` override to the `FirstWelcomeFragment` class body:

```kotlin
    override fun onResume() {
        super.onResume()
        (requireContext().applicationContext as App).analyticsTracker
            .track(AnalyticsEvent.ScreenView("welcome"))
    }
```

- [ ] **Step 8: Verify the whole module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (all factory call sites now supply `analyticsTracker`).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt
git commit -m "Wire analytics tracker into fragments and emit screen views"
```

---

## Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `FirebasePayloadMappingTest` (8) and `AnalyticsTrackerTest` (2) pass, existing tests still pass.

- [ ] **Step 2: Run detekt (project style gate)**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If new style violations appear in the analytics files, fix them and re-run.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual logcat smoke test**

Install the debug build on a device/emulator, then run:
`adb logcat -s Analytics:D`

Exercise the app and confirm one log line per action:
- Start recording → `event=track_recording_started params={}`
- Stop recording → `event=track_recording_finished params={}`
- Give a rating → `event=rating_given params={rating=...}`
- Accept/decline the volume-keys dialog → `event=volume_feature_suggest params={accepted=true|false}`
- Share selected paths → `event=track_shared params={paths_count=N}`
- Show paths on the map → `event=path_shown params={paths_count=N, path_type=rating|common}`
- Confirm an imported outer path → `event=outer_path_imported params={paths_count=N}`
- Open map / settings / welcome screens → `event=screen_view params={screen_name=map|settings|welcome}`

(Optional) In the Firebase console, enable DebugView via `adb shell setprop debug.firebase.analytics.app ru.lobotino.walktraveller` and confirm events arrive in the linked GA4 property.

- [ ] **Step 5: Final commit (if any detekt fixes were made)**

```bash
git add -A
git commit -m "Fix analytics code style issues"
```

---

## Done

All 8 events are emitted through `IAnalyticsTracker` → `FirebaseAnalyticsLogger` (GA) plus the debug logger. Adding a new logger is a one-line change in `App.onCreate()`; adding an opt-out later means gating inside `AnalyticsTracker.track` or swapping the logger list — no call sites change.

**Cleanup reminder:** Per the user's standing preference, delete this plan and the design spec (`docs/superpowers/specs/2026-05-29-analytics-system-design.md`) once implementation is complete and merged — they are working artifacts, not committed docs.

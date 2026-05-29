# Volume Keys via MediaSession — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `AccessibilityService`-based volume-key rating control with an active `MediaSession` + `VolumeProvider`, preserving the exact single/double-tap rating behavior, and add a settings toggle for the feature.

**Architecture:** A pure `VolumeKeysRatingDetector` holds the single/double-tap debounce state machine (unit-tested). A `RatingVolumeKeysController` wraps a platform `MediaSession` whose remote `VolumeProvider` feeds the detector; it is started/released by the existing `WritingPathService` only while a path is being recorded and only when the settings toggle is on. The legacy accessibility service, permission repository, and permission-request UI flow are deleted.

**Tech Stack:** Kotlin, Android `android.media.session.MediaSession` + `android.media.VolumeProvider` (platform, API 21+, no new runtime deps), kotlinx-coroutines, JUnit4 + kotlinx-coroutines-test.

---

## File Structure

**Create:**
- `app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysRatingDetector.kt` — pure debounce state machine.
- `app/src/main/java/ru/lobotino/walktraveller/services/RatingVolumeKeysController.kt` — MediaSession + VolumeProvider lifecycle wrapper.
- `app/src/test/java/ru/lobotino/walktraveller/VolumeKeysRatingDetectorTest.kt` — unit tests for the detector.

**Modify:**
- `app/build.gradle` — add `testImplementation` kotlinx-coroutines-test.
- `app/src/main/java/ru/lobotino/walktraveller/utils/Constants.kt` — add `RATING_CHANGES_BROADCAST`.
- `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IUserInfoRepository.kt` — add toggle methods.
- `app/src/main/java/ru/lobotino/walktraveller/repositories/UserInfoRepository.kt` — implement toggle methods.
- `app/src/main/java/ru/lobotino/walktraveller/services/WritingPathService.kt` — host the controller.
- `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt` — update broadcast import; remove accessibility wiring; remove `VolumeButtonsFeatureInfo` dialog case.
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt` — simplify volume-feature flow (drop permission step).
- `app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt` — drop `volumeKeysListenerPermissionsInteractor`.
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/ConfirmDialogType.kt` — drop `VolumeButtonsFeatureInfo`.
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/SettingsViewModel.kt` — add toggle state + handler.
- `app/src/main/java/ru/lobotino/walktraveller/di/SettingsViewModelFactory.kt` — pass `IUserInfoRepository`.
- `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt` — wire the switch.
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/SettingsUiState.kt` — add `volumeKeysRatingEnabled`.
- `app/src/main/res/layout/fragment_settings.xml` — add the switch row.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml` — add toggle label; remove obsolete strings.
- `app/src/main/AndroidManifest.xml` — remove the accessibility `<service>` block.

**Delete:**
- `app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysDetectorService.kt`
- `app/src/main/res/xml/accessibility_layout.xml`
- `app/src/main/java/ru/lobotino/walktraveller/repositories/permissions/AccessibilityPermissionRepository.kt`
- `app/src/main/java/ru/lobotino/walktraveller/usecases/permissions/VolumeKeysListenerPermissionsUseCase.kt`
- `app/src/main/java/ru/lobotino/walktraveller/ui/dialog/VolumeButtonsPermissionsInfoDialog.kt`

---

## Task 1: VolumeKeysRatingDetector (pure debounce logic, TDD)

**Files:**
- Modify: `app/build.gradle` (test deps)
- Create: `app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysRatingDetector.kt`
- Test: `app/src/test/java/ru/lobotino/walktraveller/VolumeKeysRatingDetectorTest.kt`

- [ ] **Step 1: Add the coroutines-test dependency**

In `app/build.gradle`, in the `dependencies { }` block, directly after the line `testImplementation 'junit:junit:4.13.2'` (line 89), add:

```groovy
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/ru/lobotino/walktraveller/VolumeKeysRatingDetectorTest.kt`:

```kotlin
package ru.lobotino.walktraveller

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.services.VolumeKeysRatingDetector

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeKeysRatingDetectorTest {

    @Test
    fun `single volume down sets NORMAL only after the delay`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        assertTrue("nothing should fire before the delay elapses", results.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(SegmentRating.NORMAL), results)
    }

    @Test
    fun `double volume down sets BADLY and never NORMAL`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.onVolumeDown()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.BADLY), results)
    }

    @Test
    fun `single volume up sets GOOD only after the delay`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeUp()
        assertTrue(results.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(SegmentRating.GOOD), results)
    }

    @Test
    fun `double volume up sets PERFECT and never GOOD`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeUp()
        detector.onVolumeUp()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.PERFECT), results)
    }

    @Test
    fun `opposite key press cancels the pending rating`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.onVolumeUp()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.GOOD), results)
    }

    @Test
    fun `release cancels pending rating`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.release()
        advanceUntilIdle()

        assertTrue(results.isEmpty())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "ru.lobotino.walktraveller.VolumeKeysRatingDetectorTest"`
Expected: FAIL — `VolumeKeysRatingDetector` is unresolved (compilation error).

- [ ] **Step 4: Implement the detector**

Create `app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysRatingDetector.kt`:

```kotlin
package ru.lobotino.walktraveller.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating

/**
 * Single/double volume-key tap detection for path rating.
 * 1 tap (after the debounce window) = NORMAL/GOOD, 2 taps within the window = BADLY/PERFECT.
 * Pressing the opposite direction cancels the pending single-tap rating.
 */
class VolumeKeysRatingDetector(
    private val scope: CoroutineScope,
    private val onRatingDetected: (SegmentRating) -> Unit,
) {

    private var downRatingJob: Job? = null
    private var upRatingJob: Job? = null

    fun onVolumeDown() {
        upRatingJob?.cancel()
        if (downRatingJob?.isActive == true) {
            downRatingJob?.cancel()
            downRatingJob = null
            onRatingDetected(SegmentRating.BADLY)
        } else {
            downRatingJob = scope.launch {
                delay(BEFORE_CHANGE_RATING_DELAY)
                downRatingJob = null
                onRatingDetected(SegmentRating.NORMAL)
            }
        }
    }

    fun onVolumeUp() {
        downRatingJob?.cancel()
        if (upRatingJob?.isActive == true) {
            upRatingJob?.cancel()
            upRatingJob = null
            onRatingDetected(SegmentRating.PERFECT)
        } else {
            upRatingJob = scope.launch {
                delay(BEFORE_CHANGE_RATING_DELAY)
                upRatingJob = null
                onRatingDetected(SegmentRating.GOOD)
            }
        }
    }

    fun release() {
        downRatingJob?.cancel()
        upRatingJob?.cancel()
        downRatingJob = null
        upRatingJob = null
    }

    companion object {
        const val BEFORE_CHANGE_RATING_DELAY = 500L
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "ru.lobotino.walktraveller.VolumeKeysRatingDetectorTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysRatingDetector.kt app/src/test/java/ru/lobotino/walktraveller/VolumeKeysRatingDetectorTest.kt
git commit -m "Add VolumeKeysRatingDetector with single/double-tap rating logic"
```

---

## Task 2: RatingVolumeKeysController (MediaSession + VolumeProvider)

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/services/RatingVolumeKeysController.kt`

- [ ] **Step 1: Implement the controller**

Create `app/src/main/java/ru/lobotino/walktraveller/services/RatingVolumeKeysController.kt`:

```kotlin
package ru.lobotino.walktraveller.services

import android.content.Context
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.lobotino.walktraveller.model.SegmentRating

/**
 * Captures hardware volume-key presses without an AccessibilityService by holding an active
 * MediaSession whose playback volume is "remote": while active, volume keys are routed to
 * [VolumeProvider.onAdjustVolume] instead of changing the device volume. Active only between
 * [start] and [release].
 */
class RatingVolumeKeysController(
    private val context: Context,
    private val onRatingDetected: (SegmentRating) -> Unit,
) {

    private var mediaSession: MediaSession? = null
    private var scope: CoroutineScope? = null
    private var detector: VolumeKeysRatingDetector? = null

    fun start() {
        if (mediaSession != null) return

        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ratingDetector = VolumeKeysRatingDetector(controllerScope, onRatingDetected)

        val volumeProvider = object : VolumeProvider(
            VOLUME_CONTROL_RELATIVE,
            MAX_VOLUME,
            CURRENT_VOLUME
        ) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> ratingDetector.onVolumeUp()
                    direction < 0 -> ratingDetector.onVolumeDown()
                }
            }
        }

        mediaSession = MediaSession(context, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {})
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }
        scope = controllerScope
        detector = ratingDetector
    }

    fun release() {
        detector?.release()
        detector = null
        scope?.cancel()
        scope = null
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
    }

    companion object {
        private const val MEDIA_SESSION_TAG = "WalkTravellerVolumeKeys"
        private const val MAX_VOLUME = 100
        private const val CURRENT_VOLUME = 50
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/services/RatingVolumeKeysController.kt
git commit -m "Add RatingVolumeKeysController wrapping MediaSession volume capture"
```

---

## Task 3: Broadcast constant + UserInfoRepository toggle

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/utils/Constants.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IUserInfoRepository.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/repositories/UserInfoRepository.kt`

- [ ] **Step 1: Add the broadcast constant**

In `app/src/main/java/ru/lobotino/walktraveller/utils/Constants.kt`, directly below the existing line `const val APPLICATION_ID = BuildConfig.APPLICATION_ID` (line 15), add:

```kotlin
const val RATING_CHANGES_BROADCAST = "$APPLICATION_ID.rating_broadcast"
```

- [ ] **Step 2: Add toggle methods to the interface**

In `app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IUserInfoRepository.kt`, add inside the interface (after `fun needToSuggestVolumeFeature(): Boolean`):

```kotlin

    fun setVolumeKeysRatingEnabled(enabled: Boolean)

    fun isVolumeKeysRatingEnabled(): Boolean
```

- [ ] **Step 3: Implement the toggle methods**

In `app/src/main/java/ru/lobotino/walktraveller/repositories/UserInfoRepository.kt`:

Add the preference key inside the `companion object` (after the `NEED_TO_SUGGEST_VOLUME_BUTTONS_FEATURE` line):

```kotlin
        private const val VOLUME_KEYS_RATING_ENABLED = "volume_keys_rating_enabled"
```

Add the implementations at the end of the class (after `needToSuggestVolumeFeature()`):

```kotlin

    override fun setVolumeKeysRatingEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(VOLUME_KEYS_RATING_ENABLED, enabled)
            apply()
        }
    }

    override fun isVolumeKeysRatingEnabled(): Boolean {
        return sharedPreferences.getBoolean(VOLUME_KEYS_RATING_ENABLED, true)
    }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/utils/Constants.kt app/src/main/java/ru/lobotino/walktraveller/repositories/interfaces/IUserInfoRepository.kt app/src/main/java/ru/lobotino/walktraveller/repositories/UserInfoRepository.kt
git commit -m "Add rating broadcast constant and volume-keys-rating toggle storage"
```

---

## Task 4: Wire controller into WritingPathService + DEVICE VERIFICATION

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/services/WritingPathService.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt:90`

- [ ] **Step 1: Add imports to WritingPathService**

In `app/src/main/java/ru/lobotino/walktraveller/services/WritingPathService.kt`, add these imports (alongside the existing imports):

```kotlin
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.usecases.PathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.utils.RATING_CHANGES_BROADCAST
```

(`android.content.Intent` and `PathRatingUseCase`/`PathRatingRepository`/`VibrationRepository` may already be imported — do not duplicate; keep one of each.)

- [ ] **Step 2: Add fields and lifecycle wiring**

In `WritingPathService`, add fields next to the other `private lateinit var` declarations:

```kotlin
    private lateinit var userInfoRepository: IUserInfoRepository
    private lateinit var volumeRatingUseCase: IPathRatingUseCase
    private lateinit var ratingVolumeKeysController: RatingVolumeKeysController
```

In `onCreate()`, after `initLocalPathRepository()`, add a call:

```kotlin
        initVolumeKeysController()
```

Add the init method (place it near the other `init*` methods):

```kotlin
    private fun initVolumeKeysController() {
        userInfoRepository = UserInfoRepository(sharedPreferences)
        volumeRatingUseCase = PathRatingUseCase(
            PathRatingRepository(sharedPreferences),
            VibrationRepository(applicationContext)
        )
        ratingVolumeKeysController = RatingVolumeKeysController(applicationContext) { rating ->
            volumeRatingUseCase.setCurrentRating(rating)
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent(RATING_CHANGES_BROADCAST))
        }
    }
```

- [ ] **Step 3: Start/release the controller with path tracking**

In `startWritingPath()` (currently lines 179-182), change it to:

```kotlin
    private fun startWritingPath() {
        locationUpdatesRepository.startLocationUpdates()
        startForegroundNotification()
        if (userInfoRepository.isVolumeKeysRatingEnabled()) {
            ratingVolumeKeysController.start()
        }
    }
```

In `finishWritingPath()` (currently lines 184-189), add the release call before `stopSelf()`:

```kotlin
    fun finishWritingPath() {
        pathInteractor.finishCurrentPath()
        locationUpdatesRepository.stopLocationUpdates()
        ratingVolumeKeysController.release()
        stopForegroundNotification()
        stopSelf()
    }
```

In `onDestroy()` (currently lines 173-177), add a release call before `super.onDestroy()` (guards the process-death path):

```kotlin
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        locationUpdatesRepository.stopLocationUpdates()
        ratingVolumeKeysController.release()
        super.onDestroy()
    }
```

- [ ] **Step 4: Point MainMapFragment at the new broadcast constant**

In `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`, replace line 90:

```kotlin
import ru.lobotino.walktraveller.services.VolumeKeysDetectorService.Companion.RATING_CHANGES_BROADCAST
```

with:

```kotlin
import ru.lobotino.walktraveller.utils.RATING_CHANGES_BROADCAST
```

- [ ] **Step 5: Build and install the debug APK**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL; app installed on a connected device/emulator.

- [ ] **Step 6: DEVICE VERIFICATION SPIKE (manual — critical gate)**

This confirms MediaSession volume capture actually works before we delete the accessibility fallback. On a real device:

1. Open the app, start recording a path.
2. With the **screen on**, press Volume Up once → after ~0.5s expect a single vibration and the on-screen rating turning GOOD. Press Volume Up twice quickly → double vibration, PERFECT. Repeat for Volume Down → NORMAL / BADLY.
3. **Lock the screen** (screen off), put the phone in a pocket, press Volume Up/Down → expect the same vibrations (rating still changes; confirm afterward in the recorded path).
4. Start **music playback in another app**, then repeat step 2. Note whether volume keys still drive ratings or instead change music volume.
5. Stop recording → confirm Volume keys change device volume normally again (controller released).

Record the outcome:
- If steps 1-3 work, proceed. Step 4 conflict-with-music is acceptable (same limitation as the old accessibility approach, which also consumed the keys).
- If volume keys are NOT captured when screen is off / music plays, STOP and apply the fallback below before continuing.

- [ ] **Step 7: (Only if Step 6 failed) Strengthen session priority**

Add audio-focus acquisition and a silent looping audio track so the session reliably becomes the volume target. In `RatingVolumeKeysController.start()`, before `isActive = true`, request audio focus via `AudioManager.requestAudioFocus` with an `AudioFocusRequest` (`AUDIOFOCUS_GAIN`), and start a silent `MediaPlayer` looping a short silent resource; abandon focus and stop/release the player in `release()`. Re-run Step 6. (Implement only if needed — silent audio has battery cost.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/services/WritingPathService.kt app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Drive volume-key rating from WritingPathService via MediaSession"
```

---

## Task 5: Settings toggle UI

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/SettingsUiState.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/SettingsViewModel.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/di/SettingsViewModelFactory.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Add the toggle to SettingsUiState**

Replace the contents of `app/src/main/java/ru/lobotino/walktraveller/ui/model/SettingsUiState.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.TileSourceType

data class SettingsUiState(
    val optimizePathsValue: Float,
    val mapStyleValue: TileSourceType,
    val volumeKeysRatingEnabled: Boolean
)
```

- [ ] **Step 2: Add state + handler to SettingsViewModel**

In `app/src/main/java/ru/lobotino/walktraveller/viewmodels/SettingsViewModel.kt`:

Add the import:

```kotlin
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
```

Add a constructor parameter (after `notificationPermissionsRepository`):

```kotlin
    private val userInfoRepository: IUserInfoRepository,
```

Update the `settingsUiState` initializer to include the new field:

```kotlin
    private val settingsUiState =
        MutableStateFlow(
            SettingsUiState(
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f,
                tileSourceInteractor.getCurrentTileSourceType(),
                userInfoRepository.isVolumeKeysRatingEnabled()
            )
        )
```

Add the handler method (after `onMapStyleChosen`):

```kotlin
    fun onVolumeKeysRatingToggle(enabled: Boolean) {
        userInfoRepository.setVolumeKeysRatingEnabled(enabled)
        settingsUiState.value = settingsUiState.value.copy(volumeKeysRatingEnabled = enabled)
    }
```

- [ ] **Step 3: Pass the repository through the factory**

In `app/src/main/java/ru/lobotino/walktraveller/di/SettingsViewModelFactory.kt`:

Add the import:

```kotlin
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
```

Add the constructor parameter (after `notificationPermissionsRepository`):

```kotlin
    private val userInfoRepository: IUserInfoRepository,
```

Add it to the `SettingsViewModel(...)` call (after `notificationPermissionsRepository`, before `resourceManager`):

```kotlin
                userInfoRepository,
```

- [ ] **Step 4: Add the string resources**

In `app/src/main/res/values/strings.xml`, add:

```xml
    <string name="settings_volume_keys_rating_title">Rating control with volume buttons</string>
```

In `app/src/main/res/values-ru/strings.xml`, add:

```xml
    <string name="settings_volume_keys_rating_title">Оценка пути кнопками громкости</string>
```

- [ ] **Step 5: Add the switch to the layout**

In `app/src/main/res/layout/fragment_settings.xml`, add this row immediately after the closing `</FrameLayout>` of the map-style row (after line 42, before the `MaterialDivider` on line 44):

```xml
    <com.google.android.material.divider.MaterialDivider
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp" />

    <androidx.appcompat.widget.SwitchCompat
        android:id="@+id/volume_keys_rating_switch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginTop="12dp"
        android:text="@string/settings_volume_keys_rating_title"
        android:textColor="@color/black"
        android:textSize="18sp" />
```

- [ ] **Step 6: Wire the switch in SettingsFragment**

In `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt`:

Add the import:

```kotlin
import androidx.appcompat.widget.SwitchCompat
```

Pass `UserInfoRepository` to the factory. In `initViewModel`, add this import-side construction by inserting into the `SettingsViewModelFactory(...)` call (after the `notificationPermissionsRepository = ...` argument):

```kotlin
                    userInfoRepository = ru.lobotino.walktraveller.repositories.UserInfoRepository(
                        sharedPreferences
                    ),
```

Add a field next to the other view fields:

```kotlin
    private lateinit var volumeKeysRatingSwitch: SwitchCompat
```

In `initViews(view)`, add (e.g. after the `mapStyleSpinner` block):

```kotlin
        volumeKeysRatingSwitch =
            view.findViewById<SwitchCompat>(R.id.volume_keys_rating_switch).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.onVolumeKeysRatingToggle(isChecked)
                }
            }
```

In `syncUiState(uiState)`, add (only update when the value differs to avoid listener loops):

```kotlin
        if (volumeKeysRatingSwitch.isChecked != uiState.volumeKeysRatingEnabled) {
            volumeKeysRatingSwitch.isChecked = uiState.volumeKeysRatingEnabled
        }
```

- [ ] **Step 7: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/SettingsUiState.kt app/src/main/java/ru/lobotino/walktraveller/viewmodels/SettingsViewModel.kt app/src/main/java/ru/lobotino/walktraveller/di/SettingsViewModelFactory.kt app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt app/src/main/res/layout/fragment_settings.xml app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "Add settings toggle for volume-keys rating control"
```

---

## Task 6: Simplify the feature-suggestion flow (drop permission step)

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/ConfirmDialogType.kt`

- [ ] **Step 1: Remove the permission use case from MapViewModel**

In `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`:

Delete the constructor parameter (line 46):

```kotlin
    private val volumeKeysListenerPermissionsUseCase: IPermissionsUseCase,
```

Delete the companion constant (lines 63-64):

```kotlin
        private const val START_REQUEST_VOLUME_KEYS_PERMISSION_KEY =
            "START_REQUEST_VOLUME_KEYS_PERMISSION"
```

Replace `needToAskVolumeButtonsPermissions()` (lines 143-145) with a suggestion-only check:

```kotlin
    private fun needToSuggestVolumeFeature(): Boolean {
        return userInfoRepository.needToSuggestVolumeFeature()
    }
```

Update the call site in `onStartPathButtonClicked()` (line 208) from `if (needToAskVolumeButtonsPermissions())` to:

```kotlin
        if (needToSuggestVolumeFeature()) {
```

Replace `onVolumeFeatureSuggestAccepted()` (lines 476-479) so accepting enables the feature and starts tracking directly:

```kotlin
    fun onVolumeFeatureSuggestAccepted() {
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        userInfoRepository.setVolumeKeysRatingEnabled(true)
        startPathTracking()
    }
```

Replace `onVolumeFeatureSuggestDecline()` (lines 481-484) so declining disables the feature:

```kotlin
    fun onVolumeFeatureSuggestDecline() {
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        userInfoRepository.setVolumeKeysRatingEnabled(false)
        startPathTracking()
    }
```

Delete `onVolumeFeaturePermissionsInfoConfirm()` (lines 486-492) and `syncRequestPermissionsState()` (lines 494-509) entirely.

Remove the `syncRequestPermissionsState()` call inside `onResume()` (line 174).

If `IPermissionsUseCase` is now unused in this file, remove its import (line 38: `import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase`). Note `notificationsPermissionsUseCase` and `geoPermissionsUseCase` remain — `notificationsPermissionsUseCase` is still typed `IPermissionsUseCase`, so the import likely stays; keep it if still referenced.

- [ ] **Step 2: Remove the param from MapViewModelFactory**

In `app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt`:

Delete the constructor parameter (line 24):

```kotlin
    private val volumeKeysListenerPermissionsInteractor: IPermissionsUseCase,
```

Delete the argument passed to `MapViewModel(...)` (line 49):

```kotlin
                volumeKeysListenerPermissionsInteractor,
```

- [ ] **Step 3: Remove accessibility wiring + info dialog from MainMapFragment**

In `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`:

Delete the `volumeKeysListenerPermissionsInteractor = ...` argument in the `MapViewModelFactory(...)` call (lines 593-597):

```kotlin
                        volumeKeysListenerPermissionsInteractor = VolumeKeysListenerPermissionsUseCase(
                            AccessibilityPermissionRepository(
                                requireContext().applicationContext
                            )
                        ),
```

Delete the now-unused imports (lines 82 and 119):

```kotlin
import ru.lobotino.walktraveller.repositories.permissions.AccessibilityPermissionRepository
import ru.lobotino.walktraveller.usecases.permissions.VolumeKeysListenerPermissionsUseCase
```

Delete the `ConfirmDialogType.VolumeButtonsFeatureInfo` branch (lines 778-783):

```kotlin
                ConfirmDialogType.VolumeButtonsFeatureInfo -> {
                    VolumeButtonsPermissionsInfoDialog(
                        context = context,
                        onYesClicked = { mapViewModel.onVolumeFeaturePermissionsInfoConfirm() }
                    ).show()
                }
```

If `VolumeButtonsPermissionsInfoDialog` is imported, remove that import too.

- [ ] **Step 4: Remove the dialog type**

In `app/src/main/java/ru/lobotino/walktraveller/ui/model/ConfirmDialogType.kt`, delete line 12:

```kotlin
    object VolumeButtonsFeatureInfo : ConfirmDialogType
```

- [ ] **Step 5: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If the compiler flags an unused import, remove it.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt app/src/main/java/ru/lobotino/walktraveller/di/MapViewModelFactory.kt app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt app/src/main/java/ru/lobotino/walktraveller/ui/model/ConfirmDialogType.kt
git commit -m "Simplify volume-feature suggestion flow, drop accessibility permission step"
```

---

## Task 7: Delete legacy accessibility code + obsolete strings

**Files:**
- Delete: `VolumeKeysDetectorService.kt`, `accessibility_layout.xml`, `AccessibilityPermissionRepository.kt`, `VolumeKeysListenerPermissionsUseCase.kt`, `VolumeButtonsPermissionsInfoDialog.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Remove the accessibility service from the manifest**

In `app/src/main/AndroidManifest.xml`, delete the entire `<service ... VolumeKeysDetectorService ...>` block (lines 72-82):

```xml
        <service
            android:name=".services.VolumeKeysDetectorService"
            android:exported="false"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_layout" />
        </service>
```

- [ ] **Step 2: Delete the obsolete source and resource files**

Run:

```bash
git rm app/src/main/java/ru/lobotino/walktraveller/services/VolumeKeysDetectorService.kt \
       app/src/main/res/xml/accessibility_layout.xml \
       app/src/main/java/ru/lobotino/walktraveller/repositories/permissions/AccessibilityPermissionRepository.kt \
       app/src/main/java/ru/lobotino/walktraveller/usecases/permissions/VolumeKeysListenerPermissionsUseCase.kt \
       app/src/main/java/ru/lobotino/walktraveller/ui/dialog/VolumeButtonsPermissionsInfoDialog.kt
```

- [ ] **Step 3: Remove obsolete strings**

In both `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml`, delete these three entries (they relate only to the removed accessibility-permission dialog/error):

```xml
    <string name="volume_buttons_feature_info_title">...</string>
    <string name="volume_buttons_feature_info_desc">...</string>
    <string name="error_message_not_allow_access_to_volume_buttons">...</string>
```

Keep `volume_buttons_feature_request_title`, `volume_buttons_feature_request_desc`, and all `volume_buttons_feature_tutorial_*` / `volume_feature_tutorial_*` strings (still used by the suggestion dialog).

- [ ] **Step 4: Verify no dangling references**

Run: `grep -rn "VolumeKeysDetectorService\|AccessibilityPermissionRepository\|VolumeKeysListenerPermissionsUseCase\|VolumeButtonsPermissionsInfoDialog\|VolumeButtonsFeatureInfo\|error_message_not_allow_access_to_volume_buttons\|volume_buttons_feature_info" app/src/main`
Expected: no output.

- [ ] **Step 5: Full build + unit tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 6: Final manual regression on device**

Install (`./gradlew installDebug`) and verify end-to-end:
1. Fresh start (or after clearing the "suggest" flag): starting a path shows the volume-feature suggestion dialog. Accept → tracking starts and volume keys drive ratings.
2. Decline on a later run → tracking starts but volume keys change device volume (feature off).
3. Settings → toggle the switch off, start a path → volume keys do NOT drive ratings. Toggle on → they do.
4. Rating changes update the on-screen rating indicator (broadcast path intact).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "Remove legacy AccessibilityService volume-key implementation"
```

---

## Post-implementation cleanup (project convention)

After all tasks are complete and verified, delete BOTH this plan and the design spec in a single commit (working artifacts are not kept in this repo's history):

```bash
git rm docs/superpowers/plans/2026-05-29-volume-keys-mediasession.md \
       docs/superpowers/specs/2026-05-29-volume-keys-mediasession-design.md
git commit -m "Remove volume-keys MediaSession spec and plan working artifacts"
```

---

## Self-Review

**Spec coverage:**
- "MediaSession + VolumeProvider in WritingPathService" → Task 2 (controller), Task 4 (wiring). ✓
- "Functionality 1:1 (single/double tap, 500ms, opposite-key cancel)" → Task 1 detector + tests. ✓
- "Vibration preserved" → Task 4 routes rating through `PathRatingUseCase` (unchanged vibration). ✓
- "RATING_CHANGES_BROADCAST preserved + consumers" → Task 3 (constant), Task 4 (send + MainMapFragment import). ✓
- "Only while writing path" → Task 4 start/release tied to `startWritingPath`/`finishWritingPath`/`onDestroy`. ✓
- "Delete legacy (service, xml, manifest, repo, usecase, dialog, error string)" → Task 6 (dialog/flow) + Task 7 (files, manifest, strings). ✓
- "Keep suggestion dialog, drop permission step" → Task 6. ✓
- "Settings toggle on/off" → Task 3 (storage) + Task 5 (UI). ✓
- "Device spike first / fallback if routing weak" → Task 4 Steps 6-7. ✓
- "No migration code; no permission-status row" → out of scope, not implemented. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code. The only conditional task (Task 4 Step 7 fallback) is gated on the device-spike result, which is the spec's stated approach. ✓

**Type consistency:** `VolumeKeysRatingDetector(scope, onRatingDetected)` with `onVolumeUp()/onVolumeDown()/release()` used identically in Task 1, 2. `RatingVolumeKeysController(context, onRatingDetected)` with `start()/release()` used identically in Task 2, 4. `isVolumeKeysRatingEnabled()/setVolumeKeysRatingEnabled()` consistent across Tasks 3, 4, 5, 6. `RATING_CHANGES_BROADCAST` defined once (Task 3) and referenced in Tasks 4. `volumeKeysRatingEnabled` field consistent across SettingsUiState/ViewModel/Fragment (Task 5). ✓

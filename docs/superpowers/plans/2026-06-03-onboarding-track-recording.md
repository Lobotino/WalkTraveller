# Onboarding with Animated Track Recording — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single welcome screen with a 4-slide swipe onboarding that shows an animated, color-rated track being recorded, keeps the existing privacy consent on the last slide, and exposes the privacy policy / terms from Settings.

**Architecture:** `FirstWelcomeFragment` hosts a `ViewPager2` with 4 pages (3 tutorial slides + 1 consent slide) plus a shared bottom bar (Skip / dots / primary button). Tutorial slides render a custom `TrackRecordingAnimationView` (Canvas + `ValueAnimator`) that draws a growing polyline colored with the app's rating palette. All non-Android geometry/color logic lives in a pure `WelcomeTrackGeometry` object that is unit-tested on the JVM.

**Tech Stack:** Kotlin, Android Views (no Compose/Lottie), `androidx.viewpager2:1.0.0`, `androidx.recyclerview:1.2.1` (both already on the classpath), JUnit4 + MockK + kotlinx-coroutines-test for tests.

---

## File Structure

**Create:**
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/TrackAnimationMode.kt` — enum of the 3 tutorial animation modes.
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometry.kt` — pure geometry + rating-color mapping (+ `WelcomePoint`).
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomePage.kt` — sealed model of onboarding pages.
- `app/src/main/java/ru/lobotino/walktraveller/ui/view/TrackRecordingAnimationView.kt` — custom Canvas animation view.
- `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/WelcomeSlidesAdapter.kt` — `ViewPager2` adapter with 2 page types.
- `app/src/main/res/layout/item_welcome_tutorial.xml` — tutorial page layout.
- `app/src/main/res/layout/item_welcome_consent.xml` — consent page layout.
- `app/src/main/res/drawable/bg_welcome_dot.xml` — page-indicator dot.
- `app/src/test/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometryTest.kt` — geometry unit tests.
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/FirstWelcomeViewModelTest.kt` — consent-gating unit tests.

**Modify:**
- `app/src/main/res/layout/first_welcome_fragment.xml` — replace content with pager + bottom bar.
- `app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt` — host the pager.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml` — new strings.
- `app/src/main/res/layout/fragment_settings.xml` — add privacy/terms buttons.
- `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt` — wire privacy/terms buttons.

**Unchanged (do NOT touch):** `FirstWelcomeViewModel.kt` logic (only add tests), `WelcomeTutorialStep.kt`, `UserInfoRepository.kt`, navigation in `MainActivity.kt`.

---

## Task 1: Pure track geometry (`WelcomeTrackGeometry`)

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/model/TrackAnimationMode.kt`
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometry.kt`
- Test: `app/src/test/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometryTest.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating

class WelcomeTrackGeometryTest {

    private val sut = WelcomeTrackGeometry

    @Test
    fun ratingForSegmentCyclesThroughPaletteAndNeverNone() {
        // given
        val ratings = (0 until 8).map { sut.ratingForSegment(it) }

        // then
        assertEquals(SegmentRating.BADLY, ratings[0])
        assertEquals(SegmentRating.NORMAL, ratings[1])
        assertEquals(SegmentRating.GOOD, ratings[2])
        assertEquals(SegmentRating.PERFECT, ratings[3])
        assertEquals(SegmentRating.BADLY, ratings[4])
        assertTrue(ratings.none { it == SegmentRating.NONE })
    }

    @Test
    fun visibleSegmentCountClampsToBounds() {
        // then
        assertEquals(0, sut.visibleSegmentCount(0f))
        assertEquals(0, sut.visibleSegmentCount(-1f))
        assertEquals(sut.segmentCount, sut.visibleSegmentCount(1f))
        assertEquals(sut.segmentCount, sut.visibleSegmentCount(2f))
    }

    @Test
    fun headPositionMovesFromFirstToLastPoint() {
        // given
        val start = sut.headPosition(0f)
        val end = sut.headPosition(1f)
        val middle = sut.headPosition(0.5f)

        // then
        assertEquals(sut.trackPoints.first(), start)
        assertEquals(sut.trackPoints.last(), end)
        assertNotEquals(start, middle)
        assertNotEquals(end, middle)
    }

    @Test
    fun trackHasManySegments() {
        // then: "add more segments" — track must be visually dense
        assertTrue("expected a dense track", sut.segmentCount >= 10)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.model.WelcomeTrackGeometryTest"`
Expected: FAIL — `WelcomeTrackGeometry` / `TrackAnimationMode` unresolved (compilation error).

- [ ] **Step 3: Create `TrackAnimationMode`**

Create `app/src/main/java/ru/lobotino/walktraveller/ui/model/TrackAnimationMode.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.model

enum class TrackAnimationMode {
    RECORD, RATE, VOLUME
}
```

- [ ] **Step 4: Create `WelcomeTrackGeometry`**

Create `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometry.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.SegmentRating

/** A normalized (0f..1f) point on the onboarding track. Android-free for JVM tests. */
data class WelcomePoint(val x: Float, val y: Float)

/**
 * Pure geometry and rating-color logic for the onboarding track animation.
 * Coordinates are normalized 0f..1f and scaled to view bounds by the View.
 */
object WelcomeTrackGeometry {

    /** Rating palette, "worse -> better". Never includes NONE. */
    private val palette = listOf(
        SegmentRating.BADLY,
        SegmentRating.NORMAL,
        SegmentRating.GOOD,
        SegmentRating.PERFECT,
    )

    /** Stylized winding walk. Many points => many segments (dense colored line). */
    val trackPoints: List<WelcomePoint> = listOf(
        WelcomePoint(0.08f, 0.88f),
        WelcomePoint(0.18f, 0.84f),
        WelcomePoint(0.26f, 0.74f),
        WelcomePoint(0.22f, 0.62f),
        WelcomePoint(0.30f, 0.52f),
        WelcomePoint(0.42f, 0.54f),
        WelcomePoint(0.48f, 0.44f),
        WelcomePoint(0.44f, 0.32f),
        WelcomePoint(0.54f, 0.26f),
        WelcomePoint(0.66f, 0.30f),
        WelcomePoint(0.70f, 0.42f),
        WelcomePoint(0.80f, 0.46f),
        WelcomePoint(0.86f, 0.36f),
        WelcomePoint(0.92f, 0.24f),
    )

    val segmentCount: Int = trackPoints.size - 1

    /** Color rating of the given 0-based segment. Cycles through the 4-color palette. */
    fun ratingForSegment(segmentIndex: Int): SegmentRating {
        require(segmentIndex >= 0) { "segmentIndex must be >= 0" }
        return palette[segmentIndex % palette.size]
    }

    /** Segments visible at the given progress (0f -> 0, 1f -> segmentCount). */
    fun visibleSegmentCount(progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return (clamped * segmentCount).toInt()
    }

    /** Position of the moving "me" dot along the polyline at the given progress. */
    fun headPosition(progress: Float): WelcomePoint {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) return trackPoints.first()
        if (clamped >= 1f) return trackPoints.last()
        val exact = clamped * segmentCount
        val index = exact.toInt()
        val frac = exact - index
        val from = trackPoints[index]
        val to = trackPoints[index + 1]
        return WelcomePoint(
            x = from.x + (to.x - from.x) * frac,
            y = from.y + (to.y - from.y) * frac,
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.ui.model.WelcomeTrackGeometryTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/TrackAnimationMode.kt \
        app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometry.kt \
        app/src/test/java/ru/lobotino/walktraveller/ui/model/WelcomeTrackGeometryTest.kt
git commit -m "Add WelcomeTrackGeometry with rating-color track logic and tests"
```

---

## Task 2: Consent-gating tests for `FirstWelcomeViewModel`

The new UI depends on the existing consent gating (`onContinueButtonClick` only proceeds when the checkbox is checked). Lock that behavior with tests. No production change.

**Files:**
- Test: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/FirstWelcomeViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/ru/lobotino/walktraveller/viewmodels/FirstWelcomeViewModelTest.kt`:

```kotlin
package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState

class FirstWelcomeViewModelTest {

    private lateinit var userInfoRepository: IUserInfoRepository
    private lateinit var sut: FirstWelcomeViewModel
    private var continueCalled = false

    @Before
    fun setUp() {
        continueCalled = false
        userInfoRepository = mockk(relaxed = true)
        sut = FirstWelcomeViewModel(mockk<Application>(relaxed = true)).apply {
            setUserInfoRepository(userInfoRepository)
            onContinueListener = { continueCalled = true }
        }
    }

    @After
    fun tearDown() {
        continueCalled = false
    }

    @Test
    fun continueBlockedWhenPolicyNotChecked() = runTest {
        // when
        sut.onContinueButtonClick()

        // then
        assertFalse(continueCalled)
        verify(exactly = 0) { userInfoRepository.setWelcomeTutorialFinished(any()) }
        assertEquals(
            WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST,
            sut.observeContinueButtonStateChanges.first(),
        )
    }

    @Test
    fun continueProceedsWhenPolicyChecked() = runTest {
        // given
        sut.onPrivacyPolicyCheckedChanged(true)

        // when
        sut.onContinueButtonClick()

        // then
        assertTrue(continueCalled)
        verify(exactly = 1) { userInfoRepository.setWelcomeTutorialFinished(true) }
    }

    @Test
    fun buttonReturnsToDefaultAfterCheckingPolicy() = runTest {
        // given
        sut.onContinueButtonClick() // moves to NEED_TO_AGREEMENT_FIRST

        // when
        sut.onPrivacyPolicyCheckedChanged(true)

        // then
        assertEquals(
            WelcomeContinueButtonState.DEFAULT,
            sut.observeContinueButtonStateChanges.first(),
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.FirstWelcomeViewModelTest"`
Expected: PASS (3 tests). If a test fails, the production gating differs from the plan — STOP and reconcile before continuing (do not weaken the test to make it pass).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/ru/lobotino/walktraveller/viewmodels/FirstWelcomeViewModelTest.kt
git commit -m "Add consent-gating tests for FirstWelcomeViewModel"
```

---

## Task 3: `WelcomePage` model

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomePage.kt`

- [ ] **Step 1: Create the model**

Create `app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomePage.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.model

import androidx.annotation.StringRes

/** A single page of the first-launch onboarding pager. */
sealed class WelcomePage {

    /** Tutorial slide with an animated track and explanatory text. */
    data class Tutorial(
        val mode: TrackAnimationMode,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
    ) : WelcomePage()

    /** Final slide: privacy policy / terms consent (must be accepted to continue). */
    object Consent : WelcomePage()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/WelcomePage.kt
git commit -m "Add WelcomePage onboarding model"
```

---

## Task 4: `TrackRecordingAnimationView`

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/view/TrackRecordingAnimationView.kt`

- [ ] **Step 1: Create the view**

Create `app/src/main/java/ru/lobotino/walktraveller/ui/view/TrackRecordingAnimationView.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.ui.model.TrackAnimationMode
import ru.lobotino.walktraveller.ui.model.WelcomeTrackGeometry

/**
 * Draws a stylized walking track that grows over time, colored with the app's
 * rating palette. Three modes vary the extra hints drawn on top of the track.
 */
class TrackRecordingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var mode: TrackAnimationMode = TrackAnimationMode.RECORD
        set(value) {
            field = value
            invalidate()
        }

    private var progress: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val keyRect = RectF()

    private val whiteColor = ContextCompat.getColor(context, android.R.color.white)
    private val inactiveKeyColor = ContextCompat.getColor(context, R.color.rating_unknown)

    private val ratingColors: Map<SegmentRating, Int> = mapOf(
        SegmentRating.BADLY to ContextCompat.getColor(context, R.color.rating_badly),
        SegmentRating.NORMAL to ContextCompat.getColor(context, R.color.rating_normal),
        SegmentRating.GOOD to ContextCompat.getColor(context, R.color.rating_good),
        SegmentRating.PERFECT to ContextCompat.getColor(context, R.color.rating_perfect),
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3500L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun startAnimation() {
        if (!animator.isStarted) animator.start()
    }

    fun stopAnimation() {
        animator.cancel()
        progress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawTrack(canvas, w, h)
        drawHead(canvas, w, h)
        when (mode) {
            TrackAnimationMode.RATE -> drawLegend(canvas, w, h)
            TrackAnimationMode.VOLUME -> drawVolumeKeys(canvas, w, h)
            TrackAnimationMode.RECORD -> Unit
        }
    }

    private fun drawTrack(canvas: Canvas, w: Float, h: Float) {
        trackPaint.strokeWidth = h * 0.02f
        val visible = WelcomeTrackGeometry.visibleSegmentCount(progress)
        val points = WelcomeTrackGeometry.trackPoints
        for (i in 0 until visible) {
            val from = points[i]
            val to = points[i + 1]
            trackPaint.color = ratingColors.getValue(WelcomeTrackGeometry.ratingForSegment(i))
            canvas.drawLine(from.x * w, from.y * h, to.x * w, to.y * h, trackPaint)
        }
    }

    private fun drawHead(canvas: Canvas, w: Float, h: Float) {
        val head = WelcomeTrackGeometry.headPosition(progress)
        val cx = head.x * w
        val cy = head.y * h
        val r = h * 0.03f
        fillPaint.color = whiteColor
        canvas.drawCircle(cx, cy, r * 1.45f, fillPaint)
        fillPaint.color = ratingColors.getValue(SegmentRating.PERFECT)
        canvas.drawCircle(cx, cy, r, fillPaint)
    }

    private fun drawLegend(canvas: Canvas, w: Float, h: Float) {
        val order = listOf(
            SegmentRating.BADLY,
            SegmentRating.NORMAL,
            SegmentRating.GOOD,
            SegmentRating.PERFECT,
        )
        val r = h * 0.022f
        val cy = h * 0.95f
        var cx = w * 0.12f
        val step = w * 0.08f
        for (rating in order) {
            fillPaint.color = ratingColors.getValue(rating)
            canvas.drawCircle(cx, cy, r, fillPaint)
            cx += step
        }
    }

    private fun drawVolumeKeys(canvas: Canvas, w: Float, h: Float) {
        val keyW = w * 0.11f
        val keyH = h * 0.16f
        val left = w * 0.84f
        val gap = h * 0.04f
        val upTop = h * 0.26f
        val downTop = upTop + keyH + gap
        val radius = keyW * 0.25f
        val upActive = (progress * 4f).toInt() % 2 == 0

        keyRect.set(left, upTop, left + keyW, upTop + keyH)
        fillPaint.color = if (upActive) ratingColors.getValue(SegmentRating.PERFECT) else inactiveKeyColor
        canvas.drawRoundRect(keyRect, radius, radius, fillPaint)

        keyRect.set(left, downTop, left + keyW, downTop + keyH)
        fillPaint.color = if (!upActive) ratingColors.getValue(SegmentRating.BADLY) else inactiveKeyColor
        canvas.drawRoundRect(keyRect, radius, radius, fillPaint)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/view/TrackRecordingAnimationView.kt
git commit -m "Add TrackRecordingAnimationView canvas animation"
```

---

## Task 5: Strings (en + ru)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Add English strings**

In `app/src/main/res/values/strings.xml`, add these immediately after the line
`<string name="welcome_terms_of_use_text_part">terms of use</string>`:

```xml
    <string name="welcome_next_button">Next</string>
    <string name="welcome_skip_button">Skip</string>
    <string name="welcome_start_button">Start</string>
    <string name="welcome_slide_record_title">Record your walks</string>
    <string name="welcome_slide_record_subtitle">Your route is drawn automatically as you walk — no need to do anything by hand.</string>
    <string name="welcome_slide_rate_title">Rate the route as you go</string>
    <string name="welcome_slide_rate_subtitle">Mark each part of the path with a color: from bad (red) to perfect (green).</string>
    <string name="welcome_slide_volume_title">Without taking out your phone</string>
    <string name="welcome_slide_volume_subtitle">Set the rating with the volume buttons: up is better, down is worse.</string>
    <string name="settings_privacy_policy_button">Privacy policy</string>
    <string name="settings_terms_of_use_button">Terms of use</string>
```

- [ ] **Step 2: Add Russian strings**

In `app/src/main/res/values-ru/strings.xml`, add immediately after the line
`<string name="welcome_terms_of_use_text_part">условиями пользования</string>`:

```xml
    <string name="welcome_next_button">Далее</string>
    <string name="welcome_skip_button">Пропустить</string>
    <string name="welcome_start_button">Начать</string>
    <string name="welcome_slide_record_title">Записывайте свои прогулки</string>
    <string name="welcome_slide_record_subtitle">Маршрут рисуется сам, пока вы идёте — ничего не нужно делать вручную.</string>
    <string name="welcome_slide_rate_title">Оценивайте маршрут на ходу</string>
    <string name="welcome_slide_rate_subtitle">Отмечайте участки пути цветом: от плохого (красный) до отличного (зелёный).</string>
    <string name="welcome_slide_volume_title">Не доставая телефон</string>
    <string name="welcome_slide_volume_subtitle">Ставьте оценку кнопками громкости: выше — лучше, ниже — хуже.</string>
    <string name="settings_privacy_policy_button">Политика конфиденциальности</string>
    <string name="settings_terms_of_use_button">Условия пользования</string>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "Add onboarding and settings policy strings (en + ru)"
```

---

## Task 6: Page layouts + indicator dot drawable

**Files:**
- Create: `app/src/main/res/drawable/bg_welcome_dot.xml`
- Create: `app/src/main/res/layout/item_welcome_tutorial.xml`
- Create: `app/src/main/res/layout/item_welcome_consent.xml`

- [ ] **Step 1: Create the dot drawable**

Create `app/src/main/res/drawable/bg_welcome_dot.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/black" />
    <size
        android:width="8dp"
        android:height="8dp" />
</shape>
```

- [ ] **Step 2: Create the tutorial page layout**

Create `app/src/main/res/layout/item_welcome_tutorial.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center_horizontal">

    <ru.lobotino.walktraveller.ui.view.TrackRecordingAnimationView
        android:id="@+id/welcome_animation_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginStart="24dp"
        android:layout_marginTop="32dp"
        android:layout_marginEnd="24dp" />

    <TextView
        android:id="@+id/welcome_slide_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginEnd="24dp"
        android:layout_marginTop="16dp"
        android:gravity="center"
        android:textColor="@color/black"
        android:textSize="24sp"
        android:textStyle="bold"
        tools:text="Record your walks"
        xmlns:tools="http://schemas.android.com/tools" />

    <TextView
        android:id="@+id/welcome_slide_subtitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginEnd="24dp"
        android:layout_marginTop="12dp"
        android:layout_marginBottom="24dp"
        android:gravity="center"
        android:textColor="@color/black"
        android:textSize="16sp"
        tools:text="Your route is drawn automatically as you walk."
        xmlns:tools="http://schemas.android.com/tools" />

</LinearLayout>
```

- [ ] **Step 3: Create the consent page layout**

Create `app/src/main/res/layout/item_welcome_consent.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:layout_marginStart="24dp"
            android:layout_marginTop="48dp"
            android:layout_marginEnd="24dp"
            android:gravity="center"
            android:text="@string/welcome_title"
            android:textColor="@color/black"
            android:textSize="26sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="24dp"
            android:layout_marginTop="12dp"
            android:layout_marginEnd="24dp"
            android:gravity="center"
            android:text="@string/welcome_subtitle"
            android:textColor="@color/black"
            android:textSize="18sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="24dp"
            android:layout_marginTop="48dp"
            android:layout_marginEnd="24dp"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/privacy_policy_text"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_gravity="center_vertical"
                android:layout_weight="1"
                android:text="@string/welcome_privacy_policy_accepting"
                android:textColor="@color/black"
                android:textSize="15sp" />

            <CheckBox
                android:id="@+id/privacy_policy_check_box"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center_vertical" />

        </LinearLayout>

    </LinearLayout>

</ScrollView>
```

- [ ] **Step 4: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/bg_welcome_dot.xml \
        app/src/main/res/layout/item_welcome_tutorial.xml \
        app/src/main/res/layout/item_welcome_consent.xml
git commit -m "Add onboarding page layouts and indicator dot drawable"
```

---

## Task 7: `WelcomeSlidesAdapter`

**Files:**
- Create: `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/WelcomeSlidesAdapter.kt`

- [ ] **Step 1: Create the adapter**

Create `app/src/main/java/ru/lobotino/walktraveller/ui/adapter/WelcomeSlidesAdapter.kt`:

```kotlin
package ru.lobotino.walktraveller.ui.adapter

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.ui.dialog.PrivacyPolicyDialog
import ru.lobotino.walktraveller.ui.dialog.TermsOfUseDialog
import ru.lobotino.walktraveller.ui.model.WelcomePage
import ru.lobotino.walktraveller.ui.view.TrackRecordingAnimationView

/**
 * Pages for the first-launch onboarding pager: tutorial slides (animated track)
 * and a final consent slide. Animations start/stop with page attach/detach.
 */
class WelcomeSlidesAdapter(
    private val pages: List<WelcomePage>,
    private val onPrivacyCheckedChange: (Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_TUTORIAL = 0
        const val TYPE_CONSENT = 1
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = when (pages[position]) {
        is WelcomePage.Tutorial -> TYPE_TUTORIAL
        WelcomePage.Consent -> TYPE_CONSENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TUTORIAL) {
            TutorialViewHolder(inflater.inflate(R.layout.item_welcome_tutorial, parent, false))
        } else {
            ConsentViewHolder(
                inflater.inflate(R.layout.item_welcome_consent, parent, false),
                onPrivacyCheckedChange,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = pages[position]) {
            is WelcomePage.Tutorial -> (holder as TutorialViewHolder).bind(page)
            WelcomePage.Consent -> (holder as ConsentViewHolder).bind()
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is TutorialViewHolder) holder.animationView.startAnimation()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is TutorialViewHolder) holder.animationView.stopAnimation()
    }

    class TutorialViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val animationView: TrackRecordingAnimationView =
            view.findViewById(R.id.welcome_animation_view)
        private val title: TextView = view.findViewById(R.id.welcome_slide_title)
        private val subtitle: TextView = view.findViewById(R.id.welcome_slide_subtitle)

        fun bind(page: WelcomePage.Tutorial) {
            animationView.mode = page.mode
            title.setText(page.titleRes)
            subtitle.setText(page.subtitleRes)
        }
    }

    class ConsentViewHolder(
        view: View,
        private val onPrivacyCheckedChange: (Boolean) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.privacy_policy_check_box)
        private val policyText: TextView = view.findViewById(R.id.privacy_policy_text)

        fun bind() {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onPrivacyCheckedChange(isChecked)
            }
            bindPolicyText()
        }

        private fun bindPolicyText() {
            val context = policyText.context
            val full = context.getString(R.string.welcome_privacy_policy_accepting)
            val policyPart = context.getString(R.string.welcome_privacy_policy_text_part)
            val termsPart = context.getString(R.string.welcome_terms_of_use_text_part)
            val policyStart = full.indexOf(policyPart)
            val termsStart = full.indexOf(termsPart)

            policyText.text = SpannableString(full).apply {
                if (policyStart >= 0) {
                    setSpan(
                        clickableSpan {
                            PrivacyPolicyDialog(context).showFullScreen()
                        },
                        policyStart,
                        policyStart + policyPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                if (termsStart >= 0) {
                    setSpan(
                        clickableSpan {
                            TermsOfUseDialog(context).showFullScreen()
                        },
                        termsStart,
                        termsStart + termsPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            policyText.movementMethod = LinkMovementMethod.getInstance()
        }

        private fun clickableSpan(onClick: () -> Unit): ClickableSpan =
            object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }
    }
}
```

- [ ] **Step 2: Add the `showFullScreen()` helpers used above**

The original code calls `show()` then `window?.setLayout(MATCH_PARENT, MATCH_PARENT)`. To keep the adapter DRY, add an extension. Create it at the bottom of the adapter file (same file, after the class), OR if `PrivacyPolicyDialog`/`TermsOfUseDialog` already expose such a method, reuse it. First check:

Run: `grep -rn "fun showFullScreen\|setLayout" app/src/main/java/ru/lobotino/walktraveller/ui/dialog/`
Expected: no `showFullScreen` exists yet.

Add to the **end** of `WelcomeSlidesAdapter.kt` (top-level, outside the class):

```kotlin
import android.app.Dialog
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

private fun Dialog.showFullScreen() {
    show()
    window?.setLayout(MATCH_PARENT, MATCH_PARENT)
}
```

(Place the two `import` lines in the import block at the top of the file, not literally at the bottom — imports must be at file top. The function goes at the bottom.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/adapter/WelcomeSlidesAdapter.kt
git commit -m "Add WelcomeSlidesAdapter with tutorial and consent pages"
```

---

## Task 8: Rewrite `first_welcome_fragment.xml`

**Files:**
- Modify: `app/src/main/res/layout/first_welcome_fragment.xml` (full replacement)

- [ ] **Step 1: Replace the layout**

Replace the entire contents of `app/src/main/res/layout/first_welcome_fragment.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/first_welcome_background">

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/welcome_pager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingBottom="20dp"
        android:paddingTop="8dp">

        <Button
            android:id="@+id/welcome_skip_button"
            style="?android:attr/borderlessButtonStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/welcome_skip_button"
            android:textColor="@color/black"
            android:textAllCaps="false" />

        <LinearLayout
            android:id="@+id/welcome_dots_container"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:gravity="center"
            android:orientation="horizontal" />

        <Button
            android:id="@+id/welcome_primary_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minWidth="120dp"
            android:text="@string/welcome_next_button"
            android:textAllCaps="false" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/first_welcome_fragment.xml
git commit -m "Replace welcome layout with pager + bottom navigation bar"
```

---

## Task 9: Rewrite `FirstWelcomeFragment`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt` (full replacement)

- [ ] **Step 1: Replace the fragment**

Replace the entire contents of `app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt` with:

```kotlin
package ru.lobotino.walktraveller.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.AppScreen
import ru.lobotino.walktraveller.ui.adapter.WelcomeSlidesAdapter
import ru.lobotino.walktraveller.ui.model.TrackAnimationMode
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState
import ru.lobotino.walktraveller.ui.model.WelcomePage
import ru.lobotino.walktraveller.utils.ext.navigateTo
import ru.lobotino.walktraveller.viewmodels.FirstWelcomeViewModel

class FirstWelcomeFragment : Fragment() {

    private lateinit var pager: ViewPager2
    private lateinit var skipButton: Button
    private lateinit var primaryButton: Button
    private lateinit var dotsContainer: LinearLayout
    private val dots = mutableListOf<ImageView>()

    private var viewModel: FirstWelcomeViewModel? = null
    private var lastButtonState: WelcomeContinueButtonState = WelcomeContinueButtonState.DEFAULT

    private val pages: List<WelcomePage> = listOf(
        WelcomePage.Tutorial(
            TrackAnimationMode.RECORD,
            R.string.welcome_slide_record_title,
            R.string.welcome_slide_record_subtitle,
        ),
        WelcomePage.Tutorial(
            TrackAnimationMode.RATE,
            R.string.welcome_slide_rate_title,
            R.string.welcome_slide_rate_subtitle,
        ),
        WelcomePage.Tutorial(
            TrackAnimationMode.VOLUME,
            R.string.welcome_slide_volume_title,
            R.string.welcome_slide_volume_subtitle,
        ),
        WelcomePage.Consent,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.first_welcome_fragment, container, false).also { view ->
            initViews(view)
            initViewModel()
            updateForPage(pager.currentItem)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireContext().applicationContext as App).analyticsTracker
            .track(AnalyticsEvent.ScreenView("welcome"))
    }

    private fun initViews(view: View) {
        pager = view.findViewById(R.id.welcome_pager)
        skipButton = view.findViewById(R.id.welcome_skip_button)
        primaryButton = view.findViewById(R.id.welcome_primary_button)
        dotsContainer = view.findViewById(R.id.welcome_dots_container)

        pager.adapter = WelcomeSlidesAdapter(pages) { isChecked ->
            viewModel?.onPrivacyPolicyCheckedChanged(isChecked)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateForPage(position)
            }
        })

        buildDots()

        skipButton.setOnClickListener {
            pager.setCurrentItem(pages.lastIndex, true)
        }
        primaryButton.setOnClickListener {
            if (pager.currentItem < pages.lastIndex) {
                pager.currentItem = pager.currentItem + 1
            } else {
                viewModel?.onContinueButtonClick()
            }
        }
    }

    private fun buildDots() {
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        for (i in pages.indices) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                setImageResource(R.drawable.bg_welcome_dot)
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }
    }

    private fun initViewModel() {
        viewModel =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                .create(FirstWelcomeViewModel::class.java).apply {
                    setUserInfoRepository(
                        UserInfoRepository(
                            requireContext().getSharedPreferences(
                                App.SHARED_PREFS_TAG,
                                AppCompatActivity.MODE_PRIVATE,
                            ),
                        ),
                    )

                    observeContinueButtonStateChanges.onEach { state ->
                        lastButtonState = state
                        if (pager.currentItem == pages.lastIndex) {
                            applyConsentButtonState(state)
                        }
                    }.launchIn(lifecycleScope)

                    onContinueListener = {
                        navigateTo(AppScreen.MAP_SCREEN, arguments?.getParcelable(EXTRA_DATA_URI))
                    }
                }
    }

    private fun updateForPage(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.alpha = if (index == position) 1f else 0.3f
        }
        val isLast = position == pages.lastIndex
        skipButton.visibility = if (isLast) View.GONE else View.VISIBLE
        if (isLast) {
            applyConsentButtonState(lastButtonState)
        } else {
            primaryButton.isEnabled = true
            primaryButton.text = getString(R.string.welcome_next_button)
        }
    }

    private fun applyConsentButtonState(state: WelcomeContinueButtonState) {
        when (state) {
            WelcomeContinueButtonState.DEFAULT -> {
                primaryButton.isEnabled = true
                primaryButton.text = getString(R.string.welcome_start_button)
            }

            WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST -> {
                primaryButton.isEnabled = false
                primaryButton.text =
                    getString(R.string.welcome_continue_button_need_to_agreement_first)
            }
        }
    }

    companion object {
        private const val EXTRA_DATA_URI = "EXTRA_DATA_URI"

        fun newInstance(extraData: Uri?): FirstWelcomeFragment {
            return FirstWelcomeFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA_URI, extraData)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full unit test suite (no regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (includes Task 1 + Task 2 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/FirstWelcomeFragment.kt
git commit -m "Host onboarding pager in FirstWelcomeFragment"
```

---

## Task 10: Privacy policy / terms access in Settings

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt`

- [ ] **Step 1: Add the buttons to the settings layout**

In `app/src/main/res/layout/fragment_settings.xml`, insert this block immediately
**after** the closing `</LinearLayout>` of the FAQ buttons block (the one at line 167,
which contains `faq_check_geolocation_button`) and **before** the `</LinearLayout>`
at line 168 that closes the content container:

```xml
        <com.google.android.material.divider.MaterialDivider
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <Button
                android:id="@+id/privacy_policy_button"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="12dp"
                android:layout_marginTop="8dp"
                android:background="@drawable/confirm_button_background"
                android:text="@string/settings_privacy_policy_button"
                android:textAllCaps="false" />

            <Button
                android:id="@+id/terms_of_use_button"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="12dp"
                android:layout_marginTop="2dp"
                android:layout_marginBottom="8dp"
                android:background="@drawable/confirm_button_background"
                android:text="@string/settings_terms_of_use_button"
                android:textAllCaps="false" />

        </LinearLayout>
```

- [ ] **Step 2: Wire the buttons in `SettingsFragment`**

In `app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt`:

Add these imports to the import block:

```kotlin
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import ru.lobotino.walktraveller.ui.dialog.PrivacyPolicyDialog
import ru.lobotino.walktraveller.ui.dialog.TermsOfUseDialog
```

At the end of the `initViews(view: View)` method (immediately before its closing `}`,
after `mainView = view.findViewById(R.id.main_view)`), add:

```kotlin
        view.findViewById<Button>(R.id.privacy_policy_button).setOnClickListener {
            PrivacyPolicyDialog(requireContext()).apply {
                show()
                window?.setLayout(MATCH_PARENT, MATCH_PARENT)
            }
        }

        view.findViewById<Button>(R.id.terms_of_use_button).setOnClickListener {
            TermsOfUseDialog(requireContext()).apply {
                show()
                window?.setLayout(MATCH_PARENT, MATCH_PARENT)
            }
        }
```

(`Button` is already imported in `SettingsFragment.kt`.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml \
        app/src/main/java/ru/lobotino/walktraveller/ui/SettingsFragment.kt
git commit -m "Add privacy policy and terms access to Settings"
```

---

## Task 11: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all tests, including new geometry + view-model tests).

- [ ] **Step 3: Manual smoke test (emulator or device)**

Verify by hand (fresh install / cleared data so `isWelcomeTutorialFinished()` is false):
1. App launches into the onboarding pager (not straight to the map).
2. Slide 1 (RECORD): a colored track grows with a moving head dot; "Skip" + dots + "Next" visible.
3. Swipe to slide 2 (RATE): track grows + 4-color legend at the bottom.
4. Swipe to slide 3 (VOLUME): track grows + two volume keys alternate highlight.
5. "Skip" from any tutorial slide jumps directly to the consent slide.
6. Consent slide: "Skip" hidden; primary button reads "Start" and is **disabled**; tapping it shows the "agreement required" state.
7. Check the privacy checkbox → button becomes enabled "Start"; tapping the policy / terms links opens the respective full-screen dialogs.
8. Tap "Start" → navigates to the map; relaunching the app goes straight to the map (tutorial marked finished).
9. Settings → "Privacy policy" and "Terms of use" buttons each open their dialog.

- [ ] **Step 4: Final commit (only if any fixes were needed in Step 3)**

```bash
git add -A
git commit -m "Fix onboarding issues found during manual verification"
```

---

## Notes for the implementer

- **Do not** add Lottie, Compose, or any new Gradle dependency — everything needed is on the classpath.
- The rating colors are referenced by resource name (`rating_badly`, `rating_normal`, `rating_good`, `rating_perfect`); do not hardcode hex values.
- Keep `FirstWelcomeViewModel` consent logic unchanged — the new UI relies on its existing gating, which Task 2 locks down with tests.
- After the whole feature is implemented and verified, delete both `docs/superpowers/specs/2026-06-03-onboarding-track-recording-design.md` and `docs/superpowers/plans/2026-06-03-onboarding-track-recording.md` — per project convention, specs/plans are working artifacts and are not kept in git history.

# Fit camera to a saved/outer path on single tap — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Одиночный клик по элементу в `MyPathsMenuView` или `OuterPathsMenuView` плавно подгоняет камеру MapLibre под границы выбранного трека с отступом 10% от видимой области, если трек сейчас отображается на карте.

**Architecture:**
- Источник истины «показан ли трек» — `PathsMenuViewModel` (новое поле `shownPathIdsByMenu`).
- `PathsMenuViewModel.onPathInListShortTap` вне select-mode резолвит `MapRatingPath` через имеющиеся интеракторы, считает `PathBounds`, шлёт новый `MapEvent.FitCameraToBounds(bounds)`.
- `MapViewModel` — тонкий relay (Flow `observeFitCameraToBounds`), консистентный с уже существующим паттерном `observeNewMapCenter`/`observeHidePath`.
- `MainMapFragment` подписывается на Flow, считает padding (10% + высота видимого меню снизу) и зовёт `map.animateCamera(CameraUpdateFactory.newLatLngBounds(...))`.

**Tech Stack:** Kotlin, AndroidX ViewModel, Kotlin Coroutines/Flow, MapLibre GL Native, JUnit + mockk + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-02-fit-camera-to-path-on-tap-design.md`

**Тестовые соглашения проекта** (см. `feedback_unit_test_conventions`):
- SUT-поле называется `sut`.
- Зависимости создаются в `@Before` (`setUp`).
- Тело каждого теста разбито на `// given`, `// when`, `// then`.
- Корутины: `kotlinx.coroutines.test.runTest`.

---

## File Structure

**Создаются:**
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelFitCameraTest.kt` — фокусированные тесты relay-канала `fitCameraToBounds`.
- `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt` — тесты `onPathInListShortTap` (фит) + регрессия select-mode + поведение `shownPathIdsByMenu`.

**Модифицируются:**
- `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt` — добавить `FitCameraToBounds(bounds: PathBounds)`.
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt` — добавить Flow + метод `fitCameraToBounds`.
- `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt` — `shownPathIdsByMenu`, хелперы `markPathShown`/`markPathHidden`, вызовы в местах смены `showButtonState` и удаления путей, расширение `onPathInListShortTap`.
- `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt` — обработка `MapEvent.FitCameraToBounds`, подписка на `observeFitCameraToBounds`, новый метод `fitMapCameraToBounds`.

---

## Task 1: Добавить `MapEvent.FitCameraToBounds`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt`

Это структурное событие без поведения. TDD здесь — формальность; компиляция и последующие тесты во ViewModel-уровне служат фактическим покрытием.

- [ ] **Step 1: Открыть `MapEvent.kt` и добавить вариант**

В файле уже есть импорт `MapRatingPath` и `MapCommonPath`. Дополнительно нужен импорт `PathBounds`.

Заменить файл целиком на:

```kotlin
package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.ui.maplibre.PathBounds

sealed class MapEvent {
    object ClearMap : MapEvent()

    class ShowRatingPathList(val pathList: List<MapRatingPath>) : MapEvent()

    class ShowCommonPathList(val pathList: List<MapCommonPath>) : MapEvent()

    class ShowRatingPath(val path: MapRatingPath) : MapEvent()

    class ShowCommonPath(val path: MapCommonPath) : MapEvent()

    class HidePath(val pathsToHide: PathsToAction) : MapEvent()

    class BottomMenuStateChange(val newBottomMenuState: BottomMenuState) : MapEvent()

    class FitCameraToBounds(val bounds: PathBounds) : MapEvent()
}
```

- [ ] **Step 2: Скомпилировать модуль**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Уведомлений «when is not exhaustive» в `MainMapFragment.observeNewMapEvent` не появится, потому что `MainMapFragment` использует `when` без `else`, но Kotlin не считает его обязательно exhaustive вне `return`/`assignment`. Если компилятор всё-таки предупредит — это ожидаемо и будет починено в Task 4.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/model/MapEvent.kt
git commit -m "Add MapEvent.FitCameraToBounds carrying PathBounds payload"
```

---

## Task 2: `MapViewModel` — relay `fitCameraToBounds` + Flow

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`
- Create: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelFitCameraTest.kt`

`MapViewModel` имеет много зависимостей; в тестах используется mockk-relaxed для всего, что не задействовано. Шаблон конструктора берётся из существующего `MapViewModel(...)`. Никакой логики, кроме relay в Flow.

- [ ] **Step 1: Написать падающий тест**

Create `app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelFitCameraTest.kt`:

```kotlin
package ru.lobotino.walktraveller.viewmodels

import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IFinishPathWritingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.IResourceManager

class MapViewModelFitCameraTest {

    private lateinit var sut: MapViewModel

    @Before
    fun setUp() {
        sut = MapViewModel(
            notificationsPermissionsUseCase = mockk<IPermissionsUseCase>(relaxed = true),
            geoPermissionsUseCase = mockk<GeoPermissionsUseCase>(relaxed = true),
            finishPathWritingUseCase = mockk<IFinishPathWritingUseCase>(relaxed = true),
            userLocationInteractor = mockk<IUserLocationInteractor>(relaxed = true),
            mapPathsInteractor = mockk<IMapPathsInteractor>(relaxed = true),
            mapStateInteractor = mockk<IMapStateInteractor>(relaxed = true),
            tileSourceInteractor = mockk<ITileSourceInteractor>(relaxed = true),
            writingPathStatesRepository = mockk<IWritingPathStatesRepository>(relaxed = true),
            pathRatingUseCase = mockk<IPathRatingUseCase>(relaxed = true),
            userRotationRepository = mockk<IUserRotationRepository>(relaxed = true),
            userInfoRepository = mockk<IUserInfoRepository>(relaxed = true),
            resourceManager = mockk<IResourceManager>(relaxed = true),
            analyticsTracker = mockk<IAnalyticsTracker>(relaxed = true),
        )
    }

    @Test
    fun `fitCameraToBounds emits the same bounds to observeFitCameraToBounds`() = runTest {
        // given
        val bounds = PathBounds(minLat = 1.0, maxLat = 2.0, minLng = 3.0, maxLng = 4.0)

        // when
        sut.fitCameraToBounds(bounds)
        val emitted = sut.observeFitCameraToBounds.first()

        // then
        assertEquals(bounds, emitted)
    }
}
```

- [ ] **Step 2: Запустить тест — должен упасть на компиляции**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.MapViewModelFitCameraTest"`
Expected: FAIL (compilation error: `Unresolved reference: fitCameraToBounds` или `observeFitCameraToBounds`).

- [ ] **Step 3: Добавить Flow и метод в `MapViewModel`**

В `app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt`:

1. Добавить импорт после существующих импортов `ru.lobotino.walktraveller.model.map.*`:

```kotlin
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
```

2. Рядом с другими `MutableSharedFlow`-ами (после `newCurrentUserLocationFlow`, примерно строка 86) добавить:

```kotlin
    private val fitCameraToBoundsFlow =
        MutableSharedFlow<PathBounds>(1, 0, BufferOverflow.DROP_OLDEST)
```

3. Рядом с `observeNewCurrentUserLocation` (примерно строка 104) добавить:

```kotlin
    val observeFitCameraToBounds: Flow<PathBounds> = fitCameraToBoundsFlow
```

4. Рядом с `hidePathsFromMap` или `showRatingPathOnMap` (любое подходящее место в публичной API-секции) добавить:

```kotlin
    fun fitCameraToBounds(bounds: PathBounds) {
        fitCameraToBoundsFlow.tryEmit(bounds)
    }
```

- [ ] **Step 4: Запустить тест — должен пройти**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.MapViewModelFitCameraTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Запустить весь test suite, чтобы не сломать ничего другого**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/MapViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/MapViewModelFitCameraTest.kt
git commit -m "Add MapViewModel.fitCameraToBounds relay for camera-fit Flow"
```

---

## Task 3: `PathsMenuViewModel` — `shownPathIdsByMenu` + хелперы

Это подготовительный шаг: вводим состояние «какие треки показаны по типу меню» и обновляем его во всех местах смены `PathInfoItemShowButtonState`. Сама проверка в `onPathInListShortTap` появится в Task 4 — здесь только подложка и unit-тесты на корректность mark/unmark.

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Create: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt`

- [ ] **Step 1: Написать падающий тест файла `PathsMenuViewModelShortTapTest`**

Этот тест проверяет внешне наблюдаемый эффект: после показа пути одиночный тап в Task 4 будет эмитить событие. Сейчас же мы проверяем регрессию (что mark/unmark не ломает существующее поведение `onPathInListButtonClicked` для MY_PATHS).

Create `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt`:

```kotlin
package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
import ru.lobotino.walktraveller.ui.maplibre.pathBounds
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class PathsMenuViewModelShortTapTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pathsSaverRepository: IPathsSaverRepository
    private lateinit var externalStoragePermissionsUseCase: IPermissionsUseCase
    private lateinit var mapPathsInteractor: IMapPathsInteractor
    private lateinit var outerPathsInteractor: IOuterPathsInteractor
    private lateinit var pathRedactor: IPathRedactor
    private lateinit var analyticsTracker: IAnalyticsTracker
    private lateinit var sut: PathsMenuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pathsSaverRepository = mockk(relaxed = true)
        externalStoragePermissionsUseCase = mockk(relaxed = true)
        mapPathsInteractor = mockk(relaxed = true)
        outerPathsInteractor = mockk(relaxed = true)
        pathRedactor = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        sut = PathsMenuViewModel(
            pathsSaverRepository,
            externalStoragePermissionsUseCase,
            mapPathsInteractor,
            outerPathsInteractor,
            pathRedactor,
            analyticsTracker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPathInListShortTap hidden MY path outside select mode emits nothing`() = runTest(testDispatcher) {
        // given — путь не показывался, state по умолчанию

        // when
        sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then — канал событий пуст; используем tryReceive семантику через первый emit либо отсутствие
        val emittedOrNull = withTimeoutOrNullFirst(sut.observeNewMapEvent)
        assertNull(emittedOrNull)
    }

    // Хелпер: пытаемся забрать первый эмит с коротким таймаутом-эквивалентом
    private suspend fun <T> withTimeoutOrNullFirst(
        flow: kotlinx.coroutines.flow.Flow<T>,
    ): T? = try {
        kotlinx.coroutines.withTimeoutOrNull(50) { flow.first() }
    } catch (e: Throwable) {
        null
    }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )
}
```

Замечание: первая итерация теста проверяет только «no-op для скрытого пути», т.к. mark-логика ещё не добавлена. Полное покрытие (показанный путь → эмит) добавим в Task 4 после реализации `onPathInListShortTap`.

- [ ] **Step 2: Запустить тест — должен упасть на компиляции**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelShortTapTest"`
Expected: FAIL — либо компиляция, либо тест проходит сразу (если так — это нормально, тест не регрессионный, он просто фиксирует базовый no-op, который уже существует). Если тест проходит — пометить TODO в комментарии и идти дальше.

Если тест зелёный — это нормально, базовое поведение «no-op без select mode» сохраняется как сейчас. Цель этого Step — убедиться, что test-файл собирается и запускается.

- [ ] **Step 3: Добавить `shownPathIdsByMenu` и хелперы в `PathsMenuViewModel`**

В `PathsMenuViewModel.kt` после поля `selectedPathIdsInMenuList` (~строка 91) добавить:

```kotlin
    private val shownPathIdsByMenu: Map<PathsMenuType, MutableSet<Long>> = mapOf(
        PathsMenuType.MY_PATHS to mutableSetOf(),
        PathsMenuType.OUTER_PATHS to mutableSetOf(),
    )

    private fun markPathShown(type: PathsMenuType, ids: Collection<Long>) {
        shownPathIdsByMenu[type]?.addAll(ids)
    }

    private fun markPathHidden(type: PathsMenuType, ids: Collection<Long>) {
        shownPathIdsByMenu[type]?.removeAll(ids.toSet())
    }

    private fun isPathShown(type: PathsMenuType, pathId: Long): Boolean =
        shownPathIdsByMenu[type]?.contains(pathId) == true
```

- [ ] **Step 4: Прошить вызовы `markPathShown` / `markPathHidden` во все места смены `showButtonState`**

Ниже — точечные правки в `PathsMenuViewModel.kt`. Все находки получены из текущего файла; перед каждой правкой убедиться, что фрагмент кода соответствует представленному (если строки сдвинулись — искать по содержимому).

**4.1.** В методе `onShowSelectedPathsButtonClickedMyPathsMenu`, ветка `ShowPathsButtonState.LOADING` (после `tryEmit(...DEFAULT)`):

Заменить:
```kotlin
            ShowPathsButtonState.LOADING -> {
                loadPathsJob?.cancel()
                loadPathsJob = null
                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }
```
На:
```kotlin
            ShowPathsButtonState.LOADING -> {
                loadPathsJob?.cancel()
                loadPathsJob = null
                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                shownPathIdsByMenu[PathsMenuType.MY_PATHS]?.clear()
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }
```

**4.2.** В методе `hideSelectedPaths` (он используется и для MY_PATHS, и для OUTER_PATHS):

Заменить весь блок (примерно строки 233–263):

```kotlin
    private fun hideSelectedPaths(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
            PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
        }

        val selectedPathIds = selectedPathIdsInMenuList.toList()

        val pathsToHide = if (selectedPathIds.isEmpty()) {
            PathsToAction.All
        } else {
            PathsToAction.Multiple(selectedPathIds)
        }

        val hideMapEvent = if (pathsToHide == PathsToAction.All) {
            MapEvent.ClearMap
        } else {
            MapEvent.HidePath(pathsToHide)
        }

        newMapEventChannel.trySend(hideMapEvent)
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    pathsToHide,
                    PathInfoItemShowButtonState.DEFAULT
                )
            )
        )
    }
```

На:

```kotlin
    private fun hideSelectedPaths(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
            PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
        }

        val selectedPathIds = selectedPathIdsInMenuList.toList()

        val pathsToHide = if (selectedPathIds.isEmpty()) {
            PathsToAction.All
        } else {
            PathsToAction.Multiple(selectedPathIds)
        }

        when (pathsToHide) {
            PathsToAction.All -> shownPathIdsByMenu[pathsMenuType]?.clear()
            is PathsToAction.Multiple -> markPathHidden(pathsMenuType, pathsToHide.pathIds)
            is PathsToAction.Single -> markPathHidden(pathsMenuType, listOf(pathsToHide.pathId))
        }

        val hideMapEvent = if (pathsToHide == PathsToAction.All) {
            MapEvent.ClearMap
        } else {
            MapEvent.HidePath(pathsToHide)
        }

        newMapEventChannel.trySend(hideMapEvent)
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    pathsToHide,
                    PathInfoItemShowButtonState.DEFAULT
                )
            )
        )
    }
```

**4.3.** В `onShowSelectedPathsButtonClickedOuterPathsMenu`, ветка `ShowPathsButtonState.LOADING`:

Заменить:
```kotlin
            ShowPathsButtonState.LOADING -> {
                updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.OUTER_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }
```
На:
```kotlin
            ShowPathsButtonState.LOADING -> {
                updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                shownPathIdsByMenu[PathsMenuType.OUTER_PATHS]?.clear()
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.OUTER_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }
```

**4.4.** В `onShowSelectedPathsButtonClickedOuterPathsMenu`, ветка `ShowPathsButtonState.DEFAULT` — после `newMapEventChannel.trySend(MapEvent.ShowRatingPathList(outerPathsToShow))` и перед `newPathInfoListItemStateFlow.tryEmit(...)` добавить:

```kotlin
                markPathShown(PathsMenuType.OUTER_PATHS, outerPathsToShow.map { it.pathId })
```

**4.5.** В `loadAndShowSelectedRatedPaths` — в успешной ветке (`if (loadedPaths.isNotEmpty()) {`) после `newMapEventChannel.trySend(MapEvent.ShowRatingPathList(loadedPaths))` добавить:

```kotlin
                    markPathShown(PathsMenuType.MY_PATHS, loadedPaths.map { it.pathId })
```

**4.6.** В `loadAndShowSelectedPathsAsCommon` — в успешной ветке после `newMapEventChannel.trySend(MapEvent.ShowCommonPathList(loadedPaths))` добавить:

```kotlin
                    markPathShown(PathsMenuType.MY_PATHS, loadedPaths.map { it.pathId })
```

**4.7.** В `loadAndShowAllRatedPaths` — внутри `for (path in allPaths)` после `newMapEventChannel.trySend(MapEvent.ShowRatingPath(path))` добавить:

```kotlin
                markPathShown(PathsMenuType.MY_PATHS, listOf(path.pathId))
```

**4.8.** В `loadAndShowAllPathsAsCommon` — внутри `for (path in allPaths)` после `newMapEventChannel.trySend(MapEvent.ShowCommonPath(path))` добавить:

```kotlin
                markPathShown(PathsMenuType.MY_PATHS, listOf(path.pathId))
```

**4.9.** В `onPathInMyListButtonClicked`, ветка `LOADING, HIDE` (показ кнопки «глаз» спрятать или загружается → нажимаем → скрываем):

Заменить:
```kotlin
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(pathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.MY_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(pathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }
```
На:
```kotlin
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        markPathHidden(PathsMenuType.MY_PATHS, listOf(pathId))
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(pathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.MY_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(pathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }
```

**4.10.** В `onPathInMyListButtonClicked`, ветка `DEFAULT` — после `newMapEventChannel.trySend(MapEvent.ShowRatingPath(savedRatingPath))` и перед `newPathInfoListItemStateFlow.tryEmit(...HIDE...)`:

Заменить блок:
```kotlin
                            if (savedRatingPath != null) {
                                newMapEventChannel.trySend(MapEvent.ShowRatingPath(savedRatingPath))
                                newPathInfoListItemStateFlow.tryEmit(
                                    NewPathInfoItemState(
                                        PathsMenuType.MY_PATHS,
                                        PathInfoItemState(
                                            PathsToAction.Single(pathId),
                                            PathInfoItemShowButtonState.HIDE
                                        )
                                    )
                                )
                            } else {
                                // TODO handle bd error
                            }
```
На:
```kotlin
                            if (savedRatingPath != null) {
                                markPathShown(PathsMenuType.MY_PATHS, listOf(pathId))
                                newMapEventChannel.trySend(MapEvent.ShowRatingPath(savedRatingPath))
                                newPathInfoListItemStateFlow.tryEmit(
                                    NewPathInfoItemState(
                                        PathsMenuType.MY_PATHS,
                                        PathInfoItemState(
                                            PathsToAction.Single(pathId),
                                            PathInfoItemShowButtonState.HIDE
                                        )
                                    )
                                )
                            } else {
                                // TODO handle bd error
                            }
```

**4.11.** В `onPathInOuterListButtonClicked`, ветка `LOADING, HIDE`:

Заменить:
```kotlin
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(tempPathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.OUTER_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(tempPathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }
```
На:
```kotlin
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        markPathHidden(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(tempPathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.OUTER_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(tempPathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }
```

**4.12.** В `onPathInOuterListButtonClicked`, ветка `DEFAULT` — внутри `if (cachedPath != null)` после `newPathInfoListItemStateFlow.tryEmit(...HIDE...)` и перед `newMapEventChannel.trySend(MapEvent.ShowRatingPath(cachedPath))`:

Заменить блок:
```kotlin
                    PathInfoItemShowButtonState.DEFAULT -> {
                        val cachedPath = outerPathsInteractor.getCachedOuterPath(tempPathId)
                        if (cachedPath != null) {
                            newPathInfoListItemStateFlow.tryEmit(
                                NewPathInfoItemState(
                                    PathsMenuType.OUTER_PATHS,
                                    PathInfoItemState(
                                        PathsToAction.Single(tempPathId),
                                        PathInfoItemShowButtonState.HIDE
                                    )
                                )
                            )
                            newMapEventChannel.trySend(MapEvent.ShowRatingPath(cachedPath))
                        } else {
                            deleteOuterPathFromList(tempPathId)
                        }
                    }
```
На:
```kotlin
                    PathInfoItemShowButtonState.DEFAULT -> {
                        val cachedPath = outerPathsInteractor.getCachedOuterPath(tempPathId)
                        if (cachedPath != null) {
                            markPathShown(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
                            newPathInfoListItemStateFlow.tryEmit(
                                NewPathInfoItemState(
                                    PathsMenuType.OUTER_PATHS,
                                    PathInfoItemState(
                                        PathsToAction.Single(tempPathId),
                                        PathInfoItemShowButtonState.HIDE
                                    )
                                )
                            )
                            newMapEventChannel.trySend(MapEvent.ShowRatingPath(cachedPath))
                        } else {
                            deleteOuterPathFromList(tempPathId)
                        }
                    }
```

**4.13.** В `onConfirmMyPathDelete(pathId)` — в начале метода (до `MainScope().launch`):

```kotlin
        markPathHidden(PathsMenuType.MY_PATHS, listOf(pathId))
```

**4.14.** В `onConfirmMyPathListDelete(pathIds)` — в начале метода:

```kotlin
        markPathHidden(PathsMenuType.MY_PATHS, pathIds)
```

**4.15.** В `deleteOuterPathFromList(tempPathId)` — в начале метода (до `outerPathsInteractor.removeCachedPath(...)`):

```kotlin
        markPathHidden(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
```

**4.16.** В `deleteSelectedOuterPathsFromList()` — в начале метода (до цикла):

```kotlin
        markPathHidden(PathsMenuType.OUTER_PATHS, selectedPathIdsInMenuList.toList())
```

- [ ] **Step 5: Скомпилировать и запустить весь test suite (регрессии)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Существующие тесты должны быть зелёными — мы лишь добавили учёт состояния, не меняя поведение.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt
git commit -m "Track shown paths per menu type in PathsMenuViewModel"
```

---

## Task 4: `PathsMenuViewModel.onPathInListShortTap` — отправка `FitCameraToBounds`

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt`
- Modify: `app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt`

- [ ] **Step 1: Расширить тест-файл новыми падающими тестами**

Заменить содержимое `PathsMenuViewModelShortTapTest.kt` на расширенную версию. Это полная переустановка SUT-конфигурации — все существующие тесты сохраняются плюс новые:

```kotlin
package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
import ru.lobotino.walktraveller.ui.maplibre.pathBounds
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class PathsMenuViewModelShortTapTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pathsSaverRepository: IPathsSaverRepository
    private lateinit var externalStoragePermissionsUseCase: IPermissionsUseCase
    private lateinit var mapPathsInteractor: IMapPathsInteractor
    private lateinit var outerPathsInteractor: IOuterPathsInteractor
    private lateinit var pathRedactor: IPathRedactor
    private lateinit var analyticsTracker: IAnalyticsTracker
    private lateinit var sut: PathsMenuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pathsSaverRepository = mockk(relaxed = true)
        externalStoragePermissionsUseCase = mockk(relaxed = true)
        mapPathsInteractor = mockk(relaxed = true)
        outerPathsInteractor = mockk(relaxed = true)
        pathRedactor = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        sut = PathsMenuViewModel(
            pathsSaverRepository,
            externalStoragePermissionsUseCase,
            mapPathsInteractor,
            outerPathsInteractor,
            pathRedactor,
            analyticsTracker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPathInListShortTap hidden MY path outside select mode emits nothing`() = runTest(testDispatcher) {
        // given — путь не показывался

        // when
        sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emitted)
    }

    @Test
    fun `onPathInListShortTap hidden OUTER path outside select mode emits nothing`() = runTest(testDispatcher) {
        // given — путь не показывался

        // when
        sut.onPathInListShortTap(pathId = 99L, pathsMenuType = PathsMenuType.OUTER_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emitted)
    }

    @Test
    fun `onPathInListShortTap on shown MY path emits FitCameraToBounds with computed bounds`() = runTest(testDispatcher) {
        // given
        val pathId = 7L
        val path = ratingPath(pathId)
        val expectedBounds = pathBounds(path)!!
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path
        // путь сначала показан через стандартную кнопку — это активирует markPathShown
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
        // отбросить эмит ShowRatingPath, который пришёл от показа
        val shown = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertTrue(shown is MapEvent.ShowRatingPath)

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertTrue(emitted is MapEvent.FitCameraToBounds)
        assertEquals(expectedBounds, (emitted as MapEvent.FitCameraToBounds).bounds)
    }

    @Test
    fun `onPathInListShortTap on shown OUTER path emits FitCameraToBounds`() = runTest(testDispatcher) {
        // given
        val pathId = 11L
        val path = ratingPath(pathId)
        val expectedBounds = pathBounds(path)!!
        coEvery { outerPathsInteractor.getCachedOuterPath(pathId) } returns path
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.OUTER_PATHS,
        )
        advanceUntilIdle()
        val shown = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertTrue(shown is MapEvent.ShowRatingPath)

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.OUTER_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertTrue(emitted is MapEvent.FitCameraToBounds)
        assertEquals(expectedBounds, (emitted as MapEvent.FitCameraToBounds).bounds)
    }

    @Test
    fun `onPathInListShortTap in MY select mode keeps existing toggle behavior`() = runTest(testDispatcher) {
        // given — long-tap включает select mode, выделяет элемент 5L
        sut.onPathInListLongTap(5L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // when — short tap другого элемента в select mode → должен ТОЛЬКО переключить выделение, без FitCamera
        sut.onPathInListShortTap(6L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then — событие FitCamera не отправлено
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emitted)
    }

    @Test
    fun `onPathInListShortTap on MY path that became hidden again emits nothing`() = runTest(testDispatcher) {
        // given — показываем, потом скрываем
        val pathId = 8L
        val path = ratingPath(pathId)
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
        withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.HIDE),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
        withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emitted)
    }

    @Test
    fun `onPathInListShortTap on shown MY path with empty segments emits nothing`() = runTest(testDispatcher) {
        // given — путь помечен «показан», но interactor возвращает пустой path → bounds == null
        val pathId = 12L
        val emptyPath = MapRatingPath(pathId = pathId, pathSegments = emptyList())
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returnsMany listOf(
            ratingPath(pathId), // при первом показе вернётся валидный path
            emptyPath,           // при resolveShownPath — пустой
        )
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
        withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then
        val emitted = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emitted)
    }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )
}
```

- [ ] **Step 2: Запустить тесты — должны упасть**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelShortTapTest"`
Expected: тесты «emits FitCameraToBounds» падают (no emit), потому что `onPathInListShortTap` всё ещё no-op вне select-mode. Тест регрессии select-mode и hidden-path должны проходить.

- [ ] **Step 3: Реализовать новое поведение `onPathInListShortTap`**

В `PathsMenuViewModel.kt` заменить тело метода `onPathInListShortTap`:

Было:
```kotlin
    fun onPathInListShortTap(
        pathId: Long,
        pathsMenuType: PathsMenuType
    ) {
        if ((pathsMenuType == PathsMenuType.MY_PATHS && !myPathsMenuUiStateFlow.value.inSelectMode) ||
            (pathsMenuType == PathsMenuType.OUTER_PATHS && !outerPathsMenuUiStateFlow.value.inSelectMode)
        ) {
            return // ignore short tap without select mode
        }

        toggleMenuItemSelect(pathId, pathsMenuType)
    }
```

Стало:
```kotlin
    fun onPathInListShortTap(
        pathId: Long,
        pathsMenuType: PathsMenuType
    ) {
        val inSelectMode = when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> myPathsMenuUiStateFlow.value.inSelectMode
            PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
        }
        if (inSelectMode) {
            toggleMenuItemSelect(pathId, pathsMenuType)
            return
        }
        if (!isPathShown(pathsMenuType, pathId)) {
            return
        }
        viewModelScope.launch {
            val path = resolveShownPath(pathId, pathsMenuType) ?: return@launch
            val bounds = pathBounds(path) ?: return@launch
            newMapEventChannel.trySend(MapEvent.FitCameraToBounds(bounds))
        }
    }

    private suspend fun resolveShownPath(
        pathId: Long,
        pathsMenuType: PathsMenuType,
    ): MapRatingPath? = when (pathsMenuType) {
        PathsMenuType.OUTER_PATHS -> outerPathsInteractor.getCachedOuterPath(pathId)
        PathsMenuType.MY_PATHS -> mapPathsInteractor.getSavedRatingPath(
            pathId,
            withRatingOnly = false,
            isOptimized = true,
        )
    }
```

В импорты файла добавить:
```kotlin
import ru.lobotino.walktraveller.ui.maplibre.pathBounds
```

- [ ] **Step 4: Запустить тесты — должны пройти**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.lobotino.walktraveller.viewmodels.PathsMenuViewModelShortTapTest"`
Expected: PASS (все тесты класса).

- [ ] **Step 5: Полный test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModel.kt \
        app/src/test/java/ru/lobotino/walktraveller/viewmodels/PathsMenuViewModelShortTapTest.kt
git commit -m "Emit FitCameraToBounds on single tap of a shown path"
```

---

## Task 5: `MainMapFragment` — обработка события и анимация камеры

**Files:**
- Modify: `app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt`

Unit-тестов на этом шаге нет (зависимость от живого `mapLibreMap` и `mapView`). Проверка — компиляция и ручное smoke-тестирование.

- [ ] **Step 1: Добавить ветку для `MapEvent.FitCameraToBounds` в `observeNewMapEvent`**

В `MainMapFragment.kt` найти блок `observeNewMapEvent.onEach { mapEvent -> when (mapEvent) { ... } }` (около строки 511). Добавить новую ветку перед `is MapEvent.BottomMenuStateChange`:

```kotlin
                        is MapEvent.FitCameraToBounds -> {
                            mapViewModel.fitCameraToBounds(mapEvent.bounds)
                        }
```

- [ ] **Step 2: Добавить подписку на `observeFitCameraToBounds`**

В блоке `mapViewModel.apply { ... }` (около строки 680, рядом с `observeNewMapCenter.onEach { ... }`) добавить:

```kotlin
                    observeFitCameraToBounds.onEach { bounds ->
                        fitMapCameraToBounds(bounds)
                    }.launchIn(viewLifecycleOwner.lifecycleScope)
```

- [ ] **Step 3: Добавить приватный метод `fitMapCameraToBounds`**

В `MainMapFragment.kt` добавить новый приватный метод рядом с другими камера-методами (например, после `updateMapUiState` или рядом с `observeNewMapCenter`-подпиской). Чтобы избежать неоднозначности, разместить в конце файла перед закрывающей фигурной скобкой класса, рядом с другими `private fun`-хелперами:

```kotlin
    private fun fitMapCameraToBounds(bounds: PathBounds) {
        val map = mapLibreMap ?: return
        val mapWidth = mapView.width
        val mapHeight = mapView.height
        if (mapWidth == 0 || mapHeight == 0) return

        val horizontalPad = (mapWidth * 0.10f).toInt()
        val verticalPad = (mapHeight * 0.10f).toInt()
        val menuHeight = when {
            myPathsMenu.visibility == VISIBLE -> myPathsMenu.height
            outerPathsMenu.visibility == VISIBLE -> outerPathsMenu.height
            else -> 0
        }
        val bottomPad = verticalPad + menuHeight

        val latLngBounds = LatLngBounds.Builder()
            .include(LatLng(bounds.minLat, bounds.minLng))
            .include(LatLng(bounds.maxLat, bounds.maxLng))
            .build()

        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                latLngBounds,
                horizontalPad,
                verticalPad,
                horizontalPad,
                bottomPad,
            )
        )
    }
```

- [ ] **Step 4: Добавить нужные импорты**

В `MainMapFragment.kt` среди существующих MapLibre-импортов уже есть `CameraUpdateFactory` (используется для `observeNewMapCenter`) и `LatLng` (через `toLatLng()`). Может потребоваться добавить:

```kotlin
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
```

Если какие-то из этих импортов уже есть — пропустить дубль.

- [ ] **Step 5: Компилируем**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Полный test suite — убедиться, что ничего не сломали**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Ручная smoke-проверка**

Собрать debug APK и проверить вживую (на эмуляторе или девайсе):

Run: `./gradlew :app:assembleDebug`
Установить APK, открыть приложение, выполнить:
1. Открыть список сохранённых треков (My Paths). Включить отображение одного трека.
2. Тапнуть по нему одиночным кликом → камера плавно подгоняется под границы трека, меню остаётся открытым.
3. Тапнуть по выключенному (скрытому) треку → ничего не происходит.
4. Long-tap по треку → select mode. Short-tap другого трека → выделение, фит не выполняется.
5. Повторить шаги 1–4 для меню Outer Paths (если есть импортированный shared-файл).

Ожидаемое поведение из спеки: трек целиком вписан в видимую область над меню с отступом ~10% по горизонтали и сверху.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/lobotino/walktraveller/ui/MainMapFragment.kt
git commit -m "Animate camera to fit selected path on single tap"
```

---

## Task 6: Удалить рабочие артефакты спеки/плана (по соглашению проекта)

Согласно `feedback_no_specs_in_history`: design-спеки удаляются после реализации; это рабочие артефакты, не документация.

**Files:**
- Delete: `docs/superpowers/specs/2026-06-02-fit-camera-to-path-on-tap-design.md`
- Delete: `docs/superpowers/plans/2026-06-02-fit-camera-to-path-on-tap.md`

- [ ] **Step 1: Удалить файлы**

```bash
git rm docs/superpowers/specs/2026-06-02-fit-camera-to-path-on-tap-design.md \
       docs/superpowers/plans/2026-06-02-fit-camera-to-path-on-tap.md
```

- [ ] **Step 2: Commit**

```bash
git commit -m "Remove fit-camera-on-tap spec and plan after implementation"
```

---

## Self-Review (executed by plan author)

**Spec coverage:**
- §«Область применения» (только показанные треки, регрессия select-mode, одинаково MY/OUTER) → Task 3 mark/unmark + Task 4 проверка `isPathShown` + select-mode регрессия в тестах.
- §«Поведение» (шаги 1–6 цепочки) → Task 1 (event), Task 2 (relay), Task 4 (resolve+emit), Task 5 (фрагмент+padding).
- §«Компоненты → MapEvent» → Task 1.
- §«Компоненты → PathsMenuViewModel» (state + helpers + onPathInListShortTap) → Task 3 (state+helpers+wiring) + Task 4 (метод).
- §«Компоненты → MapViewModel» (Flow + relay-метод) → Task 2.
- §«Компоненты → MainMapFragment» (ветка события + подписка + `fitMapCameraToBounds`) → Task 5.
- §«Edge-cases» — каждый кейс закрыт: hidden→no-op (Task 4 тест), select-mode→toggle (Task 4 тест), empty path→null (Task 4 тест), mapView не уложен (Task 5 фрагмент-код), path не найден→null (Task 4 тест), повторный тап / двойной (поведение MapLibre — no test).
- §«Тесты → MapViewModelTest» → Task 2.
- §«Тесты → PathsMenuViewModelTest» → Task 3+4.

**Placeholder scan:** Нет TBD/TODO/«implement later». Все шаги содержат фактический код, команды и ожидаемые результаты.

**Type consistency:**
- `MapEvent.FitCameraToBounds(bounds: PathBounds)` — одинаково во всех тасках.
- `MapViewModel.fitCameraToBounds(bounds: PathBounds): Unit` и `observeFitCameraToBounds: Flow<PathBounds>` — одинаково Task 2 и Task 5.
- `PathsMenuViewModel.shownPathIdsByMenu`, `markPathShown`, `markPathHidden`, `isPathShown`, `resolveShownPath` — одинаково Task 3 и Task 4.
- `pathBounds()` (функция-расширение из `ui/maplibre/PathBounds.kt`) — используется без переименования.
- `MainMapFragment.fitMapCameraToBounds(bounds: PathBounds)` — одинаково.

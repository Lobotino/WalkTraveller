# Fit camera to a saved/outer path on single tap

## Цель

Одиночный клик по элементу в списке сохранённых треков (`MyPathsMenuView`) или
предложенных к сохранению (`OuterPathsMenuView`) должен плавно подгонять камеру
карты под границы выбранного трека с небольшим отступом. Сейчас одиночный клик
вне select-mode — no-op (`PathsMenuViewModel.onPathInListShortTap`,
`PathsMenuViewModel.kt:626`).

## Область применения

- Применяется только к трекам, которые **сейчас отображаются на карте**
  (иконка «глаз» в состоянии HIDE). Скрытые треки кликом не подтягиваются —
  одиночный клик игнорируется.
- В **select-mode** одиночный клик сохраняет существующее поведение
  (`toggleMenuItemSelect`). Фит камеры в select-mode не выполняется.
- Применяется одинаково для `PathsMenuType.MY_PATHS` и
  `PathsMenuType.OUTER_PATHS`.

## Поведение

1. Пользователь нажимает на карточку трека (не на кнопку внутри карточки).
2. `PathsInfoAdapter` зовёт `itemShortTapListener(pathId)` →
   `PathsMenuViewModel.onPathInListShortTap(pathId, type)`.
3. ViewModel решает:
   - в select-mode → старая логика выделения;
   - иначе и трек скрыт → no-op;
   - иначе → отправляет `MapEvent.FitCameraToPath(pathId)` в
     `newMapEventChannel`.
4. `MainMapFragment.observeNewMapEvent` пересылает в
   `MapViewModel.fitCameraToPath(pathId)`.
5. `MapViewModel` подгружает `MapRatingPath`, считает `PathBounds` и эмиттит в
   новый `observeFitCameraToBounds`.
6. `MainMapFragment` подписан на флоу: считает padding (10% от размеров
   `mapView` + высота видимого меню снизу) и вызывает
   `map.animateCamera(CameraUpdateFactory.newLatLngBounds(...))` со
   стандартной анимацией MapLibre.

## Компоненты

### `MapEvent` (`ui/model/MapEvent.kt`)

Добавить новый sealed-вариант:

```kotlin
class FitCameraToPath(val pathId: Long) : MapEvent()
```

### `PathsMenuViewModel`

Новое приватное состояние: какие треки сейчас показаны (по типу меню):

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
```

Правило обновления: рядом с каждым `tryEmit` в `newPathInfoListItemStateFlow`,
который выставляет `showButtonState`, вызвать соответствующий хелпер с тем же
набором `pathId`:

- `HIDE` → `markPathShown` (трек становится виден на карте).
- `DEFAULT` → `markPathHidden` (трек уходит с карты).
- `LOADING` — состояние промежуточное, не меняем.

Это включает оба меню (`MY_PATHS`, `OUTER_PATHS`) и все варианты целей:
`PathsToAction.Single`, `.Multiple`, `.All`. Точки правки в текущем
`PathsMenuViewModel.kt` (на момент написания спеки): диапазоны строк ~185–420
и ~510–610 — все эмиссии `PathInfoItemShowButtonState`. Также пути удаления
(`onConfirmMyPathDelete`, `onConfirmMyPathListDelete`,
`deleteOuterPathFromList`) — вызвать `markPathHidden` для удаляемых id, т.к.
`MapEvent.HidePath` уходит на карту, а строки больше нет, и состояние
«показан» нужно сбросить.

Расширить `onPathInListShortTap`:

```kotlin
fun onPathInListShortTap(pathId: Long, pathsMenuType: PathsMenuType) {
    val inSelectMode = when (pathsMenuType) {
        PathsMenuType.MY_PATHS -> myPathsMenuUiStateFlow.value.inSelectMode
        PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
    }
    if (inSelectMode) {
        toggleMenuItemSelect(pathId, pathsMenuType)
        return
    }
    if (pathId !in (shownPathIdsByMenu[pathsMenuType] ?: emptySet())) {
        return
    }
    newMapEventChannel.trySend(MapEvent.FitCameraToPath(pathId))
}
```

### `MapViewModel`

Добавить:

```kotlin
private val fitCameraToBoundsFlow =
    MutableSharedFlow<PathBounds>(1, 0, BufferOverflow.DROP_OLDEST)

val observeFitCameraToBounds: Flow<PathBounds> = fitCameraToBoundsFlow

fun fitCameraToPath(pathId: Long) {
    if (pathId !in showedPathIdsSet) return
    viewModelScope.launch {
        val path = loadRatingPathForFit(pathId) ?: return@launch
        val bounds = pathBounds(path) ?: return@launch
        fitCameraToBoundsFlow.tryEmit(bounds)
    }
}

private suspend fun loadRatingPathForFit(pathId: Long): MapRatingPath? {
    outerPathsInteractor.getCachedOuterPath(pathId)?.let { return it }
    return mapPathsInteractor.getSavedRatingPath(
        pathId,
        withRatingOnly = false,
        isOptimized = true,
    )
}
```

Источник пути и параметры (`withRatingOnly = false`, `isOptimized = true`)
совпадают с уже работающей загрузкой для отображения — мы используем тот же
кэшированный путь, который сейчас на карте.

### `MainMapFragment`

В `observeNewMapEvent`:

```kotlin
is MapEvent.FitCameraToPath -> mapViewModel.fitCameraToPath(mapEvent.pathId)
```

Новая подписка после `observeNewMapCenter`:

```kotlin
observeFitCameraToBounds.onEach { bounds ->
    fitMapCameraToBounds(bounds)
}.launchIn(viewLifecycleOwner.lifecycleScope)
```

Хелпер во фрагменте:

```kotlin
private fun fitMapCameraToBounds(bounds: PathBounds) {
    val map = mapLibreMap ?: return
    val mapWidth = mapView.width
    val mapHeight = mapView.height
    if (mapWidth == 0 || mapHeight == 0) return

    val horizontalPad = (mapWidth * 0.10f).toInt()
    val verticalPad = (mapHeight * 0.10f).toInt()
    val menuHeight = when {
        myPathsMenu.visibility == View.VISIBLE -> myPathsMenu.height
        outerPathsMenu.visibility == View.VISIBLE -> outerPathsMenu.height
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

## Edge-cases

| Случай | Поведение |
|--------|-----------|
| Трек скрыт (иконка «глаз» в DEFAULT) | no-op в `PathsMenuViewModel` |
| Select-mode активен | `toggleMenuItemSelect`, фит не запрашивается |
| Пустой трек (нет сегментов) | `pathBounds()` → null, эмиссии нет |
| Одна точка трека | `LatLngBounds` валиден, MapLibre сам подберёт zoom |
| `mapView` не уложен (width/height == 0) | no-op, пользователь повторит тап |
| Path не найден в interactor | no-op |
| Двойной быстрый тап / тап в момент анимации | MapLibre обрывает предыдущую анимацию |

## Тесты

Соглашения проекта (см. `feedback_unit_test_conventions`): SUT назвать `sut`,
`@Before/@After`, тело каждого теста разбить на `given/when/then`.

### `PathsMenuViewModelTest` (новый или дополнение существующего)

- `onPathInListShortTap`: не в select-mode, трек показан → отправлено
  `MapEvent.FitCameraToPath(pathId)`.
- `onPathInListShortTap`: не в select-mode, трек скрыт → событие НЕ
  отправлено.
- `onPathInListShortTap`: в select-mode → событие НЕ отправлено, элемент
  переключён в выделение (существующее поведение, регрессия).
- Покрытие обоих `PathsMenuType`.
- Корректность обновления `shownPathIdsByMenu` при кнопке show/hide и при
  удалении пути.

### `MapViewModelTest`

- `fitCameraToPath`: `pathId !in showedPathIdsSet` → `observeFitCameraToBounds`
  не эмиттит.
- `fitCameraToPath` для показанного outer-пути → эмиттит ожидаемый
  `PathBounds` (фейк `IOuterPathsInteractor` отдаёт заранее заданный
  `MapRatingPath`).
- `fitCameraToPath` для показанного saved-пути → эмиттит ожидаемый
  `PathBounds` (фейк `IMapPathsInteractor.getSavedRatingPath`).
- Пустой/null путь → эмиссии нет.

### Что не тестируется

- `MainMapFragment.fitMapCameraToBounds` — зависит от инициализированного
  `mapLibreMap` и `mapView`; в проекте принято UI-зависимый код не покрывать
  unit-тестами.
- Сам MapLibre `newLatLngBounds` / `animateCamera` — внешняя зависимость.

## Что НЕ меняем

- `MapLibrePathController`, его кэши и инвалидацию.
- `PathsInfoAdapter`, `MyPathsMenuView`, `OuterPathsMenuView` — короткий тап
  уже прокинут.
- Существующее поведение select-mode и кнопки show/hide.

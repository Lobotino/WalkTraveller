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
   - иначе → запускает корутину, резолвит `MapRatingPath` (синхронно для
     outer через `outerPathsInteractor.getCachedOuterPath`, асинхронно для
     saved через `mapPathsInteractor.getSavedRatingPath`), считает
     `pathBounds(path)` и отправляет `MapEvent.FitCameraToBounds(bounds)` в
     `newMapEventChannel`.
4. `MainMapFragment.observeNewMapEvent` пересылает в
   `MapViewModel.fitCameraToBounds(bounds)`.
5. `MapViewModel.fitCameraToBounds` эмиттит `bounds` в новый
   `observeFitCameraToBounds`.
6. `MainMapFragment` подписан на флоу: считает padding (10% от размеров
   `mapView` + высота видимого меню снизу) и вызывает
   `map.animateCamera(CameraUpdateFactory.newLatLngBounds(...))` со
   стандартной анимацией MapLibre.

## Компоненты

### `MapEvent` (`ui/model/MapEvent.kt`)

Добавить новый sealed-вариант:

```kotlin
class FitCameraToBounds(val bounds: PathBounds) : MapEvent()
```

Решение «передаём готовые `PathBounds`, а не `pathId`» обосновано тем, что
`MapViewModel` сейчас не имеет в зависимостях `IOuterPathsInteractor` и не
умеет резолвить outer-путь по id. `PathsMenuViewModel` уже владеет обоими
интеракторами (`IMapPathsInteractor`, `IOuterPathsInteractor`), поэтому
вычисление `PathBounds` естественно отдать ему.

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

### `MapViewModel`

Добавить тонкий relay-канал, чтобы фрагмент управлял камерой через тот же
паттерн «команда → Flow», что и `observeNewMapCenter`/`observeHidePath`:

```kotlin
private val fitCameraToBoundsFlow =
    MutableSharedFlow<PathBounds>(1, 0, BufferOverflow.DROP_OLDEST)

val observeFitCameraToBounds: Flow<PathBounds> = fitCameraToBoundsFlow

fun fitCameraToBounds(bounds: PathBounds) {
    fitCameraToBoundsFlow.tryEmit(bounds)
}
```

Никакой бизнес-логики и резолва пути в `MapViewModel` нет — это сознательный
выбор, см. комментарий в секции `MapEvent`. Источник истины «показан ли трек»
уже на стороне `PathsMenuViewModel` (`shownPathIdsByMenu`).

### `MainMapFragment`

В `observeNewMapEvent`:

```kotlin
is MapEvent.FitCameraToBounds -> mapViewModel.fitCameraToBounds(mapEvent.bounds)
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

- `fitCameraToBounds(bounds)` → `observeFitCameraToBounds` эмиттит ровно эти
  `bounds`. Регрессия тонкого relay не нужна сверх этого.

### `PathsMenuViewModelTest` — дополнительно

- `onPathInListShortTap` для MY-пути в показанном состоянии → во флоу
  `observeNewMapEvent` приходит `MapEvent.FitCameraToBounds(bounds)`, где
  `bounds = pathBounds(returnedRatingPath)`. Фейк
  `IMapPathsInteractor.getSavedRatingPath(pathId, false, true)` возвращает
  тестовый `MapRatingPath`.
- `onPathInListShortTap` для OUTER-пути в показанном состоянии → тот же
  результат, но через `IOuterPathsInteractor.getCachedOuterPath(pathId)`.
- `onPathInListShortTap` когда interactor вернул `null` или пустой путь →
  событие НЕ отправлено.

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

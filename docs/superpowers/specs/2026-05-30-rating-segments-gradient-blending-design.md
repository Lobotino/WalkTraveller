# Плавный градиент цвета между сегментами трека

## Контекст и проблема

`MainMapFragment` рендерит трек через `MapLibrePathController`, который рисует rating-пути на MapLibre `LineLayer` с цветом из `Expression.match(rating)`. `PathGeoJsonMapper.segmentsToFeatures` группирует подряд идущие сегменты одного рейтинга в единый `LineString` и помечает фичу свойством `rating`.

На стыках двух LineString'ов с разными рейтингами цвет переключается резко: «красный — жёлтый — зелёный — красный». Нужно сделать переход плавным, ~10–15 м.

## Решение в одной строке

Перевести rating-пути на MapLibre `line-gradient` с одним `LineString`-Feature на путь и собственной парой источник+слой для каждого видимого пути. Градиентные стопы строятся по накопленной длине через Haversine: внутри run'а одного рейтинга — два одинаковых стопа (визуально однотонно), на стыке — два разных стопа на расстоянии `blendMeters/2` по обе стороны (линейная интерполяция MapLibre = плавный фейд).

## Архитектура

### Источники и слои

`MapLibrePathController` сейчас держит три пары источник+слой: `wt-saved-rating-source/-layer`, `wt-saved-common-source/-layer`, `wt-current-source/-layer`. После изменения:

- **Сохранённые rating-пути**: вместо одной общей пары — `LinkedHashMap<Long, Pair<sourceId, layerId>>`. ID источника `wt-saved-rating-source-<pathId>`, слоя `wt-saved-rating-layer-<pathId>`. На каждом источнике включено `GeoJsonOptions().withLineMetrics(true)` — требование MapLibre для `line-gradient`. У слоя — собственное `lineGradient(Expression.interpolate(...))`.
- **Сохранённые common-пути**: без изменений (один источник+слой, однотонный `lineColor`).
- **Текущая дорожка**: одна пара с `line-gradient`, перестраивается на каждый `appendCurrentPathSegments`.

### Порядок наложения

Инвариант сохраняется: saved-rating слои ниже common, current сверху.

- Common-слой и current-слой добавляются как сейчас (`addLayer`).
- Каждый новый rating-слой вставляется через `style.addLayerBelow(layer, LAYER_SAVED_COMMON)`.

### Хранение состояния

`savedRatingFeatures: LinkedHashMap<Long, List<Feature>>` заменяется на `savedRatingPaths: LinkedHashMap<Long, MapRatingPath>`. Исходные данные нужны для пересборки градиента при reload стиля (`onStyleLoaded` пересоздаёт все per-path source/layer).

### Lifecycle

- `showRatingPaths(paths)`:
  - Для нового `pathId` — `addSource` (с lineMetrics) + `addLayerBelow(common)`.
  - Для существующего — `setGeoJson` на источнике + `setProperties(lineGradient(rebuild()))` на слое.
- `hidePath(pathId)` / `hidePaths(pathIds)`: для каждого rating `pathId` — `style.removeLayer(layerId)` + `style.removeSource(sourceId)`; common обрабатывается как сейчас.
- `clear()`: снести все per-path rating слои/источники.
- `onStyleLoaded(style)`: пересоздать все rating-пары из `savedRatingPaths`, common и current — как сейчас.

## Построение градиента

Один `Feature` на путь: `LineString` из всех точек `pathSegments` подряд. Дедупликация дублирующихся точек на стыках исходных `MapPathSegment` (start точки следующего сегмента равны finish точкам предыдущего — берём по одному разу).

### Алгоритм стопов

Вход: упорядоченный список точек `p[0..N]` и список рейтингов сегментов `r[1..N]` (где `r[k]` — рейтинг сегмента `p[k-1] → p[k]`).

1. Накопленная длина: `d[0] = 0`, `d[k] = d[k-1] + haversine(p[k-1], p[k])`, итог `L = d[N]`. Если `L == 0` или сегментов нет — возвращаем пустой список (слой получит однотонный fallback `lineColor`, градиент не применяется).
2. Стыки: индексы `k ∈ [1, N-1]` такие, что `r[k] != r[k+1]`. Координата стыка `dJ[k] = d[k]`.
3. Константа `blendMeters` (по умолчанию `12f`, поле конструктора `MapLibrePathController`, прокидывается в builder).
4. Для каждого стыка ограничение половины бленда (чтобы соседние блендовые зоны не пересекались):
   ```
   dPrevBoundary = координата предыдущего стыка (или 0, если этот первый)
   dNextBoundary = координата следующего стыка (или L, если этот последний)
   halfPrev = min(blendMeters / 2, (dJ - dPrevBoundary) / 2)
   halfNext = min(blendMeters / 2, (dNextBoundary - dJ) / 2)
   ```
5. Список стопов (позиция, rating):
   - `(0f, r[1])`
   - Для каждого стыка `k`:
     - `((dJ[k] - halfPrev) / L, r[k])`
     - `((dJ[k] + halfNext) / L, r[k+1])`
   - `(1f, r[N])`
6. Постобработка: монотонность. При совпадении соседних позиций (например, halfPrev=0 для первого стыка вплотную к началу) — оставлять как есть, MapLibre корректно интерпретирует совпадающие стопы как мгновенный переход. Если возникают `progress` за пределами `[0,1]` — clamp.

### Преобразование стопов в Expression

`PathGradientStopsBuilder.build(...)` возвращает `List<GradientStop>` с `rating: SegmentRating`. `MapLibrePathController` мапит rating в `@ColorInt` через имеющийся `ratingColors`, затем строит:

```kotlin
Expression.interpolate(
    Expression.linear(),
    Expression.lineProgress(),
    *stops.flatMap { listOf(Expression.literal(it.progress), Expression.color(resolve(it.rating))) }.toTypedArray()
)
```

Слой получает `PropertyFactory.lineGradient(expr)`. `lineColor` для rating-слоя не задаётся (или задаётся fallback одной из цветов для случая пустых стопов).

### Текущая дорожка

`appendCurrentPathSegments` копит точки. На каждый вызов: пересобрать `LineString`, пересчитать стопы, обновить `setGeoJson` источника, обновить `lineGradient` слоя через `setProperties`. Это O(N) от числа точек — для активной записи приемлемо.

## Структура кода

Новый файл `app/src/main/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilder.kt`:

```kotlin
data class GradientStop(val progress: Float, val rating: SegmentRating)

object PathGradientStopsBuilder {
    fun build(path: MapRatingPath, blendMeters: Float): List<GradientStop>
}

internal fun haversineMeters(a: MapPoint, b: MapPoint): Double
```

Builder и haversine — чистый Kotlin, без MapLibre/Android зависимостей. Тесты живут на JVM.

`PathGeoJsonMapper`:
- Метод `ratingPathToFeatures(path)` заменяется на `ratingPathToFeature(path): Feature?` — одна объединённая `LineString` из дедуплицированных точек, без свойства `rating`. `null` если точек меньше двух.
- `segmentsToFeatures(pathId, segments)` переименовывается в `segmentsToFeature(pathId, segments): Feature?` — также один объединённый `LineString` или `null`. `MapLibrePathController.appendCurrentPathSegments` адаптируется: вместо `ArrayList<Feature>` для current — одно nullable поле `currentFeature: Feature?`.
- `commonPathToFeature` — без изменений.

`MapLibrePathController`:
- Конструктор принимает `blendMeters: Float = 12f`.
- Внутреннее состояние и lifecycle меняются согласно секции «Архитектура».
- `ratingColorExpression` удаляется (больше не используется).
- Слои rating-путей создаются через приватный метод `addRatingLayer(pathId, path)`, который создаёт source с `withLineMetrics(true)`, layer с `lineGradient(buildExpression(path))`, вставляет под common.

## Тесты

`app/src/test/java/ru/lobotino/walktraveller/ui/maplibre/PathGradientStopsBuilderTest.kt`. По проектной конвенции: SUT `sut`, `@Before`, `// given / when / then`, без `mockk(relaxed = true)` (для чистого builder мок и не нужен).

Случаи:

1. Пустой `MapRatingPath` → пустой список.
2. Один сегмент одного rating → `[(0f, R), (1f, R)]`.
3. Несколько сегментов одного rating → `[(0f, R), (1f, R)]`.
4. Два run'а одинаковой длины с одним стыком посередине, blend помещается → 4 стопа: `(0, R1), (0.5 - half/L, R1), (0.5 + half/L, R2), (1, R2)`.
5. Стык, у которого предыдущий run короче `blendMeters` → `halfPrev` обрезается до `(dJ - dPrevBoundary) / 2`.
6. Стык, у которого следующий run короче — симметрично, обрезается `halfNext`.
7. Два стыка близко: блендовые зоны клампятся, `halfNext` первого == `halfPrev` второго == половина расстояния между стыками.
8. Три run'а PERFECT → BADLY → PERFECT → 6 стопов с правильным чередованием цветов.
9. Дубликат точки (длина 0 между двумя точками) → дедуп, прогрессы строго монотонны.
10. Инвариант монотонности прогрессов на нескольких параметризованных входах.
11. `haversineMeters` на трёх известных парах (нулевое расстояние, экватор-1градус, средние широты-1градус) — допуск 1%.

`MapLibrePathController` юнит-тестами не покрываем: взаимодействует с `Style`/`Expression`, мокабельность низкая. Проверка вручную на устройстве: запись со сменой рейтинга → плавный градиент; показ/скрытие сохранённых путей → корректное добавление/удаление слоёв; reload стиля (смена тайл-сорса) → пути перерисовываются.

## Что не делаем

- Не трогаем `common`-пути — у них один цвет, градиент бессмысленен.
- Не оптимизируем под сотни одновременно показанных путей — текущий UX не предполагает этого; per-path layer масштабируется линейно и MapLibre справляется.
- Не вводим конфигурацию `blendMeters` в Settings UI — константа в конструкторе, хардкод значения по умолчанию.
- Не меняем `MapCommonPath`, `MapRatingPath`, `MapPathSegment`, `SegmentRating` — модели стабильны.

## Риски

- **MapLibre line-progress метрика**: вычисляется в проекции Mercator, мы считаем длину Haversine. На коротких сегментах в средних широтах расхождение незначительно (<1%). Приемлемо — `blendMeters` это всё равно настроечная константа.
- **Совпадающие стопы**: если halfPrev/halfNext = 0, два стопа окажутся в одной координате с разными цветами. MapLibre это допускает (трактуется как мгновенный переход) — на стыке у самого начала/конца пути увидим резкий переход, как и сегодня для крайних точек, но это допустимо.
- **Reload стиля**: пересоздание слоёв сейчас покрыто только rating/common как единых источников; новая логика должна корректно итерировать per-path map. Покрыто `onStyleLoaded`.

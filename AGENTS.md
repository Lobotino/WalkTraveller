## Назначение

Этот документ — краткий гид для ИИ-агентов по навигации в проекте WalkTraveller (Android, Kotlin). Он описывает архитектуру, ключевые точки входа, зависимости и практические «плейбуки» для типичных задач.

## Технологический стек

- **Язык/Платформа**: Kotlin, Android SDK 24–35, Java 17
- **UI**: AppCompat + Fragments, Material Components
- **Карта**: osmdroid
- **DI/Фабрики**: кастомные `*ViewModelFactory`
- **Асинхронность**: Coroutines + Flow/Channel
- **Хранилище**: Room (`AppDatabase`), SharedPreferences
- **Сервисы**: Foreground/Bound Services для геолокации и записи трека
- **Аналитика**: Firebase Analytics, Crashlytics
- **Тесты**: `test/` и `androidTest/` (есть интеграционные тесты БД)

## Вершины архитектуры (entry points)

- Приложение: `app/src/main/java/ru/lobotino/walktraveller/App.kt`
  - Инициализирует osmdroid и StrictMode.
- Главная активити/навигация: `ui/MainActivity.kt`
  - Точка входа в экран карты/настроек/онбординга, реализует `IScreenNavigation`.
- Главный экран: `ui/MainMapFragment.kt`
  - Создает `MapViewModel` и `PathsMenuViewModel`, управляет картой и нижним меню.
- Сервисы:
  - `services/WritingPathService.kt` — foreground-сервис записи пути и уведомления.
  - `services/UserLocationUpdatesService.kt` — широковещательные обновления геопозиции для UI.
  - `services/VolumeKeysDetectorService.kt` — изменения рейтинга по кнопкам громкости.
- Room DB: `database/AppDatabase.kt` (+ DAO/Entities в `database/dao`, `database/model`).

## Логические слои

- UI: `ui/**`, `view/**`
  - Компоненты экрана, обработка кликов, отрисовка полилиний.
- ViewModel: `viewmodels/**`
  - Состояние экрана через Flow/Channel, бизнес-события, координация use-case’ов и репозиториев.
- Use cases / Interactors: `usecases/**`
  - Инкапсулируют бизнес-операции (например, `CurrentPathInteractor`, `PathRatingUseCase`, `FinishPathWritingUseCase`).
- Репозитории: `repositories/**`
  - Доступ к данным (Room, SharedPreferences, Files, Permissions, Sensors). Контракты в `repositories/interfaces/**`.
- Данные: `database/**`, модели домена/карты в `model/**`.

## Потоки данных и событий

- Запись пути (основной сценарий):
  1. Пользователь жмет «Start» в `MainMapFragment` → `MapViewModel.onStartPathButtonClicked()`.
  2. `MapViewModel` проверяет разрешения, включает регулярные локации и флаг записи (`IWritingPathStatesRepository`).
  3. `MainMapFragment` запускает `WritingPathService` с `ACTION_START_WRITING_PATH`.
  4. `WritingPathService` подписывается на `LocationUpdatesRepository.observeLocationUpdates()` и на каждую локацию вызывает `CurrentPathInteractor.addNewPathPoint()`.
  5. UI параллельно рисует текущий сегмент пути по событиям из `MapViewModel.observeNewCurrentUserLocation` и собственной логике дорисовки сегментов.
  6. Завершение: `MapViewModel.onStopPathButtonClicked()` → `FinishPathWritingUseCase` → `WritingPathService.finishWritingPath()`.

- Обновление местоположения в UI:
  - `UserLocationUpdatesService` отправляет `LocalBroadcast` (`ACTION_BROADCAST` с `EXTRA_LOCATION`), `MainMapFragment` принимает и пробрасывает в `MapViewModel.onNewLocationReceive()`.

- Рейтинг сегмента:
  - Источник — SharedPreferences через `PathRatingRepository` и `PathRatingUseCase`.
  - Кнопки на UI меняют рейтинг; сервис записи читает текущий рейтинг при сохранении точек.

## База данных

- `AppDatabase` (version 6) и миграции `MIGRATION_1_2 ... MIGRATION_5_6`:
  - `paths`: поля длины, наиболее частый рейтинг, флаг `is_outer_path`.
  - `path_segments`: добавлен `id_path` для оптимизаций выборок.
  - `points`: временная метка `timestamp`.
- Провайдер: `database/provideDatabase(Context)`.

## Важные директории и контракты

- Контракты репозиториев: `repositories/interfaces/**`
  - Примеры: `IPathRepository`, `IPathRatingRepository`, `ILocationUpdatesRepository`, `IWritingPathStatesRepository`, `ITileSourceRepository`, `IUserRotationRepository`, `IUserInfoRepository` и др.
- Репозитории реализации (выборочно):
  - Путь/БД: `DatabasePathRepository`, `CachePathsRepository`, `FilePathsSaverRepositoryV1`, `PathsLoaderRepositoryV1`, `PathsLoaderVersionHelper`.
  - Геопозиция: `LocationUpdatesRepository(updateIntervalMs = 5000)`.
  - Настройки/состояния: `PathRatingRepository`, `WritingPathStatesRepository`, `LastCreatedPathIdRepository`, `LastSeenPointRepository`, `OptimizePathsSettingsRepository`, `TileSourceRepository`, `UserInfoRepository`.
  - Сенсоры/прочее: `UserRotationRepository`, `VibrationRepository`.

## ViewModel’ы и фабрики

- `viewmodels/MapViewModel.kt`
  - Основной оркестратор экрана карты. Каналы/флоу: `observeMapUiState`, `observeNewCurrentPathSegments`, `observeNewCommonPath`, `observeNewRatingPath`, `observeNewCurrentUserLocation`, `observeWritingPathNow` и др.
  - Управляет разрешениями, состоянием записи, центром карты, плитками, ошибками.
- Фабрики: `di/MapViewModelFactory.kt`, `di/PathsMenuViewModelFactory.kt`, `di/SettingsViewModelFactory.kt` — создают VM с зависимостями.

## Ключевые константы/идентификаторы

- SharedPreferences tag: `App.SHARED_PREFS_TAG = "walk_traveller_shared_prefs"`
- БД: `App.PATH_DATABASE_NAME = "walk_traveller"`
- Foreground notification channel (запись трека): `CHANNEL_ID = "walk_traveller_notifications_channel"`
- Intent-экшены:
  - `WritingPathService.ACTION_START_WRITING_PATH = "<APPLICATION_ID>.start_writing_path"`
  - `UserLocationUpdatesService.ACTION_START_LOCATION_UPDATES`
  - Широковещательный канал `UserLocationUpdatesService.ACTION_BROADCAST`, `EXTRA_LOCATION`

## Частые задачи: где править

- Изменить интервал обновления геолокации: `repositories/LocationUpdatesRepository.kt` (аргумент конструктора, по коду — 5000 мс в `MainMapFragment`/`WritingPathService`).
- Добавить новые источники тайлов: реализовать в `model/TileSource*` и учесть в `TileSourceInteractor` + `MainMapFragment.syncTileSource`.
- Изменить логику оценки/рейтингов: `usecases/PathRatingUseCase`, `repositories/PathRatingRepository`, UI-кнопки в `MainMapFragment`/обновления в `MapViewModel`.
- Расширить модель БД: добавить Entity/DAO, поднять `version` в `AppDatabase`, дописать миграцию.
- Импорт/экспорт путей: `FilePathsSaverRepositoryV1`, `PathsLoaderRepositoryV1`, интеграция в `PathsMenuViewModel`.
- Уведомления Foreground: `usecases/PathWritingNowNotificationInteractor` + `PathWritingNowNotificationRepository` + канал в `WritingPathService`.

## Поведенческие инварианты и подводные камни

- UI не пишет в БД напрямую — запись точек делает `WritingPathService` через `CurrentPathInteractor`.
- `MapViewModel` рисует «текущие» сегменты поверх карты, опираясь на последние точки и текущий рейтинг; фактическое состояние в БД может подтягиваться и перерисовываться при ре-инициализации (`updateNewPointsIfNeeded`).
- Рейтинги и флаги состояния записываются в SharedPreferences — сохраняют кросс-сессиево.
- При работе с разрешениями учитывайте разные ветви: «все выданы», «часть отклонена», «ошибка». UI должен обновлять состояние кнопки «Найти меня».
- Не блокируйте Main thread: сохранение в БД — через `Dispatchers.IO` (см. `CurrentPathInteractor`).

## Тесты

- Юнит: пример `test/java/.../DistanceFormatterTests.kt`.
- Инструментальные: `androidTest/java/...` — тесты DAO/миграций/отношений БД.

## Сборка/запуск (минимум для агентских задач)

- Артефакт APK: имя формируется как `walktraveller-${versionName}.apk`.
- Firebase включен; для локальной сборки достаточно поставляемых Gradle плагинов и BOM.

## Карта основных файлов

- Приложение: `App.kt`
- Главный экран: `ui/MainMapFragment.kt`, `ui/MainActivity.kt`
- Сервисы: `services/WritingPathService.kt`, `services/UserLocationUpdatesService.kt`, `services/VolumeKeysDetectorService.kt`
- ViewModel: `viewmodels/MapViewModel.kt`, `viewmodels/PathsMenuViewModel.kt`
- Use cases: `usecases/CurrentPathInteractor.kt`, `usecases/PathRatingUseCase.kt`, `usecases/FinishPathWritingUseCase.kt`, `usecases/UserLocationInteractor.kt`, `usecases/MapStateInteractor.kt`, `usecases/LocalMapPathsInteractor.kt`, `usecases/OuterPathsInteractor.kt`, `usecases/TileSourceInteractor.kt`
- Репозитории (выборочно): `repositories/DatabasePathRepository.kt`, `repositories/LocationUpdatesRepository.kt`, `repositories/PathRatingRepository.kt`, `repositories/WritingPathStatesRepository.kt`, `repositories/UserRotationRepository.kt`, `repositories/UserInfoRepository.kt`
- БД: `database/AppDatabase.kt`, `database/dao/**`, `database/model/**`
- Модели карты/домена: `model/**`

## Быстрые плейбуки

- Добавить запись дополнительного атрибута к точке:
  1) Добавьте поле/таблицу в Room Entity + миграцию в `AppDatabase`.
  2) Протяните в `IPathRepository` и реализацию.
  3) Передайте значение в `CurrentPathInteractor.addNewPathPoint()`.

- Поддержать новый формат импорта треков:
  1) Реализуйте новый загрузчик в `repositories/` по контракту `IPathsLoaderRepository`.
  2) Подключите в `OuterPathsInteractor`/`PathsLoaderVersionHelper`.

- Изменить правила объединения сегментов при отрисовке:
  - Правится в `MainMapFragment.createRatingSegmentPolyline` и логике склейки в `paintNewRatingPaths`/`paintNewCurrentPathSegments`.

## Контакты/Метаданные

- Идентификатор приложения: `applicationId = "ru.lobotino.walktraveller"`
- Версия: `versionName`/`versionCode` задаются в `app/build.gradle`.



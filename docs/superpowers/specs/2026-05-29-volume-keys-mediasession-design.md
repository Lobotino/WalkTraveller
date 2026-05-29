# Замена AccessibilityService на MediaSession для управления рейтингом кнопками громкости

**Дата:** 2026-05-29
**Статус:** одобрено к реализации

## Проблема

Управление рейтингом участка пути кнопками громкости реализовано через
`VolumeKeysDetectorService` (наследник `AccessibilityService`). Эта функциональность
регулярно «отваливается»: система Android отключает доступ к спец-возможностям, и
пользователю приходится включать его вручную. Это ломает основной сценарий приложения.

Корневые причины:

1. **Политика Google Play.** Приложение распространяется через Play. Accessibility API
   по правилам Play допустим только для помощи людям с ограниченными возможностями.
   Использование его ради перехвата кнопок громкости — нарушение, за которое Play
   выдаёт предупреждения и снимает приложение с публикации.
2. **Система отключает accessibility-сервис** при обновлении приложения и при
   подозрительном поведении.
3. **Баг в проверке статуса.** `AccessibilityPermissionRepository.isPermissionsGranted()`
   читает глобальный флаг `Settings.Secure.ACCESSIBILITY_ENABLED` («включена ли вообще
   хоть одна спец-возможность»), а не статус конкретного нашего сервиса. Приложение
   считает разрешение выданным, даже когда наш сервис выключен — поэтому фича «молча»
   перестаёт работать без явной ошибки.
4. **Over-reach в `accessibility_layout.xml`:** `accessibilityEventTypes="typeAllMask"`
   и `feedbackAllMask` (хотя `onAccessibilityEvent` пустой), неверный
   `packageNames="walktraveller"`, отсутствует `android:description`.

## Решение

Полностью отказаться от `AccessibilityService` и перехватывать кнопки громкости через
активную **`MediaSession` + `VolumeProvider`** (как делают медиаплееры). Это работает
при выключенном экране и телефоне в кармане, **не требует никаких спец-возможностей**,
снимает риск по политике Play и убирает весь класс проблем «система отключила доступ».

Используются платформенные классы `android.media.session.MediaSession` и
`android.media.VolumeProvider` (API 21+). У проекта `minSdk 24` — **новых зависимостей
не требуется**.

## Архитектура

### Жизненный цикл MediaSession

MediaSession живёт внутри уже существующего foreground-сервиса `WritingPathService`
и привязан к периоду записи пути:

- `startWritingPath()` (`WritingPathService.kt:179`): создать `MediaSession`,
  `setActive(true)`, выставить `PlaybackState = PLAYING`, вызвать
  `setPlaybackToRemote(volumeProvider)` с `VOLUME_CONTROL_RELATIVE`.
- `finishWritingPath()` / `onDestroy()`: `mediaSession.release()`. После этого кнопки
  громкости снова управляют громкостью устройства.

Так сохраняется текущее поведение «реагируем только во время записи пути» (сейчас это
проверка `isWritingPathNow()` в `VolumeKeysDetectorService.onKeyEvent:55`) — вне записи
сессии просто не существует, перехвата нет.

### Детектор нажатий (сохранение функционала 1:1)

Логика одиночного/двойного нажатия с задержкой 500мс выносится из
`VolumeKeysDetectorService.onKeyEvent` в отдельный класс `VolumeKeysRatingDetector`,
который вызывается из `VolumeProvider.onAdjustVolume(direction)`.

`onAdjustVolume(direction)`: `direction = +1` (громкость вверх), `−1` (вниз).

| Действие | direction | Рейтинг | Поведение |
|---|---|---|---|
| 1× вниз | −1 | `NORMAL` | устанавливается через 500мс |
| 2× вниз | −1, −1 | `BADLY` | в пределах 500мс отменяет таймер NORMAL |
| 1× вверх | +1 | `GOOD` | устанавливается через 500мс |
| 2× вверх | +1, +1 | `PERFECT` | в пределах 500мс отменяет таймер GOOD |

- Нажатие противоположной кнопки отменяет отложенный таймер (как сейчас:
  `upRatingJob?.cancel()` / `downRatingJob?.cancel()`).
- Рейтинг устанавливается через тот же `PathRatingUseCase.setCurrentRating()`, значит
  **вибрация сохраняется без изменений**: одиночная для `NORMAL`/`GOOD`, двойная для
  `BADLY`/`PERFECT`.
- При активной remote-сессии кнопки громкости не меняют громкость устройства — то есть
  «съедаются», как и сейчас (раньше — через `flagRequestFilterKeyEvents` + `return true`).

### Broadcast и потребители

Константа `RATING_CHANGES_BROADCAST` переносится из удаляемого
`VolumeKeysDetectorService` в стабильное место (`WritingPathService` или общий файл
констант). Импорт в `MainMapFragment.kt:90` правится на новое расположение. Приёмник
`ratingChangeReceiver` (`MainMapFragment.kt:856`) и логика broadcast остаются без
изменений.

### Удаление legacy

Удаляются (чистая миграция, без миграционного кода — при обновлении через Play Android
сам убирает исчезнувший accessibility-сервис из списка включённых):

- `services/VolumeKeysDetectorService.kt`
- `res/xml/accessibility_layout.xml`
- `<service>`-блок accessibility в `AndroidManifest.xml`
- `repositories/permissions/AccessibilityPermissionRepository.kt`
- `usecases/permissions/VolumeKeysListenerPermissionsUseCase.kt`
- Диалог `VolumeButtonsPermissionsInfoDialog` (запрос разрешения больше не нужен)
- Строка `error_message_not_allow_access_to_volume_buttons`

### Поток предложения фичи

- `VolumeButtonsFeatureSuggestDialog` + туториал — **сохраняются**.
- Убирается шаг запроса разрешения: `onVolumeFeaturePermissionsInfoConfirm()` и
  `syncRequestPermissionsState()` в `MapViewModel`, диалог `VolumeButtonsFeatureInfo`.
  После согласия пользователя запись пути стартует сразу.
- `needToAskVolumeButtonsPermissions()` упрощается/удаляется (нет разрешения для
  проверки).

### Настройки

1. **Тумблер «Управление рейтингом кнопками громкости» (вкл/выкл)** добавляется в
   `SettingsFragment` / `fragment_settings.xml`. Состояние хранится в SharedPreferences
   (новое поле в `UserInfoRepository` или отдельный репозиторий настройки). Когда выключен
   — `WritingPathService` не поднимает remote-VolumeProvider при старте записи.
2. **Разовое предложение фичи при старте записи сохраняется** (как сейчас, через
   `needToSuggestVolumeFeature()`).
3. Строка статуса «разрешение дано/нет» **не добавляется** — разрешение упразднено.

## Риск и проверка

Главный риск — надёжность того, что именно наша `MediaSession` получает события кнопок
громкости при выключенном экране/в кармане, особенно если параллельно играет музыка.

**Первый шаг реализации — спайк на реальном устройстве:** активная сессия +
`PlaybackState = PLAYING` + remote `VolumeProvider`. Проверить перехват при выключенном
экране и при играющей музыке. Если приоритета сессии недостаточно — добавить удержание
audio focus и/или тихий зацикленный аудиотрек (есть нюанс по расходу батареи, поэтому
добавляем только при подтверждённой необходимости, не вслепую).

## Что НЕ входит в объём

- Миграционный код для существующих пользователей (Android сам уберёт accessibility-сервис).
- Строка статуса разрешения в настройках (разрешение упразднено).
- Изменение самих значений рейтинга / логики вибрации.

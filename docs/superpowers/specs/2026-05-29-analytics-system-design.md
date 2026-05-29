# Analytics System Design

**Date:** 2026-05-29
**Status:** Approved

## Goal

Add an abstract analytics system to WalkTraveller for tracking basic app-usage
events. Firebase Analytics (already a dependency but currently unused) becomes
one concrete logger behind the abstraction. The abstraction must allow adding
other loggers (or an opt-out gate) later without touching call sites.

## Context

- `firebase-analytics` is already declared in `app/build.gradle` (firebase-bom
  32.8.1), with the `google-services` plugin and `google-services.json` present.
  It is never initialized or called anywhere today.
- Architecture: clean-ish layering (interfaces + implementations), repositories
  / usecases / viewmodels / ui. Manual DI: dependencies are constructed inline
  in fragments and passed through `*ViewModelFactory` classes into ViewModels.
  No Hilt/Koin.
- Analytics is **always on** for v1 — no consent toggle. The abstraction must
  make adding an opt-out trivial later.

## Architecture

New package: `ru.lobotino.walktraveller.analytics`

Two levels of abstraction, matching "abstract sending system + GA as one logger":

| Component | Type | Responsibility |
|---|---|---|
| `AnalyticsEvent` | sealed class | Event catalog with typed parameters. Single source of truth for events. |
| `IAnalyticsTracker` | interface | The "sending system". Single method `track(event: AnalyticsEvent)`. ViewModels depend on this. |
| `AnalyticsTracker` | class | Implementation: holds `List<IAnalyticsLogger>` and fans the event out to each. |
| `IAnalyticsLogger` | interface | Abstract logger. Single method `log(event: AnalyticsEvent)`. |
| `FirebaseAnalyticsLogger` | class | GA as a concrete logger. Maps `AnalyticsEvent` → `FirebaseAnalytics.logEvent(name, bundle)`. |
| `DebugLogAnalyticsLogger` | class | Writes events to logcat. Wired only in debug builds; aids verification. |

The mapping from events to Firebase event/parameter names lives **only** inside
the Firebase layer. The rest of the app knows only `AnalyticsEvent`.

To keep the mapping unit-testable without a real Firebase backend, the
event → `(name: String, params: Map<String, Any>)` conversion is a **pure
function** (e.g. `AnalyticsEvent.toFirebasePayload()`). `FirebaseAnalyticsLogger`
just converts that map into a `Bundle` and calls `FirebaseAnalytics.logEvent`.
Tests assert on the pure mapping; no Firebase mocking needed.

## Event Catalog and Call Sites

All Firebase event names are snake_case and ≤40 chars (GA4/Firebase limits).

| `AnalyticsEvent` | Firebase event | Parameters | Call site |
|---|---|---|---|
| `TrackRecordingStarted` | `track_recording_started` | — | `MapViewModel.startPathTracking()` (inside `allGranted`, after `setWritingPathNow(true)`) |
| `TrackRecordingFinished` | `track_recording_finished` | — | `MapViewModel.onStopPathButtonClicked()` |
| `TrackShared(pathsCount)` | `track_shared` | `paths_count` | `PathsMenuViewModel.onShareSelectedPathsButtonClicked()`, after successful `shareFileChannel.trySend(...)` |
| `RatingGiven(rating)` | `rating_given` | `rating` (badly/normal/good/perfect/none) | `MapViewModel.onRatingButtonClicked()` |
| `OuterPathImported(pathsCount)` | `outer_path_imported` | `paths_count` | `PathsMenuViewModel.onOuterPathsConfirmButtonClicked()` |
| `PathShown(pathsCount, type)` | `path_shown` | `paths_count`, `path_type` (rating/common) | `PathsMenuViewModel` user-initiated shows (`loadAndShowSelected*`, `loadAndShowAll*`) |
| `ScreenView(screenName)` | `screen_view` (Firebase standard) | `screen_name` | `MainMapFragment`, `SettingsFragment`, `FirstWelcomeFragment` in `onResume` |
| `VolumeFeatureSuggest(accepted)` | `volume_feature_suggest` | `accepted` (bool) | `MapViewModel.onVolumeFeatureSuggestAccepted()` / `onVolumeFeatureSuggestDecline()` |

`rating` values map from `SegmentRating` enum (BADLY, NORMAL, GOOD, PERFECT,
NONE), lowercased.

## DI / Wiring

Follows the existing manual-DI pattern.

- `IAnalyticsTracker` is an app-scoped singleton, created in `App.onCreate()`:

  ```kotlin
  analyticsTracker = AnalyticsTracker(
      buildList {
          add(FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(this@App)))
          if (BuildConfig.DEBUG) add(DebugLogAnalyticsLogger())
      }
  )
  ```

  Exposed as a property on `App`; accessed via `(applicationContext as App).analyticsTracker`.
- Passed into ViewModels through the existing factories
  (`MapViewModelFactory`, `PathsMenuViewModelFactory`) by adding an
  `analyticsTracker: IAnalyticsTracker` parameter, like the other dependencies.
- For `ScreenView`, fragments read the tracker from `App` directly (no ViewModel
  needed for a screen event).
- Firebase initializes itself via the `google-services` plugin + ContentProvider;
  no manual init required.

## Error Handling

- `FirebaseAnalyticsLogger.log()` wraps the Firebase call in try/catch and
  swallows exceptions — analytics must never crash the app.
- `AnalyticsTracker` isolates loggers from each other: a failure in one logger
  does not prevent the others from receiving the event.

## Testing (TDD)

- `RecordingAnalyticsTracker` — test fake that records events into a list.
- ViewModel unit tests: assert that a given action emits the correct
  `AnalyticsEvent` with the right parameters.
  - `MapViewModel`: start/stop recording, rating given, volume-feature accept/decline.
  - `PathsMenuViewModel`: share, outer-path import, path shown.
- `AnalyticsTracker` fan-out: one event reaches all loggers; an exception thrown
  by one logger does not stop the others.
- `AnalyticsEvent.toFirebasePayload()` mapping: verify each event maps to the
  correct Firebase event name and parameter map. Pure function, no Firebase
  backend needed.

## Out of Scope (v1)

- Consent / opt-out UI and gating (abstraction leaves room to add it later).
- Enriched parameters that require extra DB queries (e.g. distance/duration on
  recording finished).
- GA4 Measurement Protocol / non-Firebase backends.

# Hibiscus: Truck Park Management (Android)

Jetpack Compose + Material 3 app for truck park operations. The project is in
**Milestone 1 (foundation)**: theming, navigation and placeholder screens. No
data, sign-in or synchronization exists yet.

## Status

| Item | Status |
|---|---|
| Builds (`testDebugUnitTest`) | Reported working by the project owner. Not re-run in this README update. |
| Unit tests | `ThemeModeTest` (theme preference logic) |
| Runs on device/emulator | Reported working by the project owner |
| Light / Dark / System theme, saved on device | Implemented (DataStore) |
| Four-tab bottom bar | Implemented (floating glass bar) |
| Top-right menu with theme picker | Implemented (bottom sheet) |
| Animated splash screen | Implemented |
| Launcher icon | Implemented (all densities) |
| Database, sign-in, roles, sync, reports | Not started |

## Architecture

```
app/src/main/java/com/truckpark/management/
├── app/                      MainActivity, Application (manual AppContainer)
├── core/
│   ├── common/               ThemeMode enum and resolveDark()
│   └── designsystem/
│       ├── components/       GlassCard, LiquidGlassNavigationBar, StatusBadge, ...
│       ├── icons/            TruckParkIcons (hand-built vector icons)
│       └── theme/            Color, Type, Theme
├── data/
│   ├── local/                ThemePreferencesDataSource (DataStore)
│   └── repositories/         ThemeRepositoryImpl
├── domain/
│   └── repositories/         ThemeRepository interface
├── feature/
│   ├── placeholder/          PlannedFeatureScreen (shared placeholder for tabs)
│   ├── settings/             MoreOptionsBottomSheet, ThemeViewModel
│   └── splash/               HibiscusAnimatedSplashScreen
└── navigation/
    ├── TopLevelDestination   The four tabs: Home, Visits, Registers, Reports
    └── TruckParkAppRoot      NavHost, bottom bar, menu sheet, scrim
```

Dependency injection is manual (`AppContainer` in `TruckParkApplication`).
Hilt is not used.

## Theming

Appearance is one of three modes: **Light**, **Dark** or **System**. The choice
is saved with DataStore and restored on restart. Switching applies immediately
in composition, so the activity is not recreated and form state is kept.

Colours are defined in `theme/Color.kt`: an indigo primary with a sky-blue
secondary in light mode, and a slate-based dark scheme.

## Navigation

- **Bottom bar:** four tabs in a floating capsule. The indicator slides between
  tabs. Passing through intermediate tabs takes 90 ms each, and the final
  landing takes 220 ms.
- **Menu:** the top-right button opens a bottom sheet with the theme picker.
- Each tab currently shows a placeholder screen (`PlannedFeatureScreen`) that
  says which milestone will add the feature.

## Splash

`HibiscusAnimatedSplashScreen` plays on every cold start (about 3 seconds). It
has a fixed dark background (`#05071E`) and does not follow the chosen theme.
The Android window background uses the same colour, so there is no flash
before the splash draws.

## Icons and dependencies

- Icons are hand-built in `TruckParkIcons.kt`. Material icon libraries are not
  used.
- Dependencies are Compose BOM, Material 3, Navigation Compose, DataStore,
  Lifecycle and Activity Compose, plus JUnit for tests.

## Build

Requires Android Studio with JDK 17+ and Android SDK Platform 35.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # install on a connected device or emulator
```

## Known gaps and risks

- **Icon rebuilds:** the tab icons are rebuilt on every frame of the glide,
  which costs CPU on low-end devices.
- **Tab accessibility:** each tab's icon and label both announce the tab name,
  so screen readers repeat it. Tabs have no pressed feedback because the
  ripple is disabled.
- **Splash:** it cannot be skipped and ignores touches during its roughly
  3-second run. It also ignores the light/dark setting.
- **Hard-coded colours:** the bar and splash use literal colours outside
  `Color.kt`, so theme changes will not reach them.
- No real backdrop blur. Glass effects use layered gradients and transparency.
- Backend not yet chosen.

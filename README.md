# Truck Park Management (Android)

Status: **Milestone 1 source written. NOT yet compiled or run by the author.**

## Verification status (read this first)

| Item | Status |
|---|---|
| Source files for Milestone 1 | Written |
| Gradle sync / compile | **Not done** – the authoring environment had no Android SDK and could not reach Google's Maven repository |
| Unit tests (`ThemeModeTest`) | Written, **not run** |
| Run on emulator/device | **Not done** |
| Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) | **Missing** – could not be generated offline |
| Dependency versions | Chosen from knowledge, unverified (see `gradle/libs.versions.toml`) |

Expect to fix small compile errors on first sync. Nothing here should be treated as tested.

## Open in Android Studio

1. Install a recent Android Studio (needs JDK 17+, Android SDK Platform 35).
2. File > Open > select this `TruckParkManagement` folder.
3. When prompted, let Studio create/upgrade the Gradle wrapper (or run `gradle wrapper --gradle-version 8.10.2` in this folder; AGP 8.7.x needs Gradle 8.9+).
4. Sync, then run the `app` configuration on an emulator (API 26+).
5. Unit tests: `./gradlew :app:testDebugUnitTest`.

## Milestone 1 scope

Implemented (in source, untested):
- Gradle/Kotlin/Compose project, minSdk 26, targetSdk 35, `allowBackup=false`.
- Layered package layout: `app`, `core/common`, `core/designsystem`, `data/local`, `data/repositories`, `domain/repositories`, `feature/*`, `navigation`.
- Light / Dark / System Default appearance, saved with DataStore and restored on restart. The theme is applied in composition only, so switching does not recreate the activity or discard `rememberSaveable` form state.
- Full light and dark `ColorScheme`s plus extra colours for glass surfaces and status badges (success, info, warning, critical, neutral, offline).
- Liquid-glass-style card built from layers (translucent fill, diagonal specular sheen, bottom accent glow, gradient rim) over a backdrop with soft cyan/indigo light pools. **No real backdrop blur or refraction yet.** Status badge uses colour + icon + text.
- Custom hand-drawn icon set (`core/designsystem/icons/TruckParkIcons.kt`): thin geometric outlines, one stroke weight, one accent dot. The Material icons dependency was removed.
- Visual quality has **not been seen on a screen** by the author; expect to tune spacing, glow strength and icon proportions after the first run.

Optional upgrade after the project builds: real backdrop blur via a library such as Haze (its current API could not be confirmed offline, so it was not added blind).
- Five-section adaptive navigation (Home, Visits, Registers, Reports, More): bottom bar on phones, rail on wider windows.
- Appearance picker in **More**.

Placeholders (explicitly marked "Not implemented yet" in the UI): Home, Visits, Registers, Reports.

Not started: everything in milestones 2–14 (authentication, roles, visits, Room database, sync protocol, registers, visitors, incidents, inspections, attendance, handovers, alerts, dashboards, search, PDF reports, archiving, backup, security testing, deployment docs). There is no backend yet and none has been selected.

## Still to do for Milestone 1 sign-off

- Compile and run; fix any errors.
- Verify contrast of both palettes (e.g. with the Accessibility Scanner and TalkBack).
- Manual check: switch themes mid-form and confirm input is kept; kill and relaunch to confirm persistence.
- Add the app icon and a splash/window background that matches the chosen theme (a brief mismatch is possible on first frame when forcing Light on a dark system).
- Decide whether to add a real backdrop-blur library behind an API-level check.

# 🌺 Hibiscus — Truck Park Management (Android)

A modern Android application for truck park management, built with **Jetpack Compose**, **Material 3**, and a custom **Liquid Glass Design System**.

---

## ✅ Current Status

| Item | Status |
|---|---|
| Compiles & builds | ✅ Verified (`./gradlew testDebugUnitTest` passes) |
| Unit tests | ✅ Passing |
| Runs on device/emulator | ✅ Working |
| Branding & Identity | ✅ Official **Hibiscus** emblem, wordmark & icons |
| Splash Screen | ✅ Dynamic animated liquid glass splash with shimmer |
| Theming (Light / Dark / System) | ✅ Implemented & persisted |
| Navigation | ✅ Liquid Glass bottom bar with animated bubble |
| Responsiveness | ✅ All screen sizes, orientations, nav types |

---

## 🏗️ Architecture

```
app/
└── src/main/java/com/truckpark/management/
    ├── core/
    │   ├── common/           # Shared utilities
    │   ├── designsystem/
    │   │   ├── components/   # GlassCard, LiquidGlassNavigationBar, ...
    │   │   ├── icons/        # Custom hand-drawn icon set (TruckParkIcons.kt)
    │   │   └── theme/        # ColorScheme, Typography, Theme.kt
    │   └── datastore/        # ThemeMode persistence via DataStore
    ├── data/
    │   ├── local/            # Room DB stubs
    │   └── repositories/     # Data repository impls
    ├── domain/
    │   └── repositories/     # Domain repository interfaces
    ├── feature/
    │   ├── home/             # Home screen
    │   ├── visits/           # Visits screen
    │   ├── registers/        # Registers screen
    │   ├── reports/          # Reports screen
    │   ├── settings/         # More / Settings screen (MoreScreen.kt)
    │   ├── splash/           # HibiscusAnimatedSplashScreen.kt
    │   └── placeholder/      # PlannedFeatureScreen with brand header
    └── navigation/
        ├── TruckParkAppRoot.kt    # Root composable, safe drawing, bottom insets
        ├── TruckParkNavGraph.kt   # NavHost + routes
        └── TopLevelDestination.kt # Bottom nav enum (Home, Visits, Registers, Reports)
```

**Stack:** Kotlin · Jetpack Compose · Material 3 · DataStore · Hilt (DI) · Navigation Compose · Gradle Version Catalog

---

## 🎨 Design System & Branding

### Hibiscus Brand Identity
- **App Icon**: Dedicated pure "H" Highway Ribbon Emblem centered inside the 66% safe zone with radial edge-feathering, blending seamlessly into the obsidian-navy `#05071E` background without square or boundary clipping. Full mipmaps generated (`mdpi` through `xxxhdpi`).
- **Animated Splash Screen (`HibiscusAnimatedSplashScreen.kt`)**:
  - Ambient radial pulse breathing in brand cyan (`#00F5FF`), indigo (`#4938FF`), and magenta (`#C500FF`).
  - Elastic scale-bloom entrance for the glossy "H" emblem.
  - Smooth upward glide and reveal for the "HIBISCUSS" wordmark.
  - Specular shimmer light-beam sweep and expanding luminous cyan underline.
  - Tagline `"TRUCK PARK MANAGEMENT"` with clean tracking.
  - Clean solid-color window background (`#0B0F19`) preventing duplicate logos or screen flashes.
- **Header**: Top of all main screens displays **Hibiscus** in the vibrant 4-color brand gradient with subtitle `"Truck Park"`.

### Liquid Glass UI
A custom design language inspired by glassmorphism:
- **GlassCard** — Layered translucent surfaces with diagonal specular sheen, bottom accent glow, gradient rim, and soft cyan/indigo light pools beneath.
- **LiquidGlassNavigationBar** — Floating capsule bottom navigation bar with a physics-driven liquid bubble indicator that glides smoothly between tabs.

### Theming
Three modes selectable in-app and persisted across restarts:

| Mode | Description |
|---|---|
| 🌙 **Dark** | Deep navy surfaces, vivid accent glows |
| ☀️ **Light** | Bright whites, warm sunrise amber accents |
| ⚙️ **System** | Follows OS light/dark preference |

Theme is applied in composition only — switching does **not** recreate the Activity or discard form state.

### Custom Icon Set (`TruckParkIcons.kt`)
Hand-drawn, geometric thin-outline icons with a single accent dot. Multi-tone SVG layers with glass badge details. Material icons dependency removed entirely.

Includes custom theme picker icons:
- `ThemeSystem` — gradient globe with system badge
- `ThemeLight` — warm sunrise amber/orange sun
- `ThemeDark` — indigo crescent moon

---

## 🧭 Navigation

### Bottom Navigation Bar — Liquid Glass
A floating capsule bar rendered at the bottom of the screen:

- **4 top-level destinations:** Home · Visits · Registers · Reports
- **Liquid bubble indicator** that glides across tabs with a step-by-step animation:
  - Each intermediate tab is swept through in **90ms** (visibly passing, never appearing to settle)
  - Final destination gets a **220ms ease-out** smooth landing
  - Example: Home → Reports = 90ms + 90ms + 220ms = natural fluid glide through all tabs
- Proximity-based icon colour glow and scale as the bubble passes each tab
- **More** (5th item) opens a bottom sheet instead of navigating

### Responsiveness
Fully responsive across:
- **Phone portrait & landscape**
- **Tablet / foldable wide windows**
- **All system navigation types:** gesture navigation, 2-button, 3-button
- Uses `.safeDrawingPadding()` + dynamic WindowInsets bottom padding to never overlap system bars or nav buttons
- Nav bar and feature screens constrained with `widthIn(max = 560.dp / 680.dp)` for comfortable wide-screen layouts

---

## ⚙️ More / Settings Screen

Accessible via the hamburger / "More" tab at the bottom:

- **Appearance selector** — three polished theme cards with animated multi-tone SVG icons, active selection glow/border, and smooth state transitions
- Clean bottom sheet with full opacity surface and proper contrast typography

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK Platform 35

### Run
```bash
# Sync & build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Install on connected device/emulator
./gradlew installDebug
```

---

## 🗺️ Roadmap

### ✅ Milestone 1 — Foundation (Complete)
- [x] Project setup (minSdk 26, targetSdk 35)
- [x] Design system: Liquid Glass cards, colours, typography
- [x] Custom icon set (no Material icons dependency)
- [x] Light / Dark / System theming with DataStore persistence
- [x] Bottom navigation with animated liquid bubble glide
- [x] Fully responsive layout (all orientations, all nav types)
- [x] More / Settings screen with appearance picker
- [x] Adaptive app icon & dark theme-matching splash screen

---

## 📝 Notes

- No real backdrop blur yet — surfaces simulate depth with layered gradients and alpha. A library like [Haze](https://github.com/chrisbanes/haze) can be added once API compatibility is confirmed.
- Backend not yet selected or started.


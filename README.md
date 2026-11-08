# Hibiscus: Truck Park Management (Android)

Jetpack Compose + Material 3 app for truck park operations, backed by **Oracle Autonomous Database (ADB)** and **Oracle REST Data Services (ORDS)**.

The project has completed **Milestone 1 (Foundation)** and **Milestone 2 (Identity, Access Management & Role-Based Workspaces)**:
- Liquid-metal authentication UI (Sign In, Create Account, Forgot Password reset flow, 6-Digit Email Verification, and Admin Security Setup gate).
- Server-side email availability check, OTP code generation, and Brevo transactional email delivery inside Oracle PL/SQL (`PKG_API`).
- Zero unverified persistence: new accounts and Admin credential updates are written to `users` and `auth_identities` **only after** the 6-digit verification code is confirmed.
- Auto-approval as `RUNNER` on self-registration with immediate sign-in, plus live Admin role management (`RUNNER`, `SUPERVISOR`, `ADMIN`) and role-adaptive navigation & workspaces.

## Status

| Item | Status |
|---|---|
| Builds (`testDebugUnitTest`) | Working |
| Unit tests | `ThemeModeTest`, `EmailValidationTest`, `AuthRepositoryImplTest`, `OrdsAuthRemoteDataSourceTest` |
| Light / Dark / System theme, saved on device | Implemented (DataStore) |
| Role-adaptive floating glass bottom bar | Implemented (`RUNNER`: 3 tabs; `SUPERVISOR` & `ADMIN`: 4 tabs) |
| Top-right menu with account card, role sync, theme picker & Sign Out | Implemented (bottom sheet) |
| Animated splash screen & launcher icons | Implemented (all densities) |
| Oracle ADB + ORDS Authentication & RBAC (`PKG_API`, `PKG_AUTH`, `PKG_AUTHZ`, `PKG_ADMIN`) | Implemented |
| Server-side Brevo OTP & transactional email dispatch (`UTL_SMTP` / `UTL_HTTP`) | Implemented |
| Admin Security Setup gate (`must_change_password` / `admin@hibiscuss.com`) | Implemented |
| Auto-approval as `RUNNER` + Admin Staff Role Management (`RUNNER` / `SUPERVISOR` / `ADMIN`) | Implemented |
| Keystore-encrypted session storage (`SessionStore`) & automatic token refresh | Implemented |
| Visits, Registers, Local Room Sync & PDF Reports (Milestones 3–12) | Planned |

## Architecture

```
app/src/main/java/com/truckpark/management/
├── app/                      MainActivity, TruckParkApplication (manual AppContainer)
├── core/
│   ├── common/               AppConfig, AppResult, AppError, ThemeMode, EmailValidation
│   └── designsystem/
│       ├── components/       GlassCard, LiquidGlassNavigationBar, StatusBadge, ...
│       ├── icons/            TruckParkIcons (hand-built vector icons)
│       └── theme/            Color, Type, Theme
├── data/
│   ├── local/                ThemePreferencesDataSource (DataStore), SessionStore (Android Keystore AES-GCM)
│   ├── remote/
│   │   ├── HttpTransport.kt  HTTPS transport interface + HttpUrlConnectionTransport
│   │   └── auth/             AuthRemoteDataSource, OrdsAuthRemoteDataSource
│   └── repositories/         ThemeRepositoryImpl, AuthRepositoryImpl
├── domain/
│   ├── models/               AuthModels (AuthUser, AuthSession, ManagedUser)
│   └── repositories/         ThemeRepository, AuthRepository
├── feature/
│   ├── auth/                 LiquidMetalAuthScreen, AdminSecuritySetupScreen, EmailCodeVerification, AuthViewModel
│   ├── operations/           OperationsScreen (role-specific workspace cards + Admin Role Management panel)
│   ├── settings/             MoreOptionsBottomSheet (user profile, role sync, theme picker, Sign Out), ThemeViewModel
│   └── splash/               HibiscusAnimatedSplashScreen
└── navigation/
    ├── TopLevelDestination   Home, Visits, Registers, Reports
    └── TruckParkAppRoot      Role-filtered NavHost, floating bottom bar, menu sheet, scrim
```

Dependency injection is manual (`AppContainer` in [`TruckParkApplication.kt`](file:///sdcard/project/TruckPark/app/src/main/java/com/truckpark/management/app/TruckParkApplication.kt)). Hilt is not used.

## Authentication, Verification & Role-Based UI

1. **Pre-Verification Account Hygiene**:
   - Pressing **Create Account** calls `POST auth/request-signup-code`. The backend checks if the email already exists (`409 ALREADY_EXISTS`), generates a 6-digit code in PL/SQL, hashes it in `email_outbox`, and sends it via Brevo.
   - Nothing is stored in `users` or `auth_identities` until the user enters the valid 6-digit code (`POST auth/register`).
2. **Default Auto-Approval & Immediate Sign-In**:
   - Upon verifying the 6-digit code, the account is created with `status = 'APPROVED'` and default role `RUNNER`, and the app automatically signs the user into the Runner workspace.
3. **Bootstrap Admin Security Setup Gate**:
   - Signing in with the bootstrap admin account (`admin@hibiscuss.com` / `@Admin`, where `must_change_password = 1`) opens `AdminSecuritySetupScreen`.
   - New admin credentials are only committed after the server verifies the 6-digit code sent to the new admin email (`POST auth/send-verification-code` → `POST auth/change-password`).
4. **Role-Adaptive Workspaces & Live Role Updates**:
   - **Runner (`RUNNER`)**: 3 bottom tabs (`Home`, `Visits`, `Registers`) and Runner Gate Workspace actions.
   - **Supervisor (`SUPERVISOR`)**: All 4 bottom tabs (`Home`, `Visits`, `Registers`, `Reports`) and Supervisor Yard Control Workspace.
   - **Admin (`ADMIN`)**: All 4 bottom tabs plus the **Staff Role Management** panel on the Home tab to switch any user between `RUNNER`, `SUPERVISOR`, and `ADMIN`.
   - Staff can tap **Sync Role & Permissions** or **Sign Out** anytime from the top-right options sheet.

## Theming

Appearance is one of three modes: **Light**, **Dark** or **System**. The choice is saved with DataStore and restored on restart. Switching applies immediately in composition, so the activity is not recreated and form state is kept.

Colours are defined in `theme/Color.kt`: an indigo primary with a sky-blue secondary in light mode, and a slate-based dark scheme.

## Splash

`HibiscusAnimatedSplashScreen` plays on every cold start (about 3 seconds). It follows the chosen theme: dark navy in Dark mode and near-white in Light mode. Tapping it skips straight to the app.

## Build & Configuration

Requires Android Studio with JDK 17+ and Android SDK Platform 35.
Copy `.env.example` to `.env` at the project root and set `ORDS_BASE_URL` to your Oracle ADB ORDS base URL.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # install on a connected device or emulator
```

For database installation and ORDS package setup, see [`backend/README.md`](file:///sdcard/project/TruckPark/backend/README.md).

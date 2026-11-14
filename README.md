# Hibiscus: Truck Park Management (Android) — v1.5.0 (Milestone 5)

Jetpack Compose + Material 3 app for SADC truck park and gate operations, backed by **Oracle Autonomous Database (ADB)** and **Oracle REST Data Services (ORDS)**.

The project has completed **Milestones 1–5**:
- **Milestone 1 (Foundation)**: Navigation shell, Light/Dark/System DataStore theme engine, liquid glass design system, animated splash screen.
- **Milestone 2 (Identity, Access Management & Role-Based Workspaces)**:
  - Liquid-metal authentication UI (Sign In, Create Account, Forgot Password reset flow, 6-Digit Email Verification, Admin Security Setup gate, and Profile & Email Update with dual 6-digit OTP verification).
  - Server-side email availability check, OTP code generation, and Brevo transactional email delivery inside Oracle PL/SQL (`PKG_API`).
  - Zero unverified persistence: new accounts and credential updates are written to `users` and `auth_identities` **only after** the 6-digit verification code is confirmed.
  - Auto-approval as `RUNNER` on self-registration with immediate sign-in, plus live Admin role management (`RUNNER`, `SUPERVISOR`, `ADMIN`), handset management (max 2 active handsets), and granular per-user permission overrides.
- **Milestone 3 (Truck Visits & SADC Gate Operations)**:
  - Full-page **Arrival Check-In** and **Departure Check-Out / Visit Details** flows with collapsible optional sections, SADC country dropdown selector, and bidirectional auto-refill between Front Plate and Horse Plate.
  - Duplicate open-visit detection (both locally and on the server during sync) and staff attribution (`Checked In By` on arrival details, `Exited / Checked Out By` on departure).
  - CameraX + Google ML Kit OCR for **Live Scanning** and **Background Photo Capture Extraction** across Front Plate, Horse Plate, Transporter/Carrier, and Container/Trailer Seals.
- **Milestone 4 (Offline-First Local Room Database & Cloud Sync Protocol)**:
  - Room local persistence (`visits`, `visit_corrections`, `sync_queue`) with pull-to-refresh, sticky On Yard / Departed switch & search bar, background auto-sync, and conflict resolution UI (`Keep Device` vs `Use Server`).
- **Milestone 5 (Driver, Cargo, Seal Verification & Audit History)**:
  - International driver contact entry with country dial codes, structured cargo manifest items, arrival & exit seal verification with photo attachments, custom per-category columns, and immutable post-arrival/post-departure correction history.

## Status

| Item | Status |
|---|---|
| Version | `1.5.0-m5` (`versionCode = 5`) |
| Builds (`testDebugUnitTest`) | Working |
| Light / Dark / System theme, saved on device | Implemented (DataStore) |
| Role-adaptive floating glass bottom bar | Implemented (`RUNNER`, `SUPERVISOR`, `ADMIN`) |
| Top-right menu with Profile & Dual-OTP Email Update, Role Sync, Theme Picker & Sign Out | Implemented (bottom sheet) |
| Animated splash screen & launcher icons | Implemented (all densities) |
| Oracle ADB + ORDS Authentication & RBAC (`PKG_API`, `PKG_AUTH`, `PKG_AUTHZ`, `PKG_ADMIN`) | Implemented |
| Server-side Brevo OTP & transactional email dispatch (`UTL_SMTP` / `UTL_HTTP`) | Implemented |
| Admin Security Setup gate (`must_change_password` / `admin@hibiscuss.com`) | Implemented |
| Auto-approval as `RUNNER` + Admin Staff Role, Handset & Permission Override Governance | Implemented |
| Keystore-encrypted session storage (`SessionStore`) & automatic token refresh | Implemented |
| SADC Truck Visits, Dual Plates, CameraX + ML Kit Live Scan & Photo Capture OCR (M3) | Implemented |
| Offline-First Room Database, Sync Queue, Pull-to-Refresh & Conflict Resolution (M4) | Implemented |
| Cargo Manifests, Seal Verification, Photo Evidence & Correction Audit Trail (M5) | Implemented |
| Visitors, Incidents, Inspections, Attendance, Shifts, Alerts & PDF Reports (Milestones 6–12) | Planned |

## Architecture

```
app/src/main/java/com/truckpark/management/
├── app/                      MainActivity, TruckParkApplication (manual AppContainer)
├── core/
│   ├── common/               AppConfig, AuthResult, ThemeMode
│   └── designsystem/
│       ├── components/       GlassCard, LiquidGlassNavigationBar, StatusBadge
│       ├── icons/            TruckParkIcons
│       └── theme/            Color, Type, Theme
├── data/
│   ├── local/                ThemePreferencesDataSource, SessionStore (Keystore AES-GCM), TransporterStore, Room DB (TruckParkDatabase, VisitDaos, VisitEntities)
│   ├── remote/
│   │   ├── HttpTransport.kt  HTTPS transport interface + HttpUrlConnectionTransport
│   │   ├── auth/             AuthRemoteDataSource, OrdsAuthRemoteDataSource
│   │   └── visits/           VisitsRemoteDataSource
│   └── repositories/         ThemeRepositoryImpl, AuthRepositoryImpl, VisitsRepositoryImpl
├── domain/
│   ├── models/               AuthModels, VisitModels (TruckVisit, CargoItem, VisitSeal, VisitCorrectionRecord, PlateInfo)
│   └── repositories/         ThemeRepository, AuthRepository, VisitsRepository
├── feature/
│   ├── auth/                 LiquidMetalAuthScreen, AdminSecuritySetupScreen, EmailCodeVerification, CountryDialCode, AuthViewModel
│   ├── ocr/                  PlateScannerDialog, PlateTextAnalyzer (CameraX Live OCR + Background Photo OCR)
│   ├── operations/           OperationsScreen (role-specific workspace cards + Admin Staff/Handsets/Overrides Governance)
│   ├── settings/             MoreOptionsBottomSheet (View/Update Profile with Dual-OTP Email Verification, Theme Picker, Sign Out), ThemeViewModel
│   ├── splash/               HibiscusAnimatedSplashScreen
│   └── visits/               VisitsScreen, VisitDetailsAndCargoComponents, VisitsViewModel
└── navigation/
    ├── TopLevelDestination   Home, Visits, Registers, Reports
    └── TruckParkAppRoot      Role-filtered NavHost, floating bottom bar, menu sheet
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

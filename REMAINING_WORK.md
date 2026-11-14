# Remaining Work

This file lists what has been completed and what the app still needs, based on the master development prompt. **Milestones 1–5 (Foundation, Authentication & RBAC, SADC Truck Visits & OCR, Offline Room Sync & Conflict Resolution, and Cargo/Seals/Corrections)** are built (`v1.5.0-m5`).

## Where we are

| Area | Status |
|---|---|
| Project setup, theme (Light / Dark / System), navigation shell (M1) | Done |
| Hibiscus branding, splash, app icon (M1) | Done |
| Oracle ADB + ORDS backend (`PKG_CRYPTO`, `PKG_AUTHZ`, `PKG_AUTH`, `PKG_ADMIN`, `PKG_API`) (M2–M5) | Done |
| Server-side 6-digit email verification & Brevo transactional emails (`UTL_SMTP` / `UTL_HTTP`) (M2) | Done |
| Pre-verification account hygiene (no `users` / `auth_identities` rows until OTP is verified) (M2) | Done |
| Bootstrap Admin Security Setup gate & Self-Service Profile / Dual-OTP Email Update (M2) | Done |
| Self-registration auto-approval as `RUNNER` + immediate sign-in (M2) | Done |
| Live Admin Staff Role, Handset & Granular Permission Override Management (M2) | Done |
| Keystore-encrypted session storage, Sign Out, and live role sync (M2) | Done |
| SADC Truck Visits, Dual Front/Horse Plates, Bidirectional Auto-Refill & CameraX + ML Kit OCR (Live Scan & Photo Capture) (M3) | Done |
| Local Room Database, Offline Sync Queue, Pull-to-Refresh & Server/Device Conflict Resolution (M4) | Done |
| Driver Contact, Cargo Manifests, Arrival/Exit Seal Verification, Photo Evidence & Audit History (M5) | Done |
| Visitors, incidents, inspections, attendance, shifts, alerts, PDF reports (Milestones 6–12) | Planned |

## Decisions resolved vs. still open

1. **Backend provider (Resolved):** Oracle Autonomous Database (ADB) + Oracle REST Data Services (ORDS) + Brevo transactional email.
2. **Admin role management UI (Resolved):** Built inside the Android app on the Admin Home tab (`AdminRoleManagementCard`), backed by server-side `user.approve` permission enforcement.
3. **Brand palette:** Current code uses slate and indigo (`Color.kt`).
4. **Splash in Light mode:** Check emblem and wordmark contrast on physical devices.
5. **Staff notice wording** for app-activity monitoring (Milestone 7): Needs HR or management sign-off.

## Milestones

### 2. Authentication, account approval and permissions (Completed)
- **Built:**
  - Individual staff accounts with PBKDF2-HMAC-SHA256 (600,000 iterations) in Oracle PL/SQL.
  - Pre-signup email check (`auth/check-email` & `auth/request-signup-code`) so duplicate emails are rejected before entering a verification code, and unverified accounts never pollute `users`.
  - Default auto-approval as `RUNNER` upon email verification with automatic sign-in.
  - Bootstrap Admin Security Setup gate requiring email verification before updating initial admin credentials.
  - Roles (`ADMIN`, `SUPERVISOR`, `RUNNER`) with server-side permission checks (`PKG_AUTHZ.require_permission`), Admin role switching (`admin/users/:id/approve`), and role-adaptive UI tabs/workspaces.
  - Self-service password reset (`auth/request-password-reset` & `auth/reset-password`) and device revocation (`admin/devices/:id/revoke`).
  - Immutable `audit_events` logging actor, time, and before/after states.
  - Maximum handset allocation policy (strict limit of max 2 active handsets per staff member) enforced on the server in `PKG_AUTH.login`.
  - Per-user granular permission exceptions (`GRANT` / `REMOVE` overrides with business reason and optional expiry) backed by `user_permission_exceptions` table, ORDS endpoints, and interactive UI in the Admin Governance panel.

### 3. Truck visits and vehicles (Completed)
- **Built:**
  - Visit types: Quick, Overnight, Transit, Loading or unloading, Other (with custom purpose reason).
  - Full-page Arrival Check-In & Departure Check-Out screens with SADC country dropdown selector, Front Plate & Horse Plate with bidirectional auto-refill, and CameraX + ML Kit OCR (both Live Scan and Background Photo Capture).
  - Duplicate open-visit detection locally and during server sync (`OPEN_VISIT_EXISTS`), plus `Checked In By` and `Exited / Checked Out By` staff attribution.
  - Role-gated post-arrival and post-departure corrections with mandatory business reason and full immutable audit history (`visit_corrections`).

### 4. Local database and sync protocol (Completed)
- **Built:**
  - Room local database (`TruckParkDatabase` v2) storing visits, cargo items, seals, custom fields, correction history, and ordered sync queue operations.
  - Sticky On Yard / Departed switch & search bar with pull-to-refresh and background sync.
  - Conflict detection (`409 CONFLICT`) and in-app resolution (`Keep Device` vs `Use Server`) for Supervisors and Admins.

### 5. Driver, cargo and seal registers (Completed)
- **Built:**
  - International driver contact entry with country dial-code picker, collapsible optional cards on arrival, and reusable transporter/carrier autocomplete store.
  - Quick cargo summary plus structured multi-item cargo manifest (`CargoItem`) and custom per-category columns.
  - Arrival & Departure seal verification (`VisitSeal`) with seal numbers, types, conditions (`Intact`, `Broken`, `Tampered`, `Missing`), Camera/Gallery photo attachments, and OCR seal scanning/photo extraction.

### 6. Visitors, incidents and inspections
- Visitor register: name, purpose, arrival and departure times, badge reference, host, vehicle, optional contact, notes and photos. Works offline.
- Incidents: Runners report quickly with type, description, time, truck or location, photos.
- Incident statuses: Reported, Under Investigation, Action Required, Resolved. Runners cannot set severity, findings or close cases. Everything audited.
- Inspections: quick and detailed checklists. Each item Pass, Fail or Not Applicable. Failed items support notes, photos, follow-up actions, assignee and status. Admins manage templates; completed inspections keep the template version used.

### 7. Automatic app-activity attendance
- No manual clock-in or clock-out.
- Record meaningful work-screen use and foreground or background transitions. Mark session ends as unknown when Android cannot tell.
- Compare with shift times to flag late starts, early finishes, missing activity and unusual sessions.
- Activity is an indicator only, not proof of working hours.
- Staff can request corrections; Supervisors can add missing events. Keep originals and review outcomes.
- Transparent staff notice on what is collected, why, who sees it and how long it is kept.

### 8. Shifts and handovers
- Admins define shifts, recurring schedules, assignments and handover reminders.
- Handover summaries list unresolved incidents, critical long-stay alerts and open actions.
- Incoming staff acknowledge receipt. Acknowledging does not resolve the items.
- Unscheduled handovers supported. Drafts and acknowledgements show as pending until the server confirms.

### 9. Long-stay alerts and notifications
- Admin-set thresholds by visit type, with Warning and Critical levels.
- Alerts show truck, reference, type, arrival, elapsed time and severity. Remain active until the visit completes; history kept.
- Urgent incidents and Critical alerts need explicit acknowledgement with name and time.
- Admins set acknowledgement deadlines and escalation recipients.
- Keep delivery, display, acknowledgement and resolution as separate states. Do not claim delivery or reading without evidence.
- Android notifications only where permission and system settings allow.

### 10. Dashboards and search
- Role dashboards (initial role workspace cards and Admin role management panel are in place; live operational metrics will connect as Milestones 3–9 land):
  - **Admin:** active trucks, arrivals and departures, staff role management, incidents and critical alerts, inspection failures, open actions, sync health, admin shortcuts.
  - **Supervisor:** active visits, long-stay warnings, incidents to investigate, failed inspections, attendance exceptions, handovers.
  - **Runner:** record arrival and departure, active visits, register visitor, report incident, sync status.
- Widgets configurable per role. Configuration never grants permissions.
- Global search over registrations, references, drivers, visitors, incidents and inspections, with filters. Offline results flagged as possibly incomplete.

### 11. PDF reports
- Company-branded: name, logo, title, date range, generated date and user, page numbers, signatures where needed.
- Reports: visit history, active visits, vehicle and driver history, visitors, incidents, inspections, attendance exceptions, handovers, audit and sync.
- Offline reports state that they include only locally available records.
- No billing, payment or financial reports.

### 12. Archiving, backup and recovery
- Admins set archive rules. Archiving organizes records and never deletes them.
- Search and restore archived records; both actions audited.
- Automatic central backups. Local recovery for unsynced records. Recovery from interrupted uploads.

### 13. Security, integration and reliability testing
- Server-side authorization tests, device revocation tests, offline authentication expiry tests.
- Sync tests: retries, duplicates, conflicts, interrupted uploads.
- Photo tests: large files, failed uploads, retries without duplicates.
- Accessibility and contrast tests in both themes.
- Background-session limits on Android.
- Do not claim production readiness until critical tests pass.

### 14. Deployment and maintenance
- Backend configuration and secrets handling.
- Database schema and migration strategy.
- Build, release and signing steps.
- Retention policies for personal data and audit events.
- Maintenance runbook.

## Known gaps in the current code

- Shrinking and obfuscation are off for release builds. Turn on before any release.
- Blur is layered gradients only; no real backdrop blur.
- Light-mode splash artwork contrast should be verified on target handsets.
- Splash shows on every cold start.
- Operational tabs (Visits, Registers, Reports) show role-aware workspace previews until Milestones 3–11 are implemented.

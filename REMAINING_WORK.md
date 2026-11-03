# Remaining Work

This file lists what has been completed and what the app still needs, based on the master development prompt. **Milestone 1 (Foundation)** and **Milestone 2 (Authentication, Verification, Roles & Admin Access Control)** are built.

## Where we are

| Area | Status |
|---|---|
| Project setup, theme (Light / Dark / System), navigation shell | Done |
| Hibiscus branding, splash, app icon | Done |
| Oracle ADB + ORDS backend (`PKG_CRYPTO`, `PKG_AUTHZ`, `PKG_AUTH`, `PKG_ADMIN`, `PKG_API`) | Done |
| Server-side 6-digit email verification & Brevo transactional emails (`UTL_SMTP` / `UTL_HTTP`) | Done |
| Pre-verification account hygiene (no `users` / `auth_identities` rows until OTP is verified) | Done |
| Bootstrap Admin Security Setup gate (`admin@hibiscuss.com` / `must_change_password`) | Done |
| Self-registration auto-approval as `RUNNER` + immediate sign-in | Done |
| Live Admin Staff Role Management (`RUNNER`, `SUPERVISOR`, `ADMIN`) & Role-Adaptive UI | Done |
| Keystore-encrypted session storage, Sign Out, and live role sync | Done |
| Truck visits, registers, local Room sync, attendance, alerts, PDF reports (Milestones 3–12) | Not started |

## Decisions resolved vs. still open

1. **Backend provider (Resolved):** Oracle Autonomous Database (ADB) + Oracle REST Data Services (ORDS) + Brevo transactional email.
2. **Admin role management UI (Resolved):** Built inside the Android app on the Admin Home tab (`AdminRoleManagementCard`), backed by server-side `user.approve` permission enforcement.
3. **Brand palette:** Current code uses slate and indigo (`Color.kt`).
4. **Splash in Light mode:** Check emblem and wordmark contrast on physical devices.
5. **Staff notice wording** for app-activity monitoring (Milestone 7): Needs HR or management sign-off.

## Milestones

### 2. Authentication, account approval and permissions (Core Built)
- **Built:**
  - Individual staff accounts with PBKDF2-HMAC-SHA256 (600,000 iterations) in Oracle PL/SQL.
  - Pre-signup email check (`auth/check-email` & `auth/request-signup-code`) so duplicate emails are rejected before entering a verification code, and unverified accounts never pollute `users`.
  - Default auto-approval as `RUNNER` upon email verification with automatic sign-in.
  - Bootstrap Admin Security Setup gate requiring email verification before updating initial admin credentials.
  - Roles (`ADMIN`, `SUPERVISOR`, `RUNNER`) with server-side permission checks (`PKG_AUTHZ.require_permission`), Admin role switching (`admin/users/:id/approve`), and role-adaptive UI tabs/workspaces.
  - Self-service password reset (`auth/request-password-reset` & `auth/reset-password`) and device revocation (`admin/devices/:id/revoke`).
  - Immutable `audit_events` logging actor, time, and before/after states.
- **Remaining in Milestone 2:**
  - Per-user maximum device count enforcement UI and Admin per-person permission exception UI (`user_permission_exceptions` table is already in `schema.sql`).

### 3. Truck visits and vehicles
- Visit types: Quick, Overnight, Transit, Loading or unloading, Other.
- Arrival: search or create a vehicle, record details, generate a unique visit reference, save locally.
- Warn on an existing open visit for the same truck. Re-check on the server during sync.
- Departure: record the departure time, notes, and mark the visit complete.
- Corrections: Runners may fix their own arrival details before departure. After departure, critical fields need a Supervisor or Admin and a recorded reason. Keep original values and full history.
- Never invent or silently overwrite timestamps.

### 4. Local database and sync protocol
- Room as the local database. The UI reads only local data.
- Device-generated unique IDs for every record.
- Sync queue: save locally, queue the operation, show pending status, sync when online, validate on the server, process idempotently, confirm, update status.
- Sync order: visits and departures, visitors, incidents, other records, then photos and large files.
- Conflicts: merge non-overlapping field edits; flag clashing edits; never silently overwrite truck identity, timestamps or visit status; keep both versions; Supervisor or Admin resolves; audit the resolution.
- Show pending, syncing, synced, conflicted and failed states, plus last successful sync time.
- Reports warn when data from other devices may be missing.

### 5. Driver, cargo and seal registers
- Reusable driver profiles with quick entry. Driver details optional.
- Quick cargo description on a visit, plus optional structured cargo records.
- Seals: numbers, types, condition, multiple per visit, photos for damage or loss.
- Operational only. No cargo value, charges or payments.

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

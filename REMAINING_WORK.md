# Remaining Work

This file lists what the app still needs, based on the master development prompt. Milestone 1 (foundation) is complete. Everything else below is not built yet.

## Where we are

| Area | Status |
|---|---|
| Project setup, theme (Light / Dark / System), navigation shell | Done |
| Hibiscus branding, splash, app icon | Done |
| Placeholder screens for Home, Visits, Registers, Reports | Done (no data) |
| Anything that stores, syncs or authorizes data | Not started |

## Decisions needed first

These block later milestones. Decide them before coding past Milestone 1.

1. **Backend provider.** Options to compare: Supabase, a custom PostgreSQL API, or another managed service. Evaluate authentication, PostgreSQL, a secure API and file storage. Do not assume the backend solves offline sync; the sync protocol must be designed explicitly.
2. **Brand palette.** Confirm the final colours. The current code uses slate and indigo. The earlier brass and serif direction is kept on `classic-ui` only.
3. **Splash in Light mode.** Check the emblem and wordmark artwork on the light splash. A light variant may be needed.
4. **Staff notice wording** for app-activity monitoring (Milestone 7). Needs HR or management sign-off.

## Milestones

### 2. Authentication, account approval and permissions
- Individual staff accounts only (no shared accounts).
- Roles: Admin, Supervisor, Runner, with role defaults and per-person exceptions.
- Account requests: Pending Submission, Pending Approval, Approved, Rejected. Applicants cannot approve themselves.
- Device limits per staff member, with Admin exceptions and device revocation.
- Offline sign-in with an Admin-set validity period. Online revalidation of accounts, devices and roles.
- Server-side permission checks on every action. Hiding a button is not authorization.
- Audit trail for permission changes (actor, time, reason, before and after).

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
- Role dashboards:
  - **Admin:** active trucks, arrivals and departures, pending account requests, incidents and critical alerts, inspection failures, open actions, sync health, admin shortcuts.
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
- Light-mode splash artwork is unchecked.
- Splash shows on every cold start.
- Hard-coded colours outside the theme file (bottom bar, splash).
- No unit tests beyond the theme logic.
- Nothing has been run on a device by anyone other than the project owner.

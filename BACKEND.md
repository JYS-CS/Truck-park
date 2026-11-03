# Backend Architecture (Oracle ADB + ORDS)

Status: **Phase 1 (Identity, Verification, Email Dispatch & RBAC) Built.** Operations & Sync (§6–§7) are planned for Milestones 3–4.

## 0. What changed from the earlier draft

- No custom API server. The phone app talks only to **ORDS** (Oracle REST Data Services) on the Autonomous Database.
- No APEX. Admin role management screens are built directly inside the Android app and gated by `ADMIN` role permissions.
- Business rules, permissions and sync logic live in **PL/SQL packages** behind ORDS (`PKG_CRYPTO`, `PKG_AUTHZ`, `PKG_AUTH`, `PKG_ADMIN`, `PKG_API`).
- Sessions use our own opaque tokens stored in the database, not JWTs, so an Admin can revoke a device or update a role instantly.
- Password hashing lives in the database layer (`PBKDF2-HMAC-SHA-256`, 600,000 iterations).
- Email sending runs directly from PL/SQL (`PKG_API`) using Oracle's default wallet via Brevo SMTP Relay (`UTL_SMTP` STARTTLS on port 587) or Brevo REST API (`UTL_HTTP` on port 443), while recording audit/outbox rows in `email_outbox`. No SMTP credentials exist in the Android client.

## 1. Overview

```
Android app ──HTTPS/JSON──▶ ORDS modules ──▶ PL/SQL packages (PKG_API / PKG_AUTH) ──▶ Oracle Autonomous DB
Admin screens (in-app) ───▶ (same ORDS modules, role-checked)                            │
                                                                                         │ email_outbox + UTL_SMTP/UTL_HTTP
Brevo (transactional email) ◀──────────────HTTPS / STARTTLS──────────────────────────────┘
```

- The app never connects to the database directly and never reads tables. It calls ORDS modules only.
- ORDS handlers are thin: each one calls one PL/SQL procedure in `PKG_API_HTTP` / `PKG_API`. All permission checks, device checks and business rules run in the database.
- Admin screens live inside the Android app (`AdminRoleManagementCard` on the Home tab for `ADMIN` users) and every action is enforced on the server via `PKG_AUTHZ.require_permission`.

## 2. Database layout and tooling

- **Platform:** one Oracle Autonomous Database (ADB) per environment: `dev` and `prod`. ORDS is enabled on the schema.
- **Schemas (least privilege):**
  - `TP_OWNER`: owns all tables, views and packages. No direct login is used by the app.
  - `TP_API`: the ORDS schema. Has `EXECUTE` on the API packages only. It has no direct access to tables.
  - `TP_MAILER`: the email sender. Can read and update `email_outbox` only, and call one package to claim rows.
- **Autorest:** off for every table. Tables are never exposed directly through ORDS, so all writes go through the API packages.
- **Migrations:** versioned SQL scripts applied in order with Flyway or Liquibase. Nothing is changed by hand in prod.
- **Tests:** PL/SQL unit tests with utPLSQL, run on `dev` before each prod release.
- **Packages:**
  - `PKG_CRYPTO`: hashing and token generation.
  - `PKG_AUTHZ`: `require(permission)` checks, called at the top of every API procedure.
  - `PKG_AUTH`: invites, login, verification, password reset, sessions.
  - `PKG_VISIT`: arrival, departure, corrections, open-visit rules.
  - `PKG_SYNC`: push and pull.
  - `PKG_ADMIN`: approvals, roles, devices, audit reads.
  - `PKG_EMAIL`: writes to `email_outbox` and claims rows for the sender.

## 3. Sign-in, Self-Registration & Verification (phase 1: email and password)

> **Detailed Specification:** For the complete, standalone architecture and schema for authentication, sessions, devices, and RBAC, see [IDENTITY_AND_ACCESS.md](file:///sdcard/project/TruckPark/IDENTITY_AND_ACCESS.md).

- **Self-Registration with Pre-Verification Hygiene:**
  1. When a user presses **Create Account**, the app calls `POST /auth/request-signup-code` with their email and full name.
  2. `PKG_API.request_signup_code` checks `users` first and immediately returns `409 ALREADY_EXISTS` if the email is already registered (before advancing to the 6-digit code screen).
  3. If available, the server generates a 6-digit code via `DBMS_CRYPTO.RANDOMBYTES`, stores only its SHA-256 hash and 15-minute expiry in `email_outbox` (`template = 'VERIFY_SIGNUP'`), and dispatches the email via Brevo. **Nothing is written to `users` or `auth_identities` until the code is verified.**
  4. When the user submits the 6-digit code to `POST /auth/register`, `PKG_API.register` validates the hash, creates the `users` row (`status = 'APPROVED'`, `email_verified_at = SYSTIMESTAMP`, `phone`), creates the `auth_identities` row, and assigns the default `RUNNER` role so the user is automatically signed in.
- **Admin Role Management & Invites:**
  - Admins can view all staff via `GET /admin/users` and update any user's role (`RUNNER`, `SUPERVISOR`, `ADMIN`) via `POST /admin/users/{id}/approve`.
  - Admins can also issue single-use 72-hour email invitations (`POST /admin/invites` → `POST /auth/accept-invite`).
- **Bootstrap Admin Security Setup Gate:**
  - The initial seeded admin (`admin@hibiscuss.com` / `@Admin`) has `must_change_password = 1`.
  - Signing in redirects to the Admin Security Setup screen, which sends a 6-digit code to the new admin email (`POST /auth/send-verification-code`) and verifies it on the server (`POST /auth/change-password`) before updating `users.email` or `auth_identities`.
- Passwords are hashed with **PBKDF2-HMAC-SHA-256**, 600,000 iterations, a 16-byte random salt per user, implemented in `PKG_CRYPTO` with `DBMS_CRYPTO.MAC`. Minimum 8 characters with uppercase, lowercase, digit, and special character. Never logged, never returned by any endpoint.
- Login lockout: 5 failed attempts per account in 15 minutes, then locked for 15 minutes. Counters live in `auth_identities`, so they work across all devices.
- Password reset: single-use 6-digit code (`POST /auth/request-password-reset` & `POST /auth/reset-password`), expires after 15 minutes. On success, every session and refresh token for that user is revoked.
- Per-IP rate limiting is not built into ORDS. Add an OCI WAF rate rule on `/auth/*` (see §10).
- Google sign-in can be added later as a second row in `auth_identities`. `users` does not change.

### Sessions and tokens

- **Access token:** opaque random 256-bit value, returned once, stored only as a SHA-256 hash in `sessions`. Lifetime 1 hour.
- **Every ORDS call** that needs sign-in is checked in `PKG_AUTHZ`: the session is not expired or revoked, the device is not revoked, the user is `APPROVED`, and the user's `permission_version` has not changed. If it has changed, the call returns `401 SESSION_STALE` and the app refreshes.
- **Refresh token:** opaque random 256-bit value, 30 days, stored as a hash in `refresh_tokens`. Rotated on every use. Reusing an old refresh token revokes the whole device.
- **Device binding:** every session and refresh token belongs to one device. Revoking a device ends its sessions immediately.
- **Why not ORDS's built-in OAuth:** ORDS tokens cannot be revoked on demand by our code. Checking our own sessions gives instant revocation and a clear audit trail. ORDS OAuth can be revisited later if third parties need access.
- **Offline window:** the login response includes `offline_valid_until` (default 72 hours, Admin-configurable), signed with an HMAC key from the secret store (§9). The app stores it encrypted with the Android Keystore. Offline work continues until that time, then the app needs a fresh sign-in before syncing new actions. Local records are kept either way.
- The app checks `offline_valid_until` against the server time it receives on each sync. A device clock change alone cannot extend the window.

## 4. Roles and permissions

- Roles: `ADMIN`, `SUPERVISOR`, `RUNNER`.
- Permissions are named strings, for example `visit.arrive`, `visit.depart`, `visit.correct_critical`, `visit.override_open`, `user.approve`, `incident.close`.
- `role_permissions` holds the defaults for each role.
- `user_permission_exceptions` holds Admin-approved individual grants or removals, each with a reason and an optional expiry.
- Every API procedure calls `PKG_AUTHZ.require('<permission>')` first. A missing permission raises `NOT_AUTHORIZED`, and nothing is written.
- Any change to a role, an exception or a user's status increments `users.permission_version` and writes to `audit_events` with before and after values.

## 5. Database objects

Conventions: IDs are `VARCHAR2(36)` UUIDs, generated on the device for device-created rows and in the database otherwise. Times are `TIMESTAMP WITH TIME ZONE` stored in UTC. Every mutable table has `created_at`, `updated_at` and `row_version NUMBER` for optimistic concurrency. Nothing is deleted; archiving uses `archived_at`. JSON payloads use the native `JSON` type where the ADB version supports it, otherwise `CLOB` with a `IS JSON` check.

### Identity and access
```sql
users (
  id                 VARCHAR2(36) PRIMARY KEY,
  full_name          VARCHAR2(200) NOT NULL,
  email              VARCHAR2(320) NOT NULL UNIQUE,  -- stored lower-case
  email_verified_at  TIMESTAMP WITH TIME ZONE,
  status             VARCHAR2(30) NOT NULL,          -- PENDING_VERIFICATION, PENDING_APPROVAL, APPROVED, REJECTED, DEACTIVATED
  deactivate_at      TIMESTAMP WITH TIME ZONE,       -- scheduled deactivation
  permission_version NUMBER DEFAULT 1 NOT NULL,
  created_at, updated_at, row_version
)

auth_identities (
  id              VARCHAR2(36) PRIMARY KEY,
  user_id         VARCHAR2(36) NOT NULL REFERENCES users(id),
  provider        VARCHAR2(20) NOT NULL,             -- PASSWORD now, GOOGLE later
  password_hash   VARCHAR2(255),                     -- PBKDF2 output, PASSWORD only
  password_salt   RAW(16),
  password_iters  NUMBER,                            -- stored so the count can be raised later
  external_id     VARCHAR2(255),                     -- Google 'sub', later
  failed_attempts NUMBER DEFAULT 0 NOT NULL,
  locked_until    TIMESTAMP WITH TIME ZONE,
  CONSTRAINT auth_provider_ext UNIQUE (provider, external_id)
)

invites (
  id            VARCHAR2(36) PRIMARY KEY,
  email         VARCHAR2(320) NOT NULL,
  proposed_role VARCHAR2(30) NOT NULL,
  token_hash    VARCHAR2(64) NOT NULL UNIQUE,        -- SHA-256 of the link token
  created_by    VARCHAR2(36) NOT NULL REFERENCES users(id),
  expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at       TIMESTAMP WITH TIME ZONE
)

one_time_codes (                                    -- verification and reset
  id          VARCHAR2(36) PRIMARY KEY,
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  purpose     VARCHAR2(20) NOT NULL,                -- VERIFY_EMAIL, RESET_PASSWORD
  code_hash   VARCHAR2(64) NOT NULL,
  attempts    NUMBER DEFAULT 0 NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE
)

devices (
  id            VARCHAR2(36) PRIMARY KEY,           -- generated on the device, sent at registration
  user_id       VARCHAR2(36) NOT NULL REFERENCES users(id),
  name          VARCHAR2(200),                      -- for example "Gate phone, Samsung A54"
  registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
  last_seen_at  TIMESTAMP WITH TIME ZONE,
  revoked_at    TIMESTAMP WITH TIME ZONE,
  revoked_by    VARCHAR2(36),
  revoke_reason VARCHAR2(500)
)

sessions (
  id          VARCHAR2(36) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,           -- SHA-256 of the access token
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,      -- now + 1 hour
  revoked_at  TIMESTAMP WITH TIME ZONE
)

refresh_tokens (
  id          VARCHAR2(36) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,      -- now + 30 days
  rotated_at  TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE
)

roles (code VARCHAR2(30) PRIMARY KEY, name VARCHAR2(100) NOT NULL)
user_roles (user_id, role_code, assigned_by, assigned_at, PRIMARY KEY (user_id, role_code))
role_permissions (role_code, permission_code, PRIMARY KEY (role_code, permission_code))
user_permission_exceptions (
  id VARCHAR2(36) PRIMARY KEY, user_id VARCHAR2(36) NOT NULL, permission_code VARCHAR2(60) NOT NULL,
  grant_or_remove VARCHAR2(6) NOT NULL,               -- GRANT, REMOVE
  reason VARCHAR2(500) NOT NULL, approved_by VARCHAR2(36), approved_at TIMESTAMP WITH TIME ZONE,
  expires_at TIMESTAMP WITH TIME ZONE
)
```

### Operations
```sql
vehicles (
  id                VARCHAR2(36) PRIMARY KEY,
  registration      VARCHAR2(20) NOT NULL,            -- as written
  registration_norm VARCHAR2(20) NOT NULL UNIQUE,     -- upper-case, no spaces
  make VARCHAR2(60), model VARCHAR2(60), notes VARCHAR2(1000),
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at, updated_at, row_version
)

drivers (
  id VARCHAR2(36) PRIMARY KEY, full_name VARCHAR2(200) NOT NULL,
  licence_ref VARCHAR2(60), phone VARCHAR2(40), company VARCHAR2(200),
  created_at, updated_at, row_version
)

visits (
  id                VARCHAR2(36) PRIMARY KEY,         -- device-generated
  reference         VARCHAR2(30) NOT NULL UNIQUE,     -- for example TP-261027-0042, assigned by the server on sync
  vehicle_id        VARCHAR2(36) NOT NULL REFERENCES vehicles(id),
  driver_id         VARCHAR2(36) REFERENCES drivers(id),
  visit_type        VARCHAR2(30) NOT NULL,            -- QUICK, OVERNIGHT, TRANSIT, LOADING_UNLOADING, OTHER
  status            VARCHAR2(20) NOT NULL,            -- OPEN, COMPLETED
  arrived_at        TIMESTAMP WITH TIME ZONE NOT NULL, -- device time, never silently changed
  arrived_by        VARCHAR2(36) NOT NULL REFERENCES users(id),
  arrival_device_id VARCHAR2(36) NOT NULL,
  departed_at       TIMESTAMP WITH TIME ZONE,
  departed_by       VARCHAR2(36) REFERENCES users(id),
  open_vehicle_key  VARCHAR2(36),                     -- = vehicle_id while OPEN, NULL when completed
  trailer_ref VARCHAR2(60), transport_company VARCHAR2(200),
  cargo_description VARCHAR2(1000), seal_numbers VARCHAR2(1000),
  notes             VARCHAR2(2000),
  created_at, updated_at, row_version,
  CONSTRAINT one_open_visit_per_vehicle UNIQUE (open_vehicle_key)
)
```
The `UNIQUE (open_vehicle_key)` constraint is the database-level guard against two open visits for one truck. Oracle allows many NULLs in a unique index, so completed visits do not clash. An authorized override (§6) is recorded on the visit, not by dropping the constraint.

```sql
visit_corrections (
  id VARCHAR2(36) PRIMARY KEY, visit_id VARCHAR2(36) NOT NULL REFERENCES visits(id),
  field_name VARCHAR2(60) NOT NULL, old_value CLOB, new_value CLOB,
  reason VARCHAR2(500) NOT NULL,
  corrected_by VARCHAR2(36) NOT NULL REFERENCES users(id),
  corrected_at TIMESTAMP WITH TIME ZONE NOT NULL
)

sync_operations (                                    -- idempotency and audit of every upload
  op_id         VARCHAR2(36) PRIMARY KEY,            -- from the device; a repeat is a no-op
  device_id     VARCHAR2(36) NOT NULL REFERENCES devices(id),
  user_id       VARCHAR2(36) NOT NULL REFERENCES users(id),
  entity_type   VARCHAR2(40) NOT NULL,
  entity_id     VARCHAR2(36) NOT NULL,
  operation     VARCHAR2(20) NOT NULL,               -- CREATE, UPDATE, DEPART, CORRECT
  payload       CLOB NOT NULL,
  result        VARCHAR2(20) NOT NULL,               -- APPLIED, REJECTED, CONFLICT
  result_detail VARCHAR2(500),
  received_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  server_seq    NUMBER GENERATED ALWAYS AS IDENTITY  -- cursor for pulls
)

sync_conflicts (
  id VARCHAR2(36) PRIMARY KEY, op_id VARCHAR2(36) NOT NULL REFERENCES sync_operations(op_id),
  entity_type VARCHAR2(40), entity_id VARCHAR2(36), field_name VARCHAR2(60),
  local_value CLOB, server_value CLOB,
  status VARCHAR2(20) NOT NULL,                        -- OPEN, RESOLVED_LOCAL, RESOLVED_SERVER, RESOLVED_MERGED
  resolved_by VARCHAR2(36), resolved_at TIMESTAMP WITH TIME ZONE, resolution_note VARCHAR2(500)
)

photos (
  id VARCHAR2(36) PRIMARY KEY, owner_entity VARCHAR2(40) NOT NULL, owner_id VARCHAR2(36) NOT NULL,
  storage_key VARCHAR2(500),                           -- OCI Object Storage object name
  content_sha256 VARCHAR2(64) NOT NULL,                -- dedupes retried uploads
  captured_at TIMESTAMP WITH TIME ZONE, captured_by VARCHAR2(36),
  upload_status VARCHAR2(20) NOT NULL                  -- PENDING, UPLOADED, FAILED
)
```
Photos are stored in OCI Object Storage, not in the database. The app uploads through a short-lived pre-authenticated request issued by the API, and only the metadata row lives in Oracle.

### Email and audit
```sql
email_outbox (
  id           VARCHAR2(36) PRIMARY KEY,
  template     VARCHAR2(40) NOT NULL,                  -- INVITE, VERIFY_EMAIL, RESET_PASSWORD, APPROVED, REJECTED, DEVICE_REVOKED, ...
  to_address   VARCHAR2(320) NOT NULL,
  payload      CLOB NOT NULL,                          -- template variables, JSON; never a password or a raw token
  status       VARCHAR2(20) NOT NULL,                  -- QUEUED, SENDING, SENT, FAILED
  attempts     NUMBER DEFAULT 0 NOT NULL,
  next_try_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  last_error   VARCHAR2(500),
  brevo_message_id VARCHAR2(100),
  created_at, updated_at, row_version
)

audit_events (
  id              VARCHAR2(36) PRIMARY KEY,
  actor_user_id   VARCHAR2(36), actor_device_id VARCHAR2(36),
  action          VARCHAR2(60) NOT NULL,
  target_type     VARCHAR2(40), target_id VARCHAR2(36),
  before_json     CLOB, after_json CLOB, reason VARCHAR2(500),
  occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
)
```
`audit_events` is append-only. `TP_API` has `INSERT` and `SELECT` on it only, and a trigger rejects `UPDATE` and `DELETE` for every user.

## 6. Visit business rules

- **Arrival:** `PKG_VISIT` checks for an open visit on the same vehicle. If one exists, the arrival is rejected with `OPEN_VISIT_EXISTS`. A user with `visit.override_open` may create it anyway, but must give a reason. The override is written to `audit_events`, and the new visit is flagged `OVERRIDE_OPEN`.
- **Departure:** no approval needed. The departure time is the device time at save. The server stores it and never changes it.
- **Corrections before departure:** a Runner can fix their own arrival details.
- **Corrections after departure:** critical fields (vehicle, arrival time, departure time, status) need `visit.correct_critical` and a reason. Each change adds a row to `visit_corrections`.
- **Each returning truck gets a new visit row.** Visits are never reopened.

## 7. Sync protocol

Both calls are ORDS modules. Each one passes the body or query string to a single `PKG_SYNC` procedure.

**Upload:** `POST /sync/push`, batched, at most 50 operations per request:
```json
{ "device_id": "…", "operations": [
  { "op_id": "uuid", "entity": "visit", "entity_id": "uuid",
    "operation": "CREATE", "base_row_version": null, "payload": { … } }
]}
```
- Operations run **in order**. Each one is first checked against `sync_operations`. If the `op_id` already exists, the stored result is returned and nothing else runs.
- Each operation runs in its own transaction, with a savepoint. One failure does not roll back the others.
- Result per operation: `APPLIED`, `DUPLICATE`, `REJECTED` (with a reason code), or `CONFLICT` (with the conflicting fields).
- Changes to critical fields with a stale `base_row_version` become conflicts. Other field changes merge automatically.
- If the user lacks the permission, or the device is revoked, the operation is `REJECTED` with `NOT_AUTHORIZED`.

**Pull:** `GET /sync/pull?cursor=…&limit=200` returns rows with `server_seq` greater than the cursor, so each device catches up after reconnecting.

**Order:** the app sends visits and departures first, then visitors, incidents and other records, and photo metadata last.

**Retries:** exponential backoff, capped at 10 minutes. The same `op_id` is reused, so a retry is always safe.

## 8. ORDS endpoints (phase 1 — built)

Base path: `https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/admin/v1/`

| Method | Path | Sign-in | Purpose |
|---|---|---|---|
| POST | `/auth/login` | no | Email and password; returns session, refresh token, `offline_valid_until`, and `must_change_password` |
| POST | `/auth/refresh` | refresh token | Rotate tokens |
| POST | `/auth/check-email` | no | Check if email is already registered (`409 ALREADY_EXISTS` if taken) |
| POST | `/auth/request-signup-code` | no | Check email availability, generate 6-digit code in `email_outbox`, and send via Brevo |
| POST | `/auth/register` | no | Verify 6-digit signup code and create `APPROVED` account with default `RUNNER` role |
| POST | `/auth/accept-invite` | no | Set password from invite link |
| POST | `/auth/verify-email` | no | Submit 6-digit code for invite-based registration |
| POST | `/auth/send-verification-code` | yes | Send 6-digit verification code for Admin Security Setup |
| POST | `/auth/change-password` | yes | Verify 6-digit code and update Admin email & password |
| POST | `/auth/request-password-reset` | no | Send 6-digit password reset code via Brevo (always returns 200 for valid email format) |
| POST | `/auth/reset-password` | no | Verify reset code, set new password, revoke all active sessions |
| POST | `/auth/logout` | yes | Revoke current session |
| GET | `/me` | yes | Current user, status, roles, permissions, `must_change_password` |
| POST | `/admin/invites` | yes, `user.invite` | Create invite and email link via Brevo |
| GET | `/admin/users?status=` | yes, `user.approve` | List staff accounts (`ALL` by default, or filtered by status) with role and phone |
| POST | `/admin/users/{id}/approve` | yes, `user.approve` | Approve user or update role (`RUNNER`, `SUPERVISOR`, `ADMIN`) and notify via Brevo |
| POST | `/admin/users/{id}/reject` | yes, `user.approve` | Reject with reason and notify via Brevo |
| POST | `/admin/users/{id}/deactivate` | yes, `user.deactivate` | Immediate or scheduled deactivation |
| POST | `/admin/devices/{id}/revoke` | yes, `device.manage` | Revoke device and its sessions |
| POST | `/sync/push` | yes | Batched operations (planned for Milestone 4) |
| GET | `/sync/pull` | yes | Changes since cursor (planned for Milestone 4) |

Every response includes a `request_id`. Errors use one format: `{ "code": "...", "message": "...", "request_id": "..." }`, with the matching HTTP status. Each handler wraps its PL/SQL call so unexpected errors become `INTERNAL_ERROR` without leaking SQL text.

## 9. Email and secrets

**In-Database PL/SQL Sender (`PKG_API`):**
1. PL/SQL writes a row to `email_outbox` with status `QUEUED` and template metadata. Raw verification codes and passwords are never stored in `email_outbox.payload` (only SHA-256 hashes where verification is needed).
2. `PKG_API` immediately dispatches the email from Oracle Autonomous Database using its built-in TLS wallet:
   - If `C_SMTP_KEY` starts with `xsmtpsib-` and `C_SMTP_LOGIN` is configured, it sends via Brevo SMTP Relay (`smtp-relay.brevo.com:587` with `UTL_SMTP.STARTTLS`).
   - If `C_SMTP_KEY` starts with `xkeysib-`, it posts to the Brevo HTTPS REST API (`https://api.brevo.com/v3/smtp/email` via `UTL_HTTP`).
3. On success, `email_outbox.status` is updated to `SENT`; on error, `FAILED` and `last_error` are recorded for audit inspection.
4. Network ACLs for `smtp-relay.brevo.com` (port 587) and `api.brevo.com` (port 443) are configured automatically in `backend/ords/02_ords_module.sql`.

**Templates:** invite, verify email (code), account request received, approved (with role), rejected (with reason), password reset (code), device revoked (security notice).

**Sender address:** `noreply@<your-domain>`, with SPF, DKIM and DMARC records set up before go-live.

**Secrets the system needs:**
- Brevo API key
- HMAC key for the offline-window signature
- Database password for `TP_MAILER`, if the sender connects to the database
- ORDS and Oracle connection details (not secret by themselves)

**[DECIDE] where secrets live.** Recommendation: **OCI Vault**. The OCI Function reads secrets through its resource principal, so no database password has to be stored in the function's configuration. Storing credentials in a database table is possible, but whatever reads that table needs its own login, and that login becomes the secret that must be protected. Either way, the app never receives any secret, and no secret goes into this repository.

## 10. Security and operations

- **TLS:** ORDS is HTTPS only on ADB. Add a custom domain through an OCI load balancer or WAF only if needed.
- **Rate limits:** ORDS has no built-in per-IP limit. Add an OCI WAF rule on `/auth/*`. Per-account lockout is already in the database (§3).
- **Least privilege:** `TP_OWNER` has no login used by the app. `TP_API` has `EXECUTE` on API packages only. `TP_MAILER` touches `email_outbox` only.
- **Logs:** never record passwords, tokens, codes or request bodies that contain them. Keep the request id, user id, endpoint and result. ORDS access logs go to OCI Logging.
- **Backups:** ADB automatic backups. Confirm the retention for the chosen tier, and test a restore before go-live.
- **Retention:** **[DECIDE]** how long to keep audit events, visits and email outbox rows.
- **Deployment:** `dev` and `prod` ADB instances. Migrations run on `dev` first, then `prod`. Health check: a `GET /health` ORDS module that checks the database connection.
- **Accounts:** the first Admin is created by a database script run once by you, not through the app.

## 11. Limits to verify before building

I have not checked these against current Oracle documentation. Confirm them first:
- Always Free or paid ADB tier: storage, OCPU, and whether ORDS and outbound calls are included.
- ORDS module, handler and body-binding syntax for the ORDS version on your ADB.
- Native `JSON` type availability on your ADB version.
- Oracle's email delivery allowance, if it is ever used instead of Brevo.
- Brevo free-tier limits (300 emails per day at last check).
- `DBMS_CRYPTO` PBKDF2 timing at 600,000 iterations on your tier.

## 12. Open decisions

1. Admin screens: inside the Android app, or a separate web page?
2. Email and signing secrets: OCI Vault (recommended) or the database?
3. Password hashing: PBKDF2 in PL/SQL (proposed) or hashing outside the database?
4. Invite and code lifetimes (proposed 72 hours and 15 minutes).
5. Offline validity window (proposed 72 hours).
6. Data retention periods for audit, visits and email outbox.
7. Whether overdue visits need automatic alerts in phase 1 (Milestone 9).
8. Whether a single truck park needs a `sites` table now, or later.
9. ADB tier: Always Free for development, and which tier for production.

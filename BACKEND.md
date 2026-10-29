# Backend Plan (Draft for Review)

Status: **proposal, not built.** Decisions marked **[DECIDE]** need your sign-off before implementation.

## 1. Overview

```
Android app ──HTTPS/JSON──▶ API service ──JDBC──▶ Oracle Autonomous DB
                              │
                              └──HTTPS──▶ Brevo (transactional email)
```

- The app never talks to the database or to Brevo directly.
- The API is the only place that enforces permissions, device limits, approvals and business rules.
- Hosting: an Oracle Cloud VM running the API behind Nginx, with TLS from Let's Encrypt, on `api.<your-domain>`.
- **[DECIDE]** API language: Kotlin with Spring Boot (proposed, shares language with the app and has solid Oracle JDBC support) or Node.js with TypeScript.

## 2. Sign-in (phase 1: email and password only)

- Sign-up is by **invite only**. An Admin creates an invite for an email address and a proposed role.
- The invite email contains a single-use link that expires after 72 hours (**[DECIDE]** duration).
- The person sets a password and verifies their email (6-digit code, expires after 15 minutes, max 5 attempts).
- The account then enters **Pending Approval** until an Admin confirms the role.
- Passwords: Argon2id hash. Minimum 12 characters. Never logged, never returned by any endpoint.
- Login rate limit: 5 failed attempts per email per 15 minutes, then a 15-minute lock. Failures are logged without the password.
- Password reset: single-use link or code, expires after 15 minutes, all existing sessions revoked on success.
- Google sign-in is added later as a second row in `auth_identities`. No change to `users` is needed.

### Tokens and sessions
- **Access token:** signed JWT, 15-minute lifetime, carries user id, device id, and the permission version number.
- **Refresh token:** random 256-bit value, returned once, stored only as a hash in `refresh_tokens`. Rotated on each use. Reuse of an old token revokes the whole device.
- **Device binding:** each refresh token belongs to one device. Revoking a device invalidates its tokens immediately.
- **Offline window:** the server issues a signed "offline valid until" timestamp (default 72 hours, Admin-configurable). The app stores it encrypted with the Android Keystore. Offline work continues until it passes, then sign-in is required before new actions sync. Local records are kept either way.

## 3. Roles and permissions

- Roles: `ADMIN`, `SUPERVISOR`, `RUNNER`.
- Permissions are named strings, for example `visit.arrive`, `visit.depart`, `visit.correct_critical`, `user.approve`, `incident.close`.
- `role_permissions` holds the defaults for each role.
- `user_permission_exceptions` holds Admin-approved individual grants or removals, each with a reason.
- Every check happens on the server. The app's buttons are cosmetic only.
- Each change to a role or exception is audited with before and after values.

## 4. Database tables (Oracle)

Conventions: IDs are `VARCHAR2(36)` UUIDs generated on the device or server. Times are `TIMESTAMP WITH TIME ZONE`, stored in UTC. Every mutable table has `created_at`, `updated_at` and `row_version NUMBER` for optimistic concurrency. Nothing is deleted; archiving uses `archived_at`.

### Identity and access
```sql
users (
  id                VARCHAR2(36) PRIMARY KEY,
  full_name         VARCHAR2(200) NOT NULL,
  email             VARCHAR2(320) NOT NULL UNIQUE,   -- stored lower-case
  email_verified_at TIMESTAMP WITH TIME ZONE,
  status            VARCHAR2(30) NOT NULL,           -- PENDING_VERIFICATION, PENDING_APPROVAL, APPROVED, REJECTED, DEACTIVATED
  deactivate_at     TIMESTAMP WITH TIME ZONE,        -- scheduled deactivation
  permission_version NUMBER DEFAULT 1 NOT NULL,      -- bumped on any role or exception change
  created_at, updated_at, row_version
)

auth_identities (
  id          VARCHAR2(36) PRIMARY KEY,
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  provider    VARCHAR2(20) NOT NULL,                  -- PASSWORD now, GOOGLE later
  password_hash VARCHAR2(255),                        -- Argon2id, PASSWORD only
  external_id VARCHAR2(255),                          -- Google 'sub', later
  failed_attempts NUMBER DEFAULT 0 NOT NULL,
  locked_until TIMESTAMP WITH TIME ZONE,
  UNIQUE (provider, external_id)
)

invites (
  id          VARCHAR2(36) PRIMARY KEY,
  email       VARCHAR2(320) NOT NULL,
  proposed_role VARCHAR2(30) NOT NULL,
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,           -- SHA-256 of the link token
  created_by  VARCHAR2(36) NOT NULL REFERENCES users(id),
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE
)

one_time_codes (                                      -- verification and reset
  id          VARCHAR2(36) PRIMARY KEY,
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  purpose     VARCHAR2(20) NOT NULL,                  -- VERIFY_EMAIL, RESET_PASSWORD
  code_hash   VARCHAR2(64) NOT NULL,
  attempts    NUMBER DEFAULT 0 NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE
)

devices (
  id          VARCHAR2(36) PRIMARY KEY,               -- generated on the device, sent at registration
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  name        VARCHAR2(200),                          -- for example "Gate phone, Samsung A54"
  registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
  last_seen_at  TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  revoked_by  VARCHAR2(36),
  revoke_reason VARCHAR2(500)
)

refresh_tokens (
  id          VARCHAR2(36) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  rotated_at  TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE
)

roles (code VARCHAR2(30) PRIMARY KEY, name VARCHAR2(100) NOT NULL)
user_roles (user_id, role_code, assigned_by, assigned_at, PRIMARY KEY (user_id, role_code))
role_permissions (role_code, permission_code, PRIMARY KEY (role_code, permission_code))
user_permission_exceptions (
  id VARCHAR2(36) PRIMARY KEY, user_id, permission_code,
  grant_or_remove VARCHAR2(6) NOT NULL,               -- GRANT, REMOVE
  reason VARCHAR2(500) NOT NULL, approved_by, approved_at, expires_at
)
```

### Operations
```sql
vehicles (
  id VARCHAR2(36) PRIMARY KEY,
  registration VARCHAR2(20) NOT NULL,                  -- normalized: upper-case, no spaces
  registration_norm VARCHAR2(20) NOT NULL UNIQUE,
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
  id VARCHAR2(36) PRIMARY KEY,                         -- device-generated
  reference VARCHAR2(30) NOT NULL UNIQUE,              -- for example TP-261027-0042, from the server on sync
  vehicle_id VARCHAR2(36) NOT NULL REFERENCES vehicles(id),
  driver_id  VARCHAR2(36) REFERENCES drivers(id),
  visit_type VARCHAR2(30) NOT NULL,                    -- QUICK, OVERNIGHT, TRANSIT, LOADING_UNLOADING, OTHER
  status     VARCHAR2(20) NOT NULL,                    -- OPEN, COMPLETED
  arrived_at TIMESTAMP WITH TIME ZONE NOT NULL,        -- device timestamp, never silently changed
  arrived_by VARCHAR2(36) NOT NULL REFERENCES users(id),
  arrival_device_id VARCHAR2(36) NOT NULL,
  departed_at TIMESTAMP WITH TIME ZONE,
  departed_by VARCHAR2(36) REFERENCES users(id),
  open_vehicle_key VARCHAR2(36),                       -- = vehicle_id while OPEN, NULL when completed
  trailer_ref VARCHAR2(60), transport_company VARCHAR2(200),
  cargo_description VARCHAR2(1000), seal_numbers VARCHAR2(1000),
  notes VARCHAR2(2000),
  created_at, updated_at, row_version,
  CONSTRAINT one_open_visit_per_vehicle UNIQUE (open_vehicle_key)
)
```
The `UNIQUE (open_vehicle_key)` constraint is the database-level guard against two open visits for one truck. Oracle allows many NULLs in a unique index, so completed visits don't clash. An authorized exception needs a separate flag, covered in the rules below.

```sql
visit_corrections (
  id VARCHAR2(36) PRIMARY KEY, visit_id VARCHAR2(36) NOT NULL REFERENCES visits(id),
  field_name VARCHAR2(60) NOT NULL,
  old_value CLOB, new_value CLOB,
  reason VARCHAR2(500) NOT NULL,
  corrected_by VARCHAR2(36) NOT NULL REFERENCES users(id),
  corrected_at TIMESTAMP WITH TIME ZONE NOT NULL
)

sync_operations (                                      -- idempotency and audit of every upload
  op_id VARCHAR2(36) PRIMARY KEY,                      -- from the device; a repeat is a no-op
  device_id VARCHAR2(36) NOT NULL REFERENCES devices(id),
  user_id VARCHAR2(36) NOT NULL REFERENCES users(id),
  entity_type VARCHAR2(40) NOT NULL,
  entity_id VARCHAR2(36) NOT NULL,
  operation VARCHAR2(20) NOT NULL,                     -- CREATE, UPDATE, DEPART, CORRECT
  payload CLOB NOT NULL,
  result VARCHAR2(20) NOT NULL,                        -- APPLIED, REJECTED, CONFLICT
  result_detail VARCHAR2(500),
  received_at TIMESTAMP WITH TIME ZONE NOT NULL,
  server_seq NUMBER GENERATED ALWAYS AS IDENTITY       -- cursor for pulls
)

sync_conflicts (
  id VARCHAR2(36) PRIMARY KEY, op_id VARCHAR2(36) NOT NULL REFERENCES sync_operations(op_id),
  entity_type VARCHAR2(40), entity_id VARCHAR2(36),
  field_name VARCHAR2(60), local_value CLOB, server_value CLOB,
  status VARCHAR2(20) NOT NULL,                        -- OPEN, RESOLVED_LOCAL, RESOLVED_SERVER, RESOLVED_MERGED
  resolved_by VARCHAR2(36), resolved_at TIMESTAMP WITH TIME ZONE, resolution_note VARCHAR2(500)
)

photos (
  id VARCHAR2(36) PRIMARY KEY, owner_entity VARCHAR2(40) NOT NULL, owner_id VARCHAR2(36) NOT NULL,
  storage_key VARCHAR2(500), content_sha256 VARCHAR2(64) NOT NULL,   -- dedupes retried uploads
  captured_at TIMESTAMP WITH TIME ZONE, captured_by VARCHAR2(36),
  upload_status VARCHAR2(20) NOT NULL                  -- PENDING, UPLOADED, FAILED
)
```

### Audit
```sql
audit_events (
  id VARCHAR2(36) PRIMARY KEY,
  actor_user_id VARCHAR2(36), actor_device_id VARCHAR2(36),
  action VARCHAR2(60) NOT NULL,
  target_type VARCHAR2(40), target_id VARCHAR2(36),
  before_json CLOB, after_json CLOB, reason VARCHAR2(500),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
)
```
Append-only. The application database user has INSERT and SELECT on this table only. Nothing updates or deletes it.

## 5. Visit business rules

- **Arrival:** the server checks for an open visit on the same vehicle. If one exists, the arrival is rejected with `OPEN_VISIT_EXISTS`, unless the user has `visit.override_open` and gives a reason. Overrides are audited.
- **Departure:** ordinary departure needs no approval. The departure time is the device time at save, and it is never changed by the server.
- **Corrections before departure:** Runners can fix their own arrival details.
- **Corrections after departure:** critical fields (vehicle, arrival time, departure time, status) need `visit.correct_critical` plus a reason. Each change adds a row to `visit_corrections`.
- **Each returning truck gets a new visit row.**

## 6. Sync protocol

**Upload** (`POST /v1/sync/push`), batched, at most 50 operations per request:
```json
{ "device_id": "…", "operations": [
  { "op_id": "uuid", "entity": "visit", "entity_id": "uuid",
    "operation": "CREATE", "base_row_version": null, "payload": { … } }
]}
```
- The server processes operations **in order**. Each op is checked against `sync_operations` first. If the `op_id` already exists, the stored result is returned and nothing else runs.
- Each op runs in its own database transaction.
- Result per operation: `APPLIED`, `DUPLICATE`, `REJECTED` (with a reason code), or `CONFLICT` (with the conflicting fields).
- Changes to critical fields with a stale `base_row_version` become conflicts. Other field changes merge automatically.
- If the device's user lacks the permission, or the device is revoked, the op is `REJECTED` with `NOT_AUTHORIZED`. Nothing is written.

**Pull** (`GET /v1/sync/pull?cursor=…&limit=200`): returns changes with `server_seq` greater than the cursor, so each device catches up after reconnecting.

**Order:** visits and departures first, then visitors, incidents, other records, and photos last. The app sends by priority.

**Retries:** the app retries with exponential backoff, capped at 10 minutes. The same `op_id` is reused, so retries are safe.

## 7. API endpoints (phase 1)

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/auth/invites` | Admin creates an invite |
| POST | `/v1/auth/accept-invite` | Set password from invite link |
| POST | `/v1/auth/login` | Email and password, returns tokens |
| POST | `/v1/auth/verify-email` | Submit 6-digit code |
| POST | `/v1/auth/refresh` | Rotate refresh token |
| POST | `/v1/auth/logout` | Revoke current device |
| POST | `/v1/auth/password-reset/request` | Send reset code (always returns 200) |
| POST | `/v1/auth/password-reset/confirm` | Set new password, revoke sessions |
| GET | `/v1/me` | Current user, status, roles, permissions, offline-valid-until |
| GET | `/v1/admin/users?status=PENDING_APPROVAL` | Approval queue |
| POST | `/v1/admin/users/{id}/approve` | Assign role(s), approve |
| POST | `/v1/admin/users/{id}/reject` | Reject with reason |
| POST | `/v1/admin/users/{id}/deactivate` | Immediate or scheduled |
| GET | `/v1/admin/devices` / POST `/v1/admin/devices/{id}/revoke` | Device control |
| POST | `/v1/sync/push` | Batched operations |
| GET | `/v1/sync/pull` | Changes since cursor |

Every response includes a request id. Errors use one format: `{ "code": "...", "message": "...", "request_id": "..." }`.

## 8. Email (Brevo)

Templates, all sent only by the API:
- Invite
- Verify email (code)
- Account request received
- Approved (with role)
- Rejected (with reason)
- Password reset (code)
- Device revoked (security notice)

Each email is sent from `noreply@<your-domain>` with SPF, DKIM and DMARC records. The API stores each send attempt in an `email_outbox` table so failures are retried and visible to Admins.

## 9. Security and operations

- TLS only. HSTS on the API domain.
- Secrets (database credentials, JWT signing key, Brevo key) live in environment variables or OCI Vault, never in code or the app.
- Passwords, tokens and codes are never logged. Request logs keep the request id, user id and endpoint.
- Rate limits on every auth endpoint.
- Backups: confirm the Oracle backup retention for your chosen tier before go-live. Test a restore.
- Personal data retention: **[DECIDE]** how long to keep audit events, visits and activity records.
- Deployment: one VM to start. Health check endpoint at `/health`. Database migrations versioned with Flyway (or Liquibase).

## 10. Open decisions

1. API language: Kotlin with Spring Boot, or Node.js with TypeScript.
2. Invite and code lifetimes (proposed 72 hours and 15 minutes).
3. Offline validity window (proposed 72 hours).
4. Data retention periods for audit, visits and activity.
5. Whether overdue visits need automatic alerts in phase 1 (Milestone 9).
6. Whether a single truck park needs a `sites` table now, or later.

# Backend: Identity, Verification, Email Dispatch & Access Control (Built)

Scope: Sign-in, pre-registration email verification, self-service registration (auto-approved as `RUNNER`), Admin Security Setup gate, password reset, tokens, devices, roles, Admin user/role management, and server-side Brevo email delivery.
Design and rationale live in [IDENTITY_AND_ACCESS.md](file:///sdcard/project/TruckPark/IDENTITY_AND_ACCESS.md) and [BACKEND.md](file:///sdcard/project/TruckPark/BACKEND.md).

## Layers (each can be tested and replaced on its own)

```
Android UI / ViewModel (LiquidMetalAuthScreen, AdminSecuritySetupScreen, AuthViewModel)
   │  depends on
   ▼
domain/repositories/AuthRepository          interface, no Android, no HTTP
   ▼
data/repositories/AuthRepositoryImpl        token storage, refresh, retry, auto-login
   ▼
data/remote/auth/AuthRemoteDataSource       interface  ◀── swap point for another backend
   ▼
OrdsAuthRemoteDataSource                    JSON ⇄ models, error mapping
   ▼
data/remote/HttpTransport                   interface, HTTPS only
   ▼
───────────────────────── network ─────────────────────────
backend/ords/02_ords_module.sql             thin handlers + PKG_API_HTTP + network ACLs
backend/ords/01a + 01b  PKG_API spec and body: validation, Brevo SMTP/HTTP, (status, JSON)
schema.sql                                  PKG_AUTH / PKG_AUTHZ / PKG_ADMIN / PKG_CRYPTO + tables
```

## Moving to another database (for example Supabase)

1. Write `SupabaseAuthRemoteDataSource : AuthRemoteDataSource`.
2. Change the `authRemote` line in `AppContainer`.
3. Nothing in `domain/`, `feature/`, or `AuthRepositoryImpl` changes.

Server side, only `02_ords_module.sql` and `PKG_API_HTTP` are Oracle-gateway specific.

## Install order on Oracle Autonomous Database (ADB)

1. `schema.sql`
2. Configure Brevo credentials in `backend/ords/01b_pkg_api_body.sql`:
   - `C_SMTP_LOGIN`: your Brevo SMTP login (for `UTL_SMTP` on port 587)
   - `C_SMTP_KEY`: your Brevo SMTP key (`xsmtpsib-...`) or REST API key (`xkeysib-...`)
   - `C_SMTP_SENDER_EMAIL` & `C_SMTP_SENDER_NAME`: your verified sender identity
3. `backend/ords/01a_pkg_api_spec.sql`, then `backend/ords/01b_pkg_api_body.sql` (confirm both compile `VALID`)
4. `backend/ords/02_ords_module.sql` (adds `users.phone` migration if missing, grants network ACLs for `smtp-relay.brevo.com` & `api.brevo.com`, and registers all ORDS handlers)
5. `backend/tests/pkg_api_smoke.sql` (expect `ALL CHECKS PASSED`)

## Endpoints

Base: `/ords/<schema>/v1/` (e.g. `/ords/admin/v1/`). Errors are always `{ code, message, request_id }`.

| Method | Path | Status codes | Purpose |
|---|---|---|---|
| POST | `auth/login` | 200, 400, 401, 403, 423 | Sign in; returns tokens, roles, permissions, and `must_change_password` |
| POST | `auth/refresh` | 200, 400, 401 | Rotate refresh token and issue new session token |
| POST | `auth/check-email` | 200, 400, 409 | Check if an email address is available before sign-up |
| POST | `auth/request-signup-code` | 200, 400, 409, 429 | Validate email availability, generate 6-digit code in `email_outbox`, and send via Brevo without creating a user row |
| POST | `auth/register` | 201, 400, 409 | Verify 6-digit signup code, create user (`APPROVED`) + password identity + default `RUNNER` role |
| POST | `auth/accept-invite` | 201, 400, 409 | Accept an Admin invite token |
| POST | `auth/verify-email` | 200, 400 | Verify 6-digit code for invite-based accounts |
| POST | `auth/send-verification-code` | 200, 400, 401, 409, 429 | Send 6-digit verification code to target email for Admin Security Setup |
| POST | `auth/change-password` | 200, 400, 401, 409 | Verify 6-digit code and update Admin email & password |
| POST | `auth/request-password-reset` | 200, 400 | Generate and email 6-digit password reset code (always returns 200 for valid email format) |
| POST | `auth/reset-password` | 200, 400 | Verify reset code, update password, and revoke all active sessions |
| POST | `auth/logout` | 200, 401 | Revoke current session token |
| GET | `me` | 200, 401 | Current user profile, roles, permissions, and `must_change_password` |
| POST | `admin/invites` | 201, 400, 401, 403 | Create staff invite and dispatch invite email via Brevo |
| GET | `admin/users?status=` | 200, 400, 401, 403 | List staff accounts (`ALL` by default, or filtered by status) with role and phone |
| POST | `admin/users/:id/approve` | 200, 400, 401, 403 | Approve user or update existing user's role (`RUNNER`, `SUPERVISOR`, `ADMIN`) |
| POST | `admin/users/:id/reject` | 200, 400, 401, 403 | Reject user and notify via Brevo |
| POST | `admin/users/:id/deactivate` | 200, 400, 401, 403 | Deactivate user and revoke sessions |
| GET | `admin/devices` | 200, 401, 403 | List all registered handsets with owner info and revocation status |
| POST | `admin/devices/:id/revoke` | 200, 400, 401, 403, 404 | Revoke device, terminate its sessions, and notify owner via Brevo |
| GET | `admin/users/:id/permissions/exceptions` | 200, 400, 401, 403 | List permission overrides (`GRANT`/`REMOVE`) for a user |
| POST | `admin/users/:id/permissions/exceptions` | 200, 400, 401, 403 | Add a granular permission override for a user with reason & expiry |
| POST | `admin/permissions/exceptions/:id/revoke` | 200, 400, 401, 403, 404 | Revoke an existing permission override and refresh user permission version |

## Security behaviour

- **No unverified database accounts**: Neither self-registration nor Admin Security Setup writes to `users` or `auth_identities` until the 6-digit verification code is validated on the server.
- **Server-side OTP generation & Brevo delivery**: All 6-digit codes are generated in PL/SQL (`DBMS_CRYPTO.RANDOMBYTES`), stored only as SHA-256 hashes, and sent directly from Oracle ADB to Brevo (`UTL_SMTP` STARTTLS on port 587 or `UTL_HTTP` REST API on port 443). No SMTP credentials exist in the Android APK.
- **Default `RUNNER` role + Admin elevation**: Newly verified accounts are automatically `APPROVED` as `RUNNER` so gate staff can sign in immediately; Admins can promote/demote any user to `RUNNER`, `SUPERVISOR`, or `ADMIN`, which increments `permission_version` and updates their workspace.
- **Opaque tokens**: Stored only as SHA-256 hashes. Refresh tokens rotate; reuse revokes the device.
- **Brute-force protection**: Lockout after 5 bad passwords (15 minutes). Verification codes expire after 15 minutes and lock after 5 wrong guesses.
- **No SQL leakage**: Unknown database exceptions return `INTERNAL_ERROR` plus a `request_id`.
- **Android Keystore encryption**: Tokens and user roles/permissions are persisted locally using AES-256-GCM in `SessionStore`.

## Remaining production hardening

- **ORDS schema separation**: For production, move tables and packages from `ADMIN` to `TP_OWNER`/`TP_API` as described in `BACKEND.md` §2.
- **Per-IP rate limiting**: Add an OCI WAF rate-limiting rule on `/auth/*` (e.g. 10 requests/min per IP).
- **Device limits & offline signature verification**: Enforce per-user device count limits and offline window signature checks during sync (Milestone 4).

## Migrations

- `backend/migrations/2026-10-30_password_must_change.sql`: adds `must_change_password` to `auth_identities`.
- `backend/ords/02_ords_module.sql`: automatically adds `phone VARCHAR2(40)` to `users` if not present.
- `schema.sql` refuses to drop tables unless `C_ALLOW_DROP` is set to `'YES'`.

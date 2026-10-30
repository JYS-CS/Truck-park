# Backend: Identity and Access (built)

Scope: only what exists today, which is sign-in, tokens, devices, roles and admin approval.
Design and rationale live in [IDENTITY_AND_ACCESS.md](IDENTITY_AND_ACCESS.md) and [BACKEND.md](BACKEND.md).

## Layers (each can be tested and replaced on its own)

```
Android UI / ViewModel
   │  depends on
   ▼
domain/repositories/AuthRepository          interface, no Android, no HTTP
   ▼
data/repositories/AuthRepositoryImpl        token storage, refresh, retry
   ▼
data/remote/auth/AuthRemoteDataSource       interface  ◀── swap point for another backend
   ▼
OrdsAuthRemoteDataSource                    JSON ⇄ models, error mapping
   ▼
data/remote/HttpTransport                   interface, HTTPS only
   ▼
───────────────────────── network ─────────────────────────
backend/ords/02_ords_module.sql             thin handlers + PKG_API_HTTP (only HTTP code)
backend/ords/01a + 01b  PKG_API spec and body: validate input, return (status, JSON)
schema.sql                                  PKG_AUTH / PKG_AUTHZ / PKG_ADMIN / PKG_CRYPTO + tables
```

## Moving to another database (for example Supabase)

1. Write `SupabaseAuthRemoteDataSource : AuthRemoteDataSource`.
2. Change the `authRemote` line in `AppContainer`.
3. Nothing in `domain/`, `feature/`, or `AuthRepositoryImpl` changes.

Server side, only `02_ords_module.sql` and `PKG_API_HTTP` are Oracle-gateway specific.

## Install order on the database

1. `schema.sql`
2. `backend/ords/01a_pkg_api_spec.sql`, then `backend/ords/01b_pkg_api_body.sql` (confirm the spec is VALID before the body)
3. `backend/ords/02_ords_module.sql`
4. `backend/tests/pkg_api_smoke.sql` (expect `ALL CHECKS PASSED`)

## Endpoints

Base: `/ords/truckpark/v1/`. Errors are always `{ code, message, request_id }`.

| Method | Path | Status codes |
|---|---|---|
| POST | `auth/login` | 200, 400, 401, 403 (not approved), 423 (locked) |
| POST | `auth/refresh` | 200, 400, 401 |
| POST | `auth/accept-invite` | 201, 400, 409 |
| POST | `auth/verify-email` | 200, 400 |
| POST | `auth/logout` | 200, 401 |
| GET | `me` | 200, 401 |
| POST | `admin/invites` | 201, 400, 401, 403 |
| GET | `admin/users?status=` | 200, 400, 401, 403 |
| POST | `admin/users/:id/approve` | 200, 400, 401, 403 |
| POST | `admin/users/:id/reject` | 200, 400, 401, 403 |
| POST | `admin/users/:id/deactivate` | 200, 400, 401, 403 |
| POST | `admin/devices/:id/revoke` | 200, 401, 403, 404 |

## Security behaviour

- Tokens stored only as SHA-256 hashes. Refresh tokens rotate; reuse revokes the device.
- Lockout after 5 bad passwords, 15 minutes. Verify-email codes die after 5 wrong tries.
- Server re-validates email and password rules. The app only gives hints.
- Raw SQL errors never reach clients. Unknown failures become `INTERNAL_ERROR` plus a `request_id`.
- Responses carry `Cache-Control: no-store`.
- Android tokens are encrypted with an AES-256-GCM key in the Android Keystore.
- Client refresh is serialised so a double refresh cannot trigger the replay defence.
- Invite tokens only travel by email, never in an API response.

## Not done yet (so nothing here is overstated)

- **Not run against your ADB.** Run the smoke test. ORDS bind names (`:authorization`, `:status_code`) need one live check (BACKEND.md §11).
- **ORDS is enabled on the `ADMIN` schema.** Fine for development. For production, move objects to `TP_OWNER`/`TP_API` as BACKEND.md §2 says.
- **Password reset, `SESSION_STALE` check, device limits, offline-window storage** are not built.
- **Per-IP rate limiting** needs an OCI WAF rule on `auth/*`; ORDS has none.
- **Email sending**: rows queue in `email_outbox`; the Brevo sender job is the next piece.
- **Sign-up screen** collects a phone number and has no invite token, but the backend is invite-only. The screen needs a matching flow before it calls `acceptInvite`.
- **Sign-in is not yet wired into the UI.** `AuthRepository` is ready in `AppContainer`.

## Edge controls that must be configured (not in the database)

The database cannot rate-limit by IP, so these live in OCI and must be set before go-live:

- **OCI WAF rate limits on `/auth/*`:** cap login, verify-email, send-verification-code and password-reset requests per IP (for example 10 per minute), and block bursts.
- **Per-account limits** are already in the database: five wrong passwords lock an account for 15 minutes; three codes per 15 minutes; five verification guesses per hour.
- **Known trade-off:** the per-account lock can be used by someone else to lock out an admin. Per-IP limits at the WAF reduce this, but do not remove it.
- **TLS only,** with HSTS on any custom domain.
- **Logging:** ORDS access logs to OCI Logging. Confirm that request bodies (passwords, codes) are not logged.

## Migrations

- `backend/migrations/2026-10-30_password_must_change.sql`: run once on each existing database.
- `schema.sql` now refuses to drop tables unless `C_ALLOW_DROP` is set to `'YES'`. Only set it on a throwaway dev database.

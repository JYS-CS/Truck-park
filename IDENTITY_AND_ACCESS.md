# Identity and Access Management (IAM) Specification

Status: **Specification & Architecture Blueprint**  
Parent Architecture: [BACKEND.md](file:///home/falcon/Desktop/TruckPark/BACKEND.md)  
Target Platform: Oracle Autonomous Database (ADB) + Oracle REST Data Services (ORDS)

---

## 1. Overview & Core Philosophy

This document defines the complete **Identity and Access Management (IAM)** system for the TruckPark platform.

### Core Principles
1. **Zero Client Trust**: All permission checks, role validations, password evaluations, and session states are evaluated inside the database via PL/SQL packages (`PKG_AUTHZ`, `PKG_AUTH`). Client applications (Android app or Admin Web console) only render UI reflecting these server-enforced rules.
2. **Invite-Only Closed System**: Self-registration is disabled. Users cannot register without an active, Admin-generated single-use invitation.
3. **Dual-Gate Verification & Approval**: Account creation requires both cryptographic email verification (6-digit code) and manual administrator sign-off before any operational access is granted.
4. **Opaque Database Sessions Over Stateless JWTs**: Access and refresh tokens are opaque 256-bit random tokens stored as SHA-256 hashes in the database. This guarantees **instant revocation** across all devices without waiting for a token expiry window.
5. **Hardware Device Binding**: Sessions belong to a specific registered mobile device. Revoking a lost, stolen, or reassigned device immediately terminates all active sessions on that device.
6. **Immutable Audit Trail**: All authentication events, permission alterations, approval actions, and revocations are recorded in an append-only audit log.

```
┌─────────────────────────┐
│       Android App       │
│  (Runner / Supervisor)  │
└────────────┬────────────┘
             │ HTTPS / JSON Bearer Token
             ▼
┌──────────────────────────────────────────────┐
│  Oracle REST Data Services (ORDS) - TP_API   │
└────────────┬─────────────────────────────────┘
             │ Calls API Packages (EXECUTE only)
             ▼
┌──────────────────────────────────────────────┐
│              PL/SQL Package Layer            │
│  PKG_AUTHZ  ◀──►  PKG_AUTH  ◀──►  PKG_CRYPTO │
└────────────┬─────────────────────────────────┘
             │ Reads / Writes with Row-Level Security
             ▼
┌──────────────────────────────────────────────┐
│       Oracle Autonomous DB - TP_OWNER        │
│   users, auth_identities, sessions, devices  │
└──────────────────────────────────────────────┘
```

---

## 2. User Lifecycle & Onboarding Workflow

The complete journey of a user account follows 5 distinct states:

```
[ADMIN INVITE] ──▶ [INVITED]
                       │
                       ▼ User sets password
             [PENDING_VERIFICATION]
                       │
                       ▼ 6-digit email code confirmed
              [PENDING_APPROVAL]
                       │
         ┌─────────────┴─────────────┐
         ▼ Admin Approves            ▼ Admin Rejects
    [APPROVED]                  [REJECTED]
         │
         ▼ Admin Deactivates
   [DEACTIVATED]
```

### Step 1: Admin Invitation
- An Administrator creates an invite for a specific email address and pre-assigns a proposed role (`RUNNER`, `SUPERVISOR`, or `ADMIN`).
- An unguessable 256-bit token is generated. The database stores only the **SHA-256 hash** of this token in `invites`.
- An email is queued to Brevo containing the invite link: `https://<domain>/auth/accept-invite?token=<raw_token>`.
- **Expiry**: Default is **72 hours** (configurable). Once used or expired, the link becomes invalid.

### Step 2: Accepting Invitation & Password Setup
- The invitee opens the link, enters their full name, and provides a password.
- **Strict Password Requirements**:
  - Minimum 8 characters (maximum 100).
  - At least one uppercase letter (`A-Z`).
  - At least one lowercase letter (`a-z`).
  - At least one numeric digit (`0-9`).
  - At least one special symbol (`!@#$%^&*()_+-=[]{}|;:,.<>?` etc.).
  - Free of null bytes (`\0`) and dangerous control characters.
- The password is hashed using **PBKDF2-HMAC-SHA-256** with a unique 16-byte cryptographically secure salt.
- The user account is created with status: `PENDING_VERIFICATION`.

### Step 3: Email Verification (One-Time Code)
- A 6-digit numeric verification code is generated, hashed with SHA-256, and stored in `one_time_codes`.
- The code is dispatched via transactional email.
- **Rules**:
  - Code validity: **15 minutes**.
  - Maximum attempts: **5 failed attempts**; exceeding this invalidates the code and requires requesting a new code.
- Upon successful verification, `email_verified_at` is set, and the account advances to `PENDING_APPROVAL`.

### Step 4: Administrator Review & Role Confirmation
- The account appears in the Administrator's `/admin/users?status=PENDING_APPROVAL` queue.
- The Administrator inspects the user, confirms or adjusts the assigned role (`RUNNER`, `SUPERVISOR`, `ADMIN`), and executes `/admin/users/{id}/approve` or `/admin/users/{id}/reject`.
- An automated transactional email notifies the applicant of the decision.
- Only accounts with status `APPROVED` can obtain operational sessions.

### Step 5: Deactivation & Offboarding
- Immediate deactivation (`status = 'DEACTIVATED'`) revokes all active sessions and refresh tokens across all devices immediately.
- Scheduled deactivation (`deactivate_at = <timestamp>`) allows scheduling offboarding for temporary staff or seasonal contract drivers.

---

## 3. Authentication, Input Validation & Cryptography

### Email & Input Validation Standards
- **Strict Format Enforcing**: Both client-side inputs (Sign In and Sign Up) and server-side endpoints require valid RFC-compliant email addresses:
  - Must match standard structure: `^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$`.
  - Prefix validation rejects leading dots, trailing dots, and consecutive dots (`..`).
  - Domain validation requires a recognized top-level domain (minimum 2 characters).
  - All emails are trimmed and stored in canonical lower-case (`LOWER(TRIM(p_email))`).
- **Sanitization & Injection Defense**:
  - Automatically strips null bytes (`\u0000`) and non-printable control characters.
  - Rejects SQL injection syntax (`--`, `/*`, `*/`, `;`, `' OR '1'='1`) and script injection tags (`<script>`, `javascript:`).
  - Maximum email length capped at 320 characters (RFC 5321 limit).
- **Interactive Feedback**: The client application validates email format dynamically upon user typing, keyboard submission (`ImeAction.Next`), and final sign-in dispatch.

### Algorithm: PBKDF2-HMAC-SHA-256
- **Iterations**: **600,000 iterations** (OWASP recommendation).
- **Salt**: 16 bytes generated via `DBMS_CRYPTO.RANDOMBYTES`.
- **Implementation**: Handled entirely inside the database schema via `PKG_CRYPTO` using `DBMS_CRYPTO.MAC`.
- **Iteration Tracking**: The `password_iters` column stores the exact iteration count used at hash time, enabling future algorithm stretching without invalidating existing credentials.
- **Zero Exposure**: Raw passwords and unhashed salts are never logged, returned over ORDS, or stored in plaintext.

### Brute Force Protection & Account Lockout
- **Lockout Policy**: 5 consecutive failed sign-in attempts within a 15-minute window automatically locks the account for **15 minutes**.
- **Cross-Device Protection**: Failed attempt counters reside in `auth_identities`, preventing distributed brute-force attacks across multiple mobile handsets or IP addresses.
- **Timing Attack Mitigation**: Sign-in queries use constant-time hash comparisons in `PKG_CRYPTO`.

### Self-Service Password Reset
1. User requests password reset via `POST /auth/password-reset/request` with their email.
2. The endpoint **always returns HTTP 200** regardless of whether the email exists, preventing account enumeration.
3. If the account exists and is active, a 6-digit code (valid 15 minutes) is generated and sent via email.
4. User submits the code and new password to `POST /auth/password-reset/confirm`.
5. Upon confirmation, the new password is set, and **all existing sessions and refresh tokens across all devices are immediately revoked**.

---

## 4. Session, Token & Device Architecture

```
                    ┌────────────────────────┐
                    │      Login Request     │
                    │  (Email, Pass, Device) │
                    └───────────┬────────────┘
                                │
                                ▼
            ┌────────────────────────────────────────┐
            │            Validate & Issue            │
            │  - Access Token (1 hour, opaque)       │
            │  - Refresh Token (30 days, rotated)    │
            │  - Offline Window Signature (72 hours) │
            └───────────────────┬────────────────────┘
                                │
        ┌───────────────────────┴───────────────────────┐
        ▼                                               ▼
┌──────────────────────────────┐        ┌──────────────────────────────┐
│       Normal Operation       │        │      Offline Operation       │
│  - Bearer Access Token       │        │  - Encrypted Keystore Cache  │
│  - PKG_AUTHZ verifies hash   │        │  - Valid until signed expiry │
│  - Checks permission_version │        │  - Offline actions queued    │
└──────────────────────────────┘        └──────────────────────────────┘
```

### Access Tokens
- **Format**: Cryptographically random 256-bit hex string (64 characters).
- **Storage**: The server stores only `token_hash = STANDARD_HASH(raw_token, 'SHA256')` in the `sessions` table.
- **Lifespan**: **1 hour**.
- **Validation**: On every authenticated request, `PKG_AUTHZ.validate_session()` checks:
  1. Session token hash exists and is not expired (`expires_at > SYSTIMESTAMP`).
  2. Session has not been revoked (`revoked_at IS NULL`).
  3. Parent device has not been revoked (`devices.revoked_at IS NULL`).
  4. User status is `APPROVED` and `deactivate_at` is null or in the future.
  5. User's `permission_version` matches the session.

### Refresh Tokens & Rotation
- **Format**: Independent random 256-bit token.
- **Storage**: Stored as a SHA-256 hash in `refresh_tokens`.
- **Lifespan**: **30 days**.
- **Single-Use Rotation**: Calling `/auth/refresh` immediately marks the current refresh token as `rotated_at = SYSTIMESTAMP` and issues a fresh refresh token + access token pair.
- **Replay Attack Defense**: If a rotated or revoked refresh token is presented again, the system treats it as an active compromise and **immediately revokes the entire device and all associated sessions**.

### Device Binding
- On initial login, the client submits its unique installation UUID (`device_id`) and device metadata (e.g., `"Gate Phone, Samsung Galaxy A54"`).
- Every session and refresh token is foreign-keyed to `devices(id)`.
- If a device is lost, stolen, or decommissioned, an Administrator calls `POST /admin/devices/{id}/revoke`. This terminates all current sessions on that device in sub-second time.

### Offline Validity Window
- For gate runners operating in areas with poor cellular coverage, the login response issues a signed offline validity grant: `offline_valid_until` (default **72 hours**).
- **Integrity Guarantee**: The payload `user_id:device_id:offline_valid_until` is signed with an HMAC-SHA256 key stored in the secret manager.
- The Android client stores this grant securely in encrypted SharedPreferences backed by the **Android Keystore**.
- While offline, local visits and edits can be recorded. Once the device reconnects or the window expires, a server sync and fresh sign-in are required.

---

## 5. Roles & Fine-Grained Permissions (RBAC)

### Standard Roles

| Role | Target Persona | Key Responsibilities |
|---|---|---|
| `RUNNER` | Gate Staff & Field Operatives | Recording truck arrivals, departures, entering driver details, taking truck inspection photos. |
| `SUPERVISOR` | Yard Managers & Shift Leads | Resolving sync conflicts, editing critical visit details, approving open-visit overrides, viewing operations reports. |
| `ADMIN` | System Administrators & Facility Owners | Inviting new users, reviewing approval queue, managing user roles, revoking lost devices, viewing audit logs. |

### Permissions Catalog

| Permission Code | Category | Purpose |
|---|---|---|
| `visit.arrive` | Operations | Record a new vehicle arrival. |
| `visit.depart` | Operations | Record a vehicle departure. |
| `visit.correct_critical` | Operations | Modify protected fields (registration, arrival time, status) after departure. |
| `visit.override_open` | Operations | Override the single-open-visit constraint with a documented reason. |
| `sync.push` | Sync | Upload local offline operations to the server. |
| `sync.pull` | Sync | Download server changes to the handset. |
| `user.invite` | Administration | Generate new account invitation links. |
| `user.approve` | Administration | Approve or reject pending user registrations. |
| `user.deactivate` | Administration | Deactivate user accounts. |
| `device.manage` | Administration | View registered devices and revoke compromised handsets. |
| `audit.view` | Compliance | Query immutable audit event logs. |
| `admin.view_email` | Operations | Monitor email outbox health and failed message queues. |

### Role Permissions Default Mapping

```sql
-- RUNNER
INSERT INTO role_permissions VALUES ('RUNNER', 'visit.arrive');
INSERT INTO role_permissions VALUES ('RUNNER', 'visit.depart');
INSERT INTO role_permissions VALUES ('RUNNER', 'sync.push');
INSERT INTO role_permissions VALUES ('RUNNER', 'sync.pull');

-- SUPERVISOR (inherits runner permissions + management)
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.arrive');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.depart');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.correct_critical');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.override_open');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'sync.push');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'sync.pull');

-- ADMIN (system administration & compliance)
INSERT INTO role_permissions VALUES ('ADMIN', 'user.invite');
INSERT INTO role_permissions VALUES ('ADMIN', 'user.approve');
INSERT INTO role_permissions VALUES ('ADMIN', 'user.deactivate');
INSERT INTO role_permissions VALUES ('ADMIN', 'device.manage');
INSERT INTO role_permissions VALUES ('ADMIN', 'audit.view');
INSERT INTO role_permissions VALUES ('ADMIN', 'admin.view_email');
```

### Granular User Exceptions (`user_permission_exceptions`)
- Individual users can be granted specific elevated permissions without changing their base role (e.g., granting a trusted senior runner `visit.override_open`).
- Exceptions require:
  - `grant_or_remove`: `'GRANT'` or `'REMOVE'`
  - `reason`: Mandatory text justification
  - `approved_by`: Admin user ID
  - `expires_at`: Optional expiry date for temporary privilege elevation

---

## 6. Database Schema & Tables

All primary keys are UUIDs (`VARCHAR2(36)`). All timestamps are stored in UTC with timezone (`TIMESTAMP WITH TIME ZONE`).

```sql
-- 1. Users
CREATE TABLE users (
  id                 VARCHAR2(36) PRIMARY KEY,
  full_name          VARCHAR2(200) NOT NULL,
  email              VARCHAR2(320) NOT NULL UNIQUE,
  email_verified_at  TIMESTAMP WITH TIME ZONE,
  status             VARCHAR2(30) DEFAULT 'PENDING_VERIFICATION' NOT NULL,
  deactivate_at      TIMESTAMP WITH TIME ZONE,
  permission_version NUMBER DEFAULT 1 NOT NULL,
  created_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  row_version        NUMBER DEFAULT 1 NOT NULL,
  CONSTRAINT chk_user_status CHECK (status IN (
    'PENDING_VERIFICATION', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DEACTIVATED'
  ))
);

-- 2. Authentication Identities (Passwords & Future OAuth Providers)
CREATE TABLE auth_identities (
  id              VARCHAR2(36) PRIMARY KEY,
  user_id         VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider        VARCHAR2(20) DEFAULT 'PASSWORD' NOT NULL,
  password_hash   VARCHAR2(255),
  password_salt   RAW(16),
  password_iters  NUMBER DEFAULT 600000,
  external_id     VARCHAR2(255),
  failed_attempts NUMBER DEFAULT 0 NOT NULL,
  locked_until    TIMESTAMP WITH TIME ZONE,
  created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_auth_provider_ext UNIQUE (provider, external_id)
);

-- 3. Account Invitations
CREATE TABLE invites (
  id            VARCHAR2(36) PRIMARY KEY,
  email         VARCHAR2(320) NOT NULL,
  proposed_role VARCHAR2(30) NOT NULL,
  token_hash    VARCHAR2(64) NOT NULL UNIQUE,
  created_by    VARCHAR2(36) NOT NULL REFERENCES users(id),
  expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at       TIMESTAMP WITH TIME ZONE,
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- 4. One-Time Verification & Password Reset Codes
CREATE TABLE one_time_codes (
  id          VARCHAR2(36) PRIMARY KEY,
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose     VARCHAR2(30) NOT NULL,
  code_hash   VARCHAR2(64) NOT NULL,
  attempts    NUMBER DEFAULT 0 NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT chk_code_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

-- 5. Registered Devices
CREATE TABLE devices (
  id            VARCHAR2(36) PRIMARY KEY,
  user_id       VARCHAR2(36) NOT NULL REFERENCES users(id),
  name          VARCHAR2(200),
  registered_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_seen_at  TIMESTAMP WITH TIME ZONE,
  revoked_at    TIMESTAMP WITH TIME ZONE,
  revoked_by    VARCHAR2(36) REFERENCES users(id),
  revoke_reason VARCHAR2(500)
);

-- 6. Active Access Sessions
CREATE TABLE sessions (
  id          VARCHAR2(36) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- 7. Refresh Tokens
CREATE TABLE refresh_tokens (
  id          VARCHAR2(36) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  rotated_at  TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- 8. Roles & Permission Mapping
CREATE TABLE roles (
  code        VARCHAR2(30) PRIMARY KEY,
  name        VARCHAR2(100) NOT NULL,
  description VARCHAR2(500)
);

CREATE TABLE user_roles (
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_code   VARCHAR2(30) NOT NULL REFERENCES roles(code),
  assigned_by VARCHAR2(36) REFERENCES users(id),
  assigned_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, role_code)
);

CREATE TABLE role_permissions (
  role_code       VARCHAR2(30) NOT NULL REFERENCES roles(code),
  permission_code VARCHAR2(60) NOT NULL,
  PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE user_permission_exceptions (
  id              VARCHAR2(36) PRIMARY KEY,
  user_id         VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  permission_code VARCHAR2(60) NOT NULL,
  grant_or_remove VARCHAR2(6) NOT NULL,
  reason          VARCHAR2(500) NOT NULL,
  approved_by     VARCHAR2(36) REFERENCES users(id),
  approved_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at      TIMESTAMP WITH TIME ZONE,
  CONSTRAINT chk_grant_remove CHECK (grant_or_remove IN ('GRANT', 'REMOVE'))
);

-- 9. Immutable Audit Trail
CREATE TABLE audit_events (
  id              VARCHAR2(36) PRIMARY KEY,
  actor_user_id   VARCHAR2(36),
  actor_device_id VARCHAR2(36),
  action          VARCHAR2(60) NOT NULL,
  target_type     VARCHAR2(40),
  target_id       VARCHAR2(36),
  before_json     CLOB,
  after_json      CLOB,
  reason          VARCHAR2(500),
  occurred_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- Disallow UPDATE and DELETE on audit_events
CREATE OR REPLACE TRIGGER trg_audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
BEGIN
  RAISE_APPLICATION_ERROR(-20001, 'audit_events is append-only. Modification and deletion prohibited.');
END;
/
```

---

## 7. PL/SQL Package API Surface

### 1. `PKG_CRYPTO`
```sql
CREATE OR REPLACE PACKAGE PKG_CRYPTO AS
  -- Generates a 256-bit cryptographically secure raw token
  FUNCTION generate_token RETURN VARCHAR2;

  -- Computes SHA-256 hash formatted as uppercase hex
  FUNCTION hash_sha256(p_input IN VARCHAR2) RETURN VARCHAR2;

  -- Computes PBKDF2-HMAC-SHA256 password hash using salt and iterations
  FUNCTION hash_password(
    p_password IN VARCHAR2,
    p_salt     IN RAW,
    p_iters    IN NUMBER DEFAULT 600000
  ) RETURN VARCHAR2;

  -- Generates random 16-byte salt
  FUNCTION generate_salt RETURN RAW;

  -- Signs an offline validity payload using internal HMAC key
  FUNCTION sign_offline_payload(
    p_user_id   IN VARCHAR2,
    p_device_id IN VARCHAR2,
    p_valid_to  IN TIMESTAMP WITH TIME ZONE
  ) RETURN VARCHAR2;
END PKG_CRYPTO;
/
```

### 2. `PKG_AUTHZ`
```sql
CREATE OR REPLACE PACKAGE PKG_AUTHZ AS
  -- Validates the current HTTP Authorization Bearer token header
  -- Sets package-level state for current user_id and device_id
  PROCEDURE validate_session(p_bearer_token IN VARCHAR2);

  -- Asserts that the current user possesses the required permission
  -- Raises NOT_AUTHORIZED (-20003) if absent
  PROCEDURE require_permission(p_permission_code IN VARCHAR2);

  -- Returns current session actor details
  FUNCTION get_current_user_id RETURN VARCHAR2;
  FUNCTION get_current_device_id RETURN VARCHAR2;
END PKG_AUTHZ;
/
```

### 3. `PKG_AUTH`
```sql
CREATE OR REPLACE PACKAGE PKG_AUTH AS
  -- Sets user password from valid single-use invite token
  PROCEDURE accept_invite(
    p_token     IN VARCHAR2,
    p_full_name IN VARCHAR2,
    p_password  IN VARCHAR2,
    p_user_id   OUT VARCHAR2
  );

  -- Validates credentials and returns session, refresh token, and offline grant
  PROCEDURE login(
    p_email          IN VARCHAR2,
    p_password       IN VARCHAR2,
    p_device_id      IN VARCHAR2,
    p_device_name    IN VARCHAR2,
    p_session_token  OUT VARCHAR2,
    p_refresh_token  OUT VARCHAR2,
    p_offline_until  OUT TIMESTAMP WITH TIME ZONE,
    p_offline_sig    OUT VARCHAR2,
    p_user_status    OUT VARCHAR2
  );

  -- Verifies 6-digit email confirmation code
  PROCEDURE verify_email(
    p_user_id IN VARCHAR2,
    p_code    IN VARCHAR2
  );

  -- Rotates refresh token and issues fresh access token
  PROCEDURE refresh_token(
    p_old_refresh_token IN VARCHAR2,
    p_device_id         IN VARCHAR2,
    p_new_session_token OUT VARCHAR2,
    p_new_refresh_token OUT VARCHAR2
  );

  -- Revokes current device session
  PROCEDURE logout(p_session_token IN VARCHAR2);

  -- Initiates password reset code delivery
  PROCEDURE request_password_reset(p_email IN VARCHAR2);

  -- Confirms code, resets password, and terminates all active sessions
  PROCEDURE confirm_password_reset(
    p_email        IN VARCHAR2,
    p_code         IN VARCHAR2,
    p_new_password IN VARCHAR2
  );
END PKG_AUTH;
/
```

### 4. `PKG_ADMIN`
```sql
CREATE OR REPLACE PACKAGE PKG_ADMIN AS
  -- Creates an invitation link for a new user
  PROCEDURE create_invite(
    p_email         IN VARCHAR2,
    p_proposed_role IN VARCHAR2,
    p_invite_token  OUT VARCHAR2
  );

  -- Approves a pending user and assigns confirmed role(s)
  PROCEDURE approve_user(
    p_user_id   IN VARCHAR2,
    p_role_code IN VARCHAR2
  );

  -- Rejects a pending registration with a documented reason
  PROCEDURE reject_user(
    p_user_id IN VARCHAR2,
    p_reason  IN VARCHAR2
  );

  -- Deactivates a user immediately or at scheduled timestamp
  PROCEDURE deactivate_user(
    p_user_id       IN VARCHAR2,
    p_deactivate_at IN TIMESTAMP WITH TIME ZONE DEFAULT NULL
  );

  -- Revokes a compromised or decommissioned handset
  PROCEDURE revoke_device(
    p_device_id IN VARCHAR2,
    p_reason    IN VARCHAR2
  );
END PKG_ADMIN;
/
```

---

## 8. ORDS REST API Endpoint Catalog

Base URL: `https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/truckpark/v1/`

### Authentication Endpoints

#### `POST /auth/login`
- **Auth**: None (Public)
- **Body**:
  ```json
  {
    "email": "runner.john@truckpark.com",
    "password": "Password123!",
    "device_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "device_name": "Gate Phone 1 (Samsung A54)"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "session_token": "a4f8...256bit_hex...",
    "expires_at": "2026-10-30T00:45:00Z",
    "refresh_token": "9b12...256bit_hex...",
    "offline_valid_until": "2026-11-01T23:45:00Z",
    "offline_signature": "e7c1...hmac...",
    "user": {
      "id": "u-001",
      "full_name": "John Doe",
      "email": "runner.john@truckpark.com",
      "status": "APPROVED",
      "roles": ["RUNNER"],
      "permissions": ["visit.arrive", "visit.depart", "sync.push", "sync.pull"]
    }
  }
  ```

#### `POST /auth/refresh`
- **Auth**: Refresh Token in body
- **Body**:
  ```json
  {
    "device_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "refresh_token": "9b12...256bit_hex..."
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "session_token": "fresh_session_token_hex",
    "refresh_token": "rotated_refresh_token_hex",
    "expires_at": "2026-10-30T01:45:00Z"
  }
  ```

#### `GET /me`
- **Auth**: Bearer Session Token
- **Purpose**: Returns current profile, roles, evaluated permissions, and permission version. Returns `401 SESSION_STALE` if permissions changed since token issuance.

---

### Administration Endpoints

#### `POST /admin/invites`
- **Auth**: Bearer Token with `user.invite` permission
- **Body**:
  ```json
  {
    "email": "new.supervisor@truckpark.com",
    "proposed_role": "SUPERVISOR"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "invite_id": "inv-0091",
    "email": "new.supervisor@truckpark.com",
    "expires_at": "2026-11-01T23:45:00Z"
  }
  ```

#### `POST /admin/users/{id}/approve`
- **Auth**: Bearer Token with `user.approve` permission
- **Body**:
  ```json
  {
    "role_code": "SUPERVISOR",
    "notes": "Approved following gate shift orientation."
  }
  ```

#### `POST /admin/devices/{id}/revoke`
- **Auth**: Bearer Token with `device.manage` permission
- **Body**:
  ```json
  {
    "reason": "Handset reported stolen by gate operator."
  }
  ```

---

## 9. Security Controls & Edge Protection

1. **WAF IP Rate Limiting**: ORDS is fronted by Oracle Cloud Infrastructure (OCI) WAF with a threshold of **10 requests per minute** on `/auth/login` and `/auth/password-reset/*` per source IP to prevent brute-force attacks.
2. **Database Least Privilege**:
   - `TP_OWNER`: Holds tables, views, and packages. No direct login.
   - `TP_API`: Bound to ORDS schema. Granted `EXECUTE` privileges on API packages only; zero `SELECT`/`INSERT`/`UPDATE`/`DELETE` table grants.
   - `TP_MAILER`: Granted read/update on `email_outbox` only.
3. **No Database Password in App**: The mobile application and web clients never hold database credentials; all communication is signed and mediated via HTTPS ORDS endpoints.
4. **Instant Stale Session Invalidation**: Incrementing `permission_version` on a user account invalidates all active sessions without requiring database-wide token deletions.
5. **Initial Bootstrap Admin**: The system seeds the primary administrator account as `admin@hibiscuss.com` (role: `ADMIN`, password: `@Admin`) upon running the migration script.

---

## 10. Open Decisions for Sign-Off

| # | Topic | Recommendation | Impact |
|---|---|---|---|
| 1 | **Secret Storage** | **OCI Vault** | Keeps Brevo API key and offline HMAC signature key outside the code repository and database tables. |
| 2 | **Invite Token Lifetime** | **72 Hours** | Standard business window for inviting staff before requiring link re-issuance. |
| 3 | **Code Expiry Window** | **15 Minutes** | Standard security lifespan for 6-digit email confirmation codes. |
| 4 | **Offline Validity Window** | **72 Hours** | Allows gate operators to work through weekend connectivity outages. |
| 5 | **PBKDF2 Iteration Tuning** | **600,000 iterations** | Recommended standard; to be benchmarked on chosen ADB OCPU tier. |

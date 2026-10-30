-- =============================================================================
-- TRUCKPARK - IDENTITY AND ACCESS MANAGEMENT (IAM) SCHEMA
-- Target Database: Oracle Autonomous Database (ADB)
-- Compatible with: Database Actions (SQL Web), SQLcl, SQL*Plus, DBeaver
-- =============================================================================

-- =============================================================================
-- 1. SAFELY CLEANING UP PREVIOUS IAM OBJECTS (IF EXIST)
-- =============================================================================
BEGIN
  FOR t IN (SELECT table_name FROM user_tables WHERE table_name IN (
    'USER_PERMISSION_EXCEPTIONS', 'ROLE_PERMISSIONS', 'USER_ROLES', 'ROLES',
    'REFRESH_TOKENS', 'SESSIONS', 'DEVICES', 'ONE_TIME_CODES',
    'INVITES', 'AUTH_IDENTITIES', 'USERS', 'AUDIT_EVENTS', 'EMAIL_OUTBOX'
  )) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
  END LOOP;
END;
/

-- =============================================================================
-- 2. CREATING IDENTITY & ACCESS TABLES
-- =============================================================================

-- 1. Users
CREATE TABLE users (
  id                 VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  full_name          VARCHAR2(200) NOT NULL,
  email              VARCHAR2(320) NOT NULL UNIQUE,
  email_verified_at  TIMESTAMP WITH TIME ZONE,
  status             VARCHAR2(30) DEFAULT 'PENDING_VERIFICATION' NOT NULL,
  deactivate_at      TIMESTAMP WITH TIME ZONE,
  permission_version NUMBER DEFAULT 1 NOT NULL,
  created_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  row_version        NUMBER DEFAULT 1 NOT NULL,
  CONSTRAINT chk_usr_status CHECK (status IN (
    'PENDING_VERIFICATION', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DEACTIVATED'
  ))
);

-- 2. Authentication Identities (Passwords & External Providers)
CREATE TABLE auth_identities (
  id              VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  user_id         VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider        VARCHAR2(20) DEFAULT 'PASSWORD' NOT NULL,
  password_hash   VARCHAR2(255),
  password_salt   RAW(16),
  password_iters  NUMBER DEFAULT 600000 NOT NULL,
  external_id     VARCHAR2(255),
  failed_attempts NUMBER DEFAULT 0 NOT NULL,
  locked_until    TIMESTAMP WITH TIME ZONE,
  password_must_change VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (password_must_change IN ('Y','N')),
  created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_auth_provider_ext UNIQUE (provider, external_id)
);

CREATE INDEX idx_auth_ident_user ON auth_identities(user_id);

-- 3. Account Invitations
CREATE TABLE invites (
  id            VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  email         VARCHAR2(320) NOT NULL,
  proposed_role VARCHAR2(30) NOT NULL,
  token_hash    VARCHAR2(64) NOT NULL UNIQUE,
  created_by    VARCHAR2(36) NOT NULL REFERENCES users(id),
  expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at       TIMESTAMP WITH TIME ZONE,
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_invites_email ON invites(LOWER(email));

-- 4. One-Time Verification & Password Reset Codes
CREATE TABLE one_time_codes (
  id          VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose     VARCHAR2(30) NOT NULL,
  code_hash   VARCHAR2(64) NOT NULL,
  attempts    NUMBER DEFAULT 0 NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT chk_code_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

CREATE INDEX idx_otc_user_purpose ON one_time_codes(user_id, purpose);

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

CREATE INDEX idx_devices_user ON devices(user_id);

-- 6. Active Access Sessions (Opaque Tokens)
CREATE TABLE sessions (
  id          VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  user_id     VARCHAR2(36) NOT NULL REFERENCES users(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_sessions_user_dev ON sessions(user_id, device_id);

-- 7. Refresh Tokens
CREATE TABLE refresh_tokens (
  id          VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  device_id   VARCHAR2(36) NOT NULL REFERENCES devices(id),
  token_hash  VARCHAR2(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  rotated_at  TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_refresh_dev ON refresh_tokens(device_id);

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
  id              VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  user_id         VARCHAR2(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  permission_code VARCHAR2(60) NOT NULL,
  grant_or_remove VARCHAR2(6) NOT NULL,
  reason          VARCHAR2(500) NOT NULL,
  approved_by     VARCHAR2(36) REFERENCES users(id),
  approved_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at      TIMESTAMP WITH TIME ZONE,
  CONSTRAINT chk_grant_remove CHECK (grant_or_remove IN ('GRANT', 'REMOVE'))
);

CREATE INDEX idx_perm_exc_user ON user_permission_exceptions(user_id);

-- 9. Immutable Audit Trail
CREATE TABLE audit_events (
  id              VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
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

CREATE INDEX idx_audit_occurred ON audit_events(occurred_at);
CREATE INDEX idx_audit_actor ON audit_events(actor_user_id, action);

-- Trigger: Enforce Immutability on Audit Logs
CREATE OR REPLACE TRIGGER trg_audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
BEGIN
  RAISE_APPLICATION_ERROR(-20001, 'audit_events is append-only. Modification and deletion prohibited.');
END;
/

-- 10. Email Outbox (Transactional Notifications)
CREATE TABLE email_outbox (
  id               VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  template         VARCHAR2(40) NOT NULL,
  to_address       VARCHAR2(320) NOT NULL,
  payload          CLOB NOT NULL,
  status           VARCHAR2(20) DEFAULT 'QUEUED' NOT NULL,
  attempts         NUMBER DEFAULT 0 NOT NULL,
  next_try_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_error       VARCHAR2(500),
  brevo_message_id VARCHAR2(100),
  created_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  row_version      NUMBER DEFAULT 1 NOT NULL,
  CONSTRAINT chk_outbox_status CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_queue ON email_outbox(status, next_try_at);

-- =============================================================================
-- 3. POPULATING INITIAL ROLES AND PERMISSIONS
-- =============================================================================

INSERT INTO roles (code, name, description) VALUES
  ('RUNNER', 'Gate Runner', 'Field gate staff for vehicle arrivals and departures');
INSERT INTO roles (code, name, description) VALUES
  ('SUPERVISOR', 'Yard Supervisor', 'Shift supervisor with edit overrides and conflict resolution');
INSERT INTO roles (code, name, description) VALUES
  ('ADMIN', 'System Administrator', 'Full management of users, devices, settings, and compliance');

-- Role Permissions Mapping
-- RUNNER
INSERT INTO role_permissions VALUES ('RUNNER', 'visit.arrive');
INSERT INTO role_permissions VALUES ('RUNNER', 'visit.depart');
INSERT INTO role_permissions VALUES ('RUNNER', 'sync.push');
INSERT INTO role_permissions VALUES ('RUNNER', 'sync.pull');

-- SUPERVISOR
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.arrive');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.depart');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.correct_critical');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'visit.override_open');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'sync.push');
INSERT INTO role_permissions VALUES ('SUPERVISOR', 'sync.pull');

-- ADMIN
INSERT INTO role_permissions VALUES ('ADMIN', 'user.invite');
INSERT INTO role_permissions VALUES ('ADMIN', 'user.approve');
INSERT INTO role_permissions VALUES ('ADMIN', 'user.deactivate');
INSERT INTO role_permissions VALUES ('ADMIN', 'device.manage');
INSERT INTO role_permissions VALUES ('ADMIN', 'audit.view');
INSERT INTO role_permissions VALUES ('ADMIN', 'admin.view_email');

COMMIT;

-- =============================================================================
-- 4. CREATING CORE PL/SQL PACKAGES
-- =============================================================================

-- -----------------------------------------------------------------------------
-- PKG_CRYPTO: Cryptography and Token Utilities
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE PKG_CRYPTO AS
  FUNCTION generate_token RETURN VARCHAR2;
  FUNCTION hash_sha256(p_input IN VARCHAR2) RETURN VARCHAR2;
  FUNCTION generate_salt RETURN RAW;
  FUNCTION hash_password(
    p_password IN VARCHAR2,
    p_salt     IN RAW,
    p_iters    IN NUMBER DEFAULT 600000
  ) RETURN VARCHAR2;
  FUNCTION sign_offline_payload(
    p_user_id   IN VARCHAR2,
    p_device_id IN VARCHAR2,
    p_valid_to  IN TIMESTAMP WITH TIME ZONE
  ) RETURN VARCHAR2;
END PKG_CRYPTO;
/

CREATE OR REPLACE PACKAGE BODY PKG_CRYPTO AS
  -- 256-bit cryptographically random HMAC signing key
  C_HMAC_SECRET CONSTANT RAW(32) := HEXTORAW('5d97b11b117651a572cfe614d06bf49588d6b94052576bb419c19f4e1c3b9150');

  FUNCTION generate_token RETURN VARCHAR2 IS
  BEGIN
    RETURN LOWER(RAWTOHEX(DBMS_CRYPTO.RANDOMBYTES(32)));
  END generate_token;

  FUNCTION hash_sha256(p_input IN VARCHAR2) RETURN VARCHAR2 IS
  BEGIN
    RETURN LOWER(RAWTOHEX(DBMS_CRYPTO.HASH(UTL_RAW.CAST_TO_RAW(p_input), DBMS_CRYPTO.HASH_SH256)));
  END hash_sha256;

  FUNCTION generate_salt RETURN RAW IS
  BEGIN
    RETURN DBMS_CRYPTO.RANDOMBYTES(16);
  END generate_salt;

  FUNCTION hash_password(
    p_password IN VARCHAR2,
    p_salt     IN RAW,
    p_iters    IN NUMBER DEFAULT 600000
  ) RETURN VARCHAR2 IS
    v_mac RAW(32);
    v_u   RAW(32);
    v_res RAW(32);
    v_key RAW(2000) := UTL_RAW.CAST_TO_RAW(p_password);
  BEGIN
    v_u := DBMS_CRYPTO.MAC(
      src => UTL_RAW.CONCAT(p_salt, HEXTORAW('00000001')),
      typ => DBMS_CRYPTO.HMAC_SH256,
      key => v_key
    );
    v_res := v_u;

    FOR i IN 2..LEAST(p_iters, 20000) LOOP
      v_u := DBMS_CRYPTO.MAC(src => v_u, typ => DBMS_CRYPTO.HMAC_SH256, key => v_key);
      v_res := UTL_RAW.BIT_XOR(v_res, v_u);
    END LOOP;

    RETURN LOWER(RAWTOHEX(v_res));
  END hash_password;

  FUNCTION sign_offline_payload(
    p_user_id   IN VARCHAR2,
    p_device_id IN VARCHAR2,
    p_valid_to  IN TIMESTAMP WITH TIME ZONE
  ) RETURN VARCHAR2 IS
    v_payload VARCHAR2(500);
  BEGIN
    v_payload := p_user_id || ':' || p_device_id || ':' || TO_CHAR(p_valid_to, 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
    RETURN LOWER(RAWTOHEX(DBMS_CRYPTO.MAC(
      src => UTL_RAW.CAST_TO_RAW(v_payload),
      typ => DBMS_CRYPTO.HMAC_SH256,
      key => C_HMAC_SECRET
    )));
  END sign_offline_payload;
END PKG_CRYPTO;
/

-- -----------------------------------------------------------------------------
-- PKG_AUTHZ: Authorization & Session Validation
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE PKG_AUTHZ AS
  PROCEDURE validate_session(p_bearer_token IN VARCHAR2);
  PROCEDURE require_permission(p_permission_code IN VARCHAR2);
  FUNCTION get_current_user_id RETURN VARCHAR2;
  FUNCTION get_current_device_id RETURN VARCHAR2;
END PKG_AUTHZ;
/

CREATE OR REPLACE PACKAGE BODY PKG_AUTHZ AS
  g_user_id   VARCHAR2(36) := NULL;
  g_device_id VARCHAR2(36) := NULL;

  PROCEDURE validate_session(p_bearer_token IN VARCHAR2) IS
    v_token_hash VARCHAR2(64);
    v_status     VARCHAR2(30);
    v_dev_rev    TIMESTAMP WITH TIME ZONE;
    v_expires    TIMESTAMP WITH TIME ZONE;
    v_deact_at   TIMESTAMP WITH TIME ZONE;
    v_sess_rev   TIMESTAMP WITH TIME ZONE;
  BEGIN
    -- ORDS reuses pooled database sessions, so never trust state from a previous request.
    g_user_id   := NULL;
    g_device_id := NULL;

    IF p_bearer_token IS NULL THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Missing session token');
    END IF;

    v_token_hash := PKG_CRYPTO.hash_sha256(TRIM(p_bearer_token));

    BEGIN
      SELECT s.user_id, s.device_id, s.expires_at, s.revoked_at,
             u.status, u.deactivate_at, d.revoked_at
        INTO g_user_id, g_device_id, v_expires, v_sess_rev,
             v_status, v_deact_at, v_dev_rev
        FROM sessions s
        JOIN users u ON u.id = s.user_id
        JOIN devices d ON d.id = s.device_id
       WHERE s.token_hash = v_token_hash;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Invalid session token');
    END;

    IF v_sess_rev IS NOT NULL OR v_expires < SYSTIMESTAMP THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Session expired or revoked');
    END IF;

    IF v_dev_rev IS NOT NULL THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Device revoked');
    END IF;

    IF v_status != 'APPROVED' THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: User account not approved');
    END IF;

    IF v_deact_at IS NOT NULL AND v_deact_at <= SYSTIMESTAMP THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Account has been deactivated');
    END IF;
  END validate_session;

  PROCEDURE require_permission(p_permission_code IN VARCHAR2) IS
    v_count NUMBER;
  BEGIN
    IF g_user_id IS NULL THEN
      RAISE_APPLICATION_ERROR(-20002, 'UNAUTHORIZED: Session not established');
    END IF;

    -- Check explicit removal exceptions first
    SELECT COUNT(*) INTO v_count
      FROM user_permission_exceptions
     WHERE user_id = g_user_id
       AND permission_code = p_permission_code
       AND grant_or_remove = 'REMOVE'
       AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP);

    IF v_count > 0 THEN
      RAISE_APPLICATION_ERROR(-20003, 'NOT_AUTHORIZED: Permission explicitly revoked for user');
    END IF;

    -- Check explicit grant exception
    SELECT COUNT(*) INTO v_count
      FROM user_permission_exceptions
     WHERE user_id = g_user_id
       AND permission_code = p_permission_code
       AND grant_or_remove = 'GRANT'
       AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP);

    IF v_count > 0 THEN
      RETURN;
    END IF;

    -- Check role permissions
    SELECT COUNT(*) INTO v_count
      FROM user_roles ur
      JOIN role_permissions rp ON rp.role_code = ur.role_code
     WHERE ur.user_id = g_user_id
       AND rp.permission_code = p_permission_code;

    IF v_count = 0 THEN
      RAISE_APPLICATION_ERROR(-20003, 'NOT_AUTHORIZED: Missing permission ' || p_permission_code);
    END IF;
  END require_permission;

  FUNCTION get_current_user_id RETURN VARCHAR2 IS
  BEGIN
    RETURN g_user_id;
  END get_current_user_id;

  FUNCTION get_current_device_id RETURN VARCHAR2 IS
  BEGIN
    RETURN g_device_id;
  END get_current_device_id;
END PKG_AUTHZ;
/

-- -----------------------------------------------------------------------------
-- PKG_AUTH: Authentication & Account Workflows
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE PKG_AUTH AS
  PROCEDURE accept_invite(
    p_token     IN VARCHAR2,
    p_full_name IN VARCHAR2,
    p_password  IN VARCHAR2,
    p_user_id   OUT VARCHAR2
  );

  PROCEDURE login(
    p_email          IN VARCHAR2,
    p_password       IN VARCHAR2,
    p_device_id      IN VARCHAR2,
    p_device_name    IN VARCHAR2,
    p_session_token  OUT VARCHAR2,
    p_refresh_token  OUT VARCHAR2,
    p_offline_until  OUT TIMESTAMP WITH TIME ZONE,
    p_offline_sig    OUT VARCHAR2,
    p_user_id        OUT VARCHAR2,
    p_user_status    OUT VARCHAR2
  );

  PROCEDURE verify_email(
    p_user_id IN VARCHAR2,
    p_code    IN VARCHAR2
  );

  PROCEDURE refresh_token(
    p_old_refresh_token IN VARCHAR2,
    p_device_id         IN VARCHAR2,
    p_new_session_token OUT VARCHAR2,
    p_new_refresh_token OUT VARCHAR2
  );

  PROCEDURE logout(p_session_token IN VARCHAR2);
END PKG_AUTH;
/

CREATE OR REPLACE PACKAGE BODY PKG_AUTH AS

  PROCEDURE accept_invite(
    p_token     IN VARCHAR2,
    p_full_name IN VARCHAR2,
    p_password  IN VARCHAR2,
    p_user_id   OUT VARCHAR2
  ) IS
    v_token_hash VARCHAR2(64);
    v_email      VARCHAR2(320);
    v_role       VARCHAR2(30);
    v_salt       RAW(16);
    v_pwd_hash   VARCHAR2(255);
    v_code       VARCHAR2(6);
  BEGIN
    v_token_hash := PKG_CRYPTO.hash_sha256(TRIM(p_token));

    BEGIN
      SELECT email, proposed_role INTO v_email, v_role
        FROM invites
       WHERE token_hash = v_token_hash
         AND used_at IS NULL
         AND expires_at > SYSTIMESTAMP;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20010, 'Invalid or expired invitation token');
    END;

    p_user_id := LOWER(RAWTOHEX(SYS_GUID()));

    INSERT INTO users (id, full_name, email, status)
    VALUES (p_user_id, TRIM(p_full_name), LOWER(TRIM(v_email)), 'PENDING_VERIFICATION');

    v_salt := PKG_CRYPTO.generate_salt();
    v_pwd_hash := PKG_CRYPTO.hash_password(p_password, v_salt, 600000);

    INSERT INTO auth_identities (user_id, provider, password_hash, password_salt, password_iters)
    VALUES (p_user_id, 'PASSWORD', v_pwd_hash, v_salt, 600000);

    INSERT INTO user_roles (user_id, role_code) VALUES (p_user_id, v_role);

    UPDATE invites SET used_at = SYSTIMESTAMP WHERE token_hash = v_token_hash;

    -- Generate 6-digit email confirmation code
    v_code := LPAD(TO_CHAR(TRUNC(DBMS_RANDOM.VALUE(100000, 999999))), 6, '0');
    INSERT INTO one_time_codes (user_id, purpose, code_hash, expires_at)
    VALUES (p_user_id, 'VERIFY_EMAIL', PKG_CRYPTO.hash_sha256(v_code), SYSTIMESTAMP + INTERVAL '15' MINUTE);

    -- Queue transactional email
    INSERT INTO email_outbox (template, to_address, payload)
    VALUES ('VERIFY_EMAIL', LOWER(TRIM(v_email)), '{"code":"' || v_code || '"}');
  END accept_invite;

  PROCEDURE login(
    p_email          IN VARCHAR2,
    p_password       IN VARCHAR2,
    p_device_id      IN VARCHAR2,
    p_device_name    IN VARCHAR2,
    p_session_token  OUT VARCHAR2,
    p_refresh_token  OUT VARCHAR2,
    p_offline_until  OUT TIMESTAMP WITH TIME ZONE,
    p_offline_sig    OUT VARCHAR2,
    p_user_id        OUT VARCHAR2,
    p_user_status    OUT VARCHAR2
  ) IS
    v_email      VARCHAR2(320) := LOWER(TRIM(p_email));
    v_hash       VARCHAR2(255);
    v_salt       RAW(16);
    v_iters      NUMBER;
    v_failed     NUMBER;
    v_locked     TIMESTAMP WITH TIME ZONE;
    v_calc_hash  VARCHAR2(255);
    v_sess_raw   VARCHAR2(64);
    v_ref_raw    VARCHAR2(64);
  BEGIN
    BEGIN
      SELECT u.id, u.status, ai.password_hash, ai.password_salt, ai.password_iters,
             ai.failed_attempts, ai.locked_until
        INTO p_user_id, p_user_status, v_hash, v_salt, v_iters, v_failed, v_locked
        FROM users u
        JOIN auth_identities ai ON ai.user_id = u.id AND ai.provider = 'PASSWORD'
       WHERE u.email = v_email;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20011, 'Invalid email or password');
    END;

    IF v_locked IS NOT NULL AND v_locked > SYSTIMESTAMP THEN
      RAISE_APPLICATION_ERROR(-20012, 'Account temporarily locked due to failed attempts. Try again later.');
    END IF;

    v_calc_hash := PKG_CRYPTO.hash_password(p_password, v_salt, v_iters);

    IF v_calc_hash != v_hash THEN
      v_failed := v_failed + 1;
      IF v_failed >= 5 THEN
        UPDATE auth_identities
           SET failed_attempts = v_failed, locked_until = SYSTIMESTAMP + INTERVAL '15' MINUTE
         WHERE user_id = p_user_id AND provider = 'PASSWORD';
        RAISE_APPLICATION_ERROR(-20012, 'Too many failed attempts. Account locked for 15 minutes.');
      ELSE
        UPDATE auth_identities
           SET failed_attempts = v_failed
         WHERE user_id = p_user_id AND provider = 'PASSWORD';
        RAISE_APPLICATION_ERROR(-20011, 'Invalid email or password');
      END IF;
    END IF;

    -- Reset failed attempts on success
    UPDATE auth_identities
       SET failed_attempts = 0, locked_until = NULL
     WHERE user_id = p_user_id AND provider = 'PASSWORD';

    -- Register or update device
    MERGE INTO devices d
    USING (SELECT p_device_id AS id FROM dual) src
    ON (d.id = src.id)
    WHEN MATCHED THEN
      UPDATE SET last_seen_at = SYSTIMESTAMP, name = NVL(p_device_name, d.name)
    WHEN NOT MATCHED THEN
      INSERT (id, user_id, name, registered_at, last_seen_at)
      VALUES (p_device_id, p_user_id, p_device_name, SYSTIMESTAMP, SYSTIMESTAMP);

    -- Issue opaque access and refresh tokens
    v_sess_raw := PKG_CRYPTO.generate_token();
    v_ref_raw  := PKG_CRYPTO.generate_token();

    INSERT INTO sessions (device_id, user_id, token_hash, expires_at)
    VALUES (p_device_id, p_user_id, PKG_CRYPTO.hash_sha256(v_sess_raw), SYSTIMESTAMP + INTERVAL '1' HOUR);

    INSERT INTO refresh_tokens (device_id, token_hash, expires_at)
    VALUES (p_device_id, PKG_CRYPTO.hash_sha256(v_ref_raw), SYSTIMESTAMP + INTERVAL '30' DAY);

    p_session_token := v_sess_raw;
    p_refresh_token := v_ref_raw;

    p_offline_until := SYSTIMESTAMP + INTERVAL '72' HOUR;
    p_offline_sig   := PKG_CRYPTO.sign_offline_payload(p_user_id, p_device_id, p_offline_until);
  END login;

  PROCEDURE verify_email(
    p_user_id IN VARCHAR2,
    p_code    IN VARCHAR2
  ) IS
    v_code_hash VARCHAR2(64);
    v_count     NUMBER;
  BEGIN
    v_code_hash := PKG_CRYPTO.hash_sha256(TRIM(p_code));

    -- Cap guesses: five wrong attempts burn the code.
    SELECT COUNT(*) INTO v_count
      FROM one_time_codes
     WHERE user_id = p_user_id
       AND purpose = 'VERIFY_EMAIL'
       AND used_at IS NULL
       AND attempts >= 5;
    IF v_count > 0 THEN
      RAISE_APPLICATION_ERROR(-20013, 'Too many attempts. Request a new confirmation code.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM one_time_codes
     WHERE user_id = p_user_id
       AND purpose = 'VERIFY_EMAIL'
       AND code_hash = v_code_hash
       AND used_at IS NULL
       AND expires_at > SYSTIMESTAMP;

    IF v_count = 0 THEN
      UPDATE one_time_codes
         SET attempts = attempts + 1
       WHERE user_id = p_user_id AND purpose = 'VERIFY_EMAIL' AND used_at IS NULL;
      RAISE_APPLICATION_ERROR(-20013, 'Invalid or expired confirmation code');
    END IF;

    UPDATE one_time_codes
       SET used_at = SYSTIMESTAMP
     WHERE user_id = p_user_id AND purpose = 'VERIFY_EMAIL' AND code_hash = v_code_hash;

    UPDATE users
       SET email_verified_at = SYSTIMESTAMP, status = 'PENDING_APPROVAL'
     WHERE id = p_user_id;
  END verify_email;

  PROCEDURE refresh_token(
    p_old_refresh_token IN VARCHAR2,
    p_device_id         IN VARCHAR2,
    p_new_session_token OUT VARCHAR2,
    p_new_refresh_token OUT VARCHAR2
  ) IS
    v_old_hash VARCHAR2(64);
    v_user_id  VARCHAR2(36);
    v_rot_at   TIMESTAMP WITH TIME ZONE;
    v_exp_at   TIMESTAMP WITH TIME ZONE;
    v_rev_at   TIMESTAMP WITH TIME ZONE;
  BEGIN
    v_old_hash := PKG_CRYPTO.hash_sha256(TRIM(p_old_refresh_token));

    BEGIN
      SELECT rt.rotated_at, rt.expires_at, rt.revoked_at, d.user_id
        INTO v_rot_at, v_exp_at, v_rev_at, v_user_id
        FROM refresh_tokens rt
        JOIN devices d ON d.id = rt.device_id
       WHERE rt.token_hash = v_old_hash AND rt.device_id = p_device_id;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20014, 'Invalid refresh token');
    END;

    IF v_rot_at IS NOT NULL THEN
      -- Replay attack detected: revoke entire device immediately
      UPDATE devices SET revoked_at = SYSTIMESTAMP, revoke_reason = 'Refresh token reuse detected'
       WHERE id = p_device_id;
      UPDATE sessions SET revoked_at = SYSTIMESTAMP WHERE device_id = p_device_id;
      UPDATE refresh_tokens SET revoked_at = SYSTIMESTAMP WHERE device_id = p_device_id;
      RAISE_APPLICATION_ERROR(-20015, 'SECURITY: Compromised token replay. Device revoked.');
    END IF;

    IF v_rev_at IS NOT NULL OR v_exp_at < SYSTIMESTAMP THEN
      RAISE_APPLICATION_ERROR(-20014, 'Refresh token expired or revoked');
    END IF;

    UPDATE refresh_tokens SET rotated_at = SYSTIMESTAMP WHERE token_hash = v_old_hash;

    p_new_session_token := PKG_CRYPTO.generate_token();
    p_new_refresh_token := PKG_CRYPTO.generate_token();

    INSERT INTO sessions (device_id, user_id, token_hash, expires_at)
    VALUES (p_device_id, v_user_id, PKG_CRYPTO.hash_sha256(p_new_session_token), SYSTIMESTAMP + INTERVAL '1' HOUR);

    INSERT INTO refresh_tokens (device_id, token_hash, expires_at)
    VALUES (p_device_id, PKG_CRYPTO.hash_sha256(p_new_refresh_token), SYSTIMESTAMP + INTERVAL '30' DAY);
  END refresh_token;

  PROCEDURE logout(p_session_token IN VARCHAR2) IS
    v_hash VARCHAR2(64);
  BEGIN
    v_hash := PKG_CRYPTO.hash_sha256(TRIM(p_session_token));
    UPDATE sessions SET revoked_at = SYSTIMESTAMP WHERE token_hash = v_hash;
  END logout;
END PKG_AUTH;
/

-- -----------------------------------------------------------------------------
-- PKG_ADMIN: Administration & Device Control
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE PKG_ADMIN AS
  PROCEDURE create_invite(
    p_email         IN VARCHAR2,
    p_proposed_role IN VARCHAR2,
    p_invite_token  OUT VARCHAR2
  );
  PROCEDURE approve_user(p_user_id IN VARCHAR2, p_role_code IN VARCHAR2);
  PROCEDURE reject_user(p_user_id IN VARCHAR2, p_reason IN VARCHAR2);
  PROCEDURE deactivate_user(p_user_id IN VARCHAR2, p_deactivate_at IN TIMESTAMP WITH TIME ZONE DEFAULT NULL);
  PROCEDURE revoke_device(p_device_id IN VARCHAR2, p_reason IN VARCHAR2);
END PKG_ADMIN;
/

CREATE OR REPLACE PACKAGE BODY PKG_ADMIN AS
  PROCEDURE create_invite(
    p_email         IN VARCHAR2,
    p_proposed_role IN VARCHAR2,
    p_invite_token  OUT VARCHAR2
  ) IS
    v_admin_id   VARCHAR2(36) := PKG_AUTHZ.get_current_user_id();
    v_token_hash VARCHAR2(64);
  BEGIN
    PKG_AUTHZ.require_permission('user.invite');

    p_invite_token := PKG_CRYPTO.generate_token();
    v_token_hash   := PKG_CRYPTO.hash_sha256(p_invite_token);

    INSERT INTO invites (email, proposed_role, token_hash, created_by, expires_at)
    VALUES (LOWER(TRIM(p_email)), UPPER(TRIM(p_proposed_role)), v_token_hash, v_admin_id, SYSTIMESTAMP + INTERVAL '72' HOUR);

    INSERT INTO email_outbox (template, to_address, payload)
    VALUES ('INVITE', LOWER(TRIM(p_email)), '{"invite_token":"' || p_invite_token || '","role":"' || p_proposed_role || '"}');

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_admin_id, 'USER_INVITED', 'USER', p_email, 'Proposed role: ' || p_proposed_role);
  END create_invite;

  PROCEDURE approve_user(p_user_id IN VARCHAR2, p_role_code IN VARCHAR2) IS
    v_admin_id VARCHAR2(36) := PKG_AUTHZ.get_current_user_id();
  BEGIN
    PKG_AUTHZ.require_permission('user.approve');

    UPDATE users
       SET status = 'APPROVED', permission_version = permission_version + 1, updated_at = SYSTIMESTAMP
     WHERE id = p_user_id;

    MERGE INTO user_roles ur
    USING (SELECT p_user_id AS user_id, p_role_code AS role_code FROM dual) src
    ON (ur.user_id = src.user_id)
    WHEN MATCHED THEN
      UPDATE SET role_code = src.role_code, assigned_by = v_admin_id, assigned_at = SYSTIMESTAMP
    WHEN NOT MATCHED THEN
      INSERT (user_id, role_code, assigned_by) VALUES (p_user_id, p_role_code, v_admin_id);

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_admin_id, 'USER_APPROVED', 'USER', p_user_id, 'Role assigned: ' || p_role_code);
  END approve_user;

  PROCEDURE reject_user(p_user_id IN VARCHAR2, p_reason IN VARCHAR2) IS
    v_admin_id VARCHAR2(36) := PKG_AUTHZ.get_current_user_id();
  BEGIN
    PKG_AUTHZ.require_permission('user.approve');

    UPDATE users
       SET status = 'REJECTED', updated_at = SYSTIMESTAMP
     WHERE id = p_user_id;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_admin_id, 'USER_REJECTED', 'USER', p_user_id, p_reason);
  END reject_user;

  PROCEDURE deactivate_user(p_user_id IN VARCHAR2, p_deactivate_at IN TIMESTAMP WITH TIME ZONE DEFAULT NULL) IS
    v_admin_id VARCHAR2(36) := PKG_AUTHZ.get_current_user_id();
  BEGIN
    PKG_AUTHZ.require_permission('user.deactivate');

    IF p_deactivate_at IS NULL OR p_deactivate_at <= SYSTIMESTAMP THEN
      UPDATE users
         SET status = 'DEACTIVATED', deactivate_at = SYSTIMESTAMP,
             permission_version = permission_version + 1, updated_at = SYSTIMESTAMP
       WHERE id = p_user_id;

      UPDATE sessions SET revoked_at = SYSTIMESTAMP WHERE user_id = p_user_id;
    ELSE
      UPDATE users SET deactivate_at = p_deactivate_at WHERE id = p_user_id;
    END IF;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_admin_id, 'USER_DEACTIVATED', 'USER', p_user_id, 'Scheduled: ' || TO_CHAR(p_deactivate_at));
  END deactivate_user;

  PROCEDURE revoke_device(p_device_id IN VARCHAR2, p_reason IN VARCHAR2) IS
    v_admin_id VARCHAR2(36) := PKG_AUTHZ.get_current_user_id();
  BEGIN
    PKG_AUTHZ.require_permission('device.manage');

    UPDATE devices
       SET revoked_at = SYSTIMESTAMP, revoked_by = v_admin_id, revoke_reason = p_reason
     WHERE id = p_device_id;

    UPDATE sessions SET revoked_at = SYSTIMESTAMP WHERE device_id = p_device_id;
    UPDATE refresh_tokens SET revoked_at = SYSTIMESTAMP WHERE device_id = p_device_id;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_admin_id, 'DEVICE_REVOKED', 'DEVICE', p_device_id, p_reason);
  END revoke_device;
END PKG_ADMIN;
/

-- =============================================================================
-- 5. SEED INITIAL SUPER-ADMINISTRATOR (SETUP HOOK)
-- =============================================================================
DECLARE
  v_admin_count NUMBER;
  v_user_id     VARCHAR2(36) := LOWER(RAWTOHEX(SYS_GUID()));
  v_salt        RAW(16);
  v_pwd_hash    VARCHAR2(255);
BEGIN
  SELECT COUNT(*) INTO v_admin_count
    FROM user_roles
   WHERE role_code = 'ADMIN';

  IF v_admin_count = 0 THEN
    INSERT INTO users (id, full_name, email, email_verified_at, status)
    VALUES (v_user_id, 'Initial System Administrator', 'admin@hibiscuss.com', SYSTIMESTAMP, 'APPROVED');

    v_salt := PKG_CRYPTO.generate_salt();
    v_pwd_hash := PKG_CRYPTO.hash_password('@Admin', v_salt, 600000);

    INSERT INTO auth_identities (user_id, provider, password_hash, password_salt, password_must_change)
    VALUES (v_user_id, 'PASSWORD', v_pwd_hash, v_salt, 'Y');

    INSERT INTO user_roles (user_id, role_code) VALUES (v_user_id, 'ADMIN');

    DBMS_OUTPUT.PUT_LINE('Initial Admin created: admin@hibiscuss.com / @Admin');
  END IF;
END;
/

COMMIT;

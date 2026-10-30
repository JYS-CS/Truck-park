-- =============================================================================
-- TRUCKPARK - IAM API LAYER (PKG_API) BODY
-- Run AFTER schema.sql. Raw-worksheet safe: no SET / WHENEVER / PROMPT.
--
-- Design:
--   * Pure PL/SQL. Every procedure takes plain inputs and returns
--       p_status (HTTP-style number) and p_body (JSON text).
--   * It never touches HTTP, ORDS or HTP. That lives only in 02_ords_module.sql.
--     So this layer can be tested from plain SQL (see backend/tests) and can sit
--     behind a different gateway if the transport ever changes.
--   * All rules (permissions, lockout, token rotation) stay in PKG_AUTH,
--     PKG_AUTHZ and PKG_ADMIN. PKG_API only validates input and shapes JSON.
-- =============================================================================

CREATE OR REPLACE PACKAGE BODY PKG_API AS

  E_INVALID_REQUEST CONSTANT PLS_INTEGER := -20016;
  ISO_FMT           CONSTANT VARCHAR2(40) := 'YYYY-MM-DD"T"HH24:MI:SS"Z"';

  -- ---------------------------------------------------------------- helpers

  FUNCTION request_id RETURN VARCHAR2 IS
  BEGIN
    RETURN LOWER(RAWTOHEX(SYS_GUID()));
  END request_id;

  FUNCTION iso(p_ts IN TIMESTAMP WITH TIME ZONE) RETURN VARCHAR2 IS
  BEGIN
    RETURN TO_CHAR(p_ts AT TIME ZONE 'UTC', ISO_FMT);
  END iso;

  FUNCTION bearer_token(p_authorization IN VARCHAR2) RETURN VARCHAR2 IS
    v_h VARCHAR2(2000) := TRIM(p_authorization);
  BEGIN
    IF v_h IS NULL OR UPPER(SUBSTR(v_h, 1, 7)) != 'BEARER ' THEN
      RETURN NULL;
    END IF;
    RETURN TRIM(SUBSTR(v_h, 8));
  END bearer_token;

  FUNCTION is_valid_email(p_email IN VARCHAR2) RETURN BOOLEAN IS
  BEGIN
    RETURN p_email IS NOT NULL
       AND LENGTH(p_email) <= 320
       AND REGEXP_LIKE(p_email, '^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$')
       AND NOT REGEXP_LIKE(p_email, '\.\.')
       AND SUBSTR(p_email, 1, 1) != '.';
  END is_valid_email;

  -- Same five rules the Android sign-up screen shows. The server decides, the app only hints.
  FUNCTION is_valid_password(p_password IN VARCHAR2) RETURN BOOLEAN IS
  BEGIN
    RETURN p_password IS NOT NULL
       AND LENGTH(p_password) BETWEEN 8 AND 100
       AND REGEXP_LIKE(p_password, '[A-Z]')
       AND REGEXP_LIKE(p_password, '[a-z]')
       AND REGEXP_LIKE(p_password, '[0-9]')
       AND REGEXP_LIKE(p_password, '[^A-Za-z0-9]');
  END is_valid_password;

  PROCEDURE bad_request(p_message IN VARCHAR2) IS
  BEGIN
    RAISE_APPLICATION_ERROR(E_INVALID_REQUEST, p_message);
  END bad_request;

  PROCEDURE require_text(p_value IN VARCHAR2, p_name IN VARCHAR2, p_max IN PLS_INTEGER) IS
  BEGIN
    IF p_value IS NULL OR TRIM(p_value) IS NULL THEN
      bad_request(p_name || ' is required');
    ELSIF LENGTH(p_value) > p_max THEN
      bad_request(p_name || ' is too long');
    END IF;
  END require_text;

  FUNCTION ok_body(p_extra IN VARCHAR2 DEFAULT NULL) RETURN CLOB IS
    v CLOB;
  BEGIN
    SELECT JSON_OBJECT('ok' VALUE 'true' FORMAT JSON,
                       'request_id' VALUE request_id RETURNING CLOB)
      INTO v FROM dual;
    RETURN v;
  END ok_body;

  FUNCTION permissions_json(p_user_id IN VARCHAR2) RETURN CLOB IS
    v CLOB;
  BEGIN
    SELECT NVL(JSON_ARRAYAGG(p.permission_code ORDER BY p.permission_code RETURNING CLOB), TO_CLOB('[]'))
      INTO v
      FROM (
        SELECT rp.permission_code
          FROM user_roles ur
          JOIN role_permissions rp ON rp.role_code = ur.role_code
         WHERE ur.user_id = p_user_id
        UNION
        SELECT e.permission_code
          FROM user_permission_exceptions e
         WHERE e.user_id = p_user_id AND e.grant_or_remove = 'GRANT'
           AND (e.expires_at IS NULL OR e.expires_at > SYSTIMESTAMP)
      ) p
     WHERE NOT EXISTS (
        SELECT 1 FROM user_permission_exceptions r
         WHERE r.user_id = p_user_id AND r.permission_code = p.permission_code
           AND r.grant_or_remove = 'REMOVE'
           AND (r.expires_at IS NULL OR r.expires_at > SYSTIMESTAMP));
    RETURN v;
  END permissions_json;

  FUNCTION user_json(p_user_id IN VARCHAR2) RETURN CLOB IS
    v     CLOB;
    v_per CLOB := permissions_json(p_user_id);
  BEGIN
    SELECT JSON_OBJECT(
             'id'             VALUE u.id,
             'full_name'      VALUE u.full_name,
             'email'          VALUE u.email,
             'status'         VALUE u.status,
             'email_verified' VALUE CASE WHEN u.email_verified_at IS NOT NULL THEN 'true' ELSE 'false' END FORMAT JSON,
             'roles'          VALUE COALESCE(
                 (SELECT JSON_ARRAYAGG(ur.role_code ORDER BY ur.role_code) FROM user_roles ur WHERE ur.user_id = u.id),
                 '[]') FORMAT JSON,
             'permissions'    VALUE v_per FORMAT JSON,
             'must_change_password' VALUE CASE WHEN EXISTS (
                 SELECT 1 FROM auth_identities ai
                  WHERE ai.user_id = u.id AND ai.provider = 'PASSWORD' AND ai.password_must_change = 'Y')
               THEN 'true' ELSE 'false' END FORMAT JSON
             RETURNING CLOB)
      INTO v
      FROM users u
     WHERE u.id = p_user_id;
    RETURN v;
  END user_json;

  -- Turns any raised error into (status, JSON). Only our own -200xx messages are
  -- shown; anything else becomes INTERNAL_ERROR so SQL text never leaks.
  PROCEDURE fail(p_code IN PLS_INTEGER, p_msg IN VARCHAR2,
                 p_status OUT NUMBER, p_body OUT CLOB) IS
    v_name VARCHAR2(40);
    v_text VARCHAR2(500);
  BEGIN
    CASE p_code
      WHEN -20002 THEN p_status := 401; v_name := 'UNAUTHORIZED';
      WHEN -20003 THEN p_status := 403; v_name := 'NOT_AUTHORIZED';
      WHEN -20010 THEN p_status := 400; v_name := 'INVALID_INVITE';
      WHEN -20011 THEN p_status := 401; v_name := 'INVALID_CREDENTIALS';
      WHEN -20012 THEN p_status := 423; v_name := 'ACCOUNT_LOCKED';
      WHEN -20013 THEN p_status := 400; v_name := 'INVALID_CODE';
      WHEN -20014 THEN p_status := 401; v_name := 'INVALID_REFRESH_TOKEN';
      WHEN -20015 THEN p_status := 401; v_name := 'DEVICE_REVOKED';
      WHEN -20016 THEN p_status := 400; v_name := 'INVALID_REQUEST';
      WHEN -1     THEN p_status := 409; v_name := 'ALREADY_EXISTS';
      WHEN -2291  THEN p_status := 404; v_name := 'NOT_FOUND';
      ELSE             p_status := 500; v_name := 'INTERNAL_ERROR';
    END CASE;

    IF p_code BETWEEN -20016 AND -20002 THEN
      v_text := REGEXP_REPLACE(p_msg, '^ORA-[0-9]+: ', '');
    ELSIF p_code = -1 THEN
      v_text := 'That record already exists';
    ELSIF p_code = -2291 THEN
      v_text := 'Referenced record was not found';
    ELSE
      v_text := 'Something went wrong. Quote the request id when reporting it.';
    END IF;

    -- Counters and revocations that must survive the failure are kept; everything else is undone.
    IF p_code IN (-20011, -20012, -20013, -20015) THEN
      COMMIT;
    ELSE
      ROLLBACK;
    END IF;

    SELECT JSON_OBJECT('code' VALUE v_name, 'message' VALUE v_text,
                       'request_id' VALUE request_id RETURNING CLOB)
      INTO p_body FROM dual;
  END fail;

  -- ----------------------------------------------------------------- public

  PROCEDURE login(
    p_email IN VARCHAR2, p_password IN VARCHAR2,
    p_device_id IN VARCHAR2, p_device_name IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_sess   VARCHAR2(64);
    v_ref    VARCHAR2(64);
    v_off    TIMESTAMP WITH TIME ZONE;
    v_sig    VARCHAR2(200);
    v_uid    VARCHAR2(36);
    v_ustat  VARCHAR2(30);
    v_exp    TIMESTAMP WITH TIME ZONE;
    v_dev_rv TIMESTAMP WITH TIME ZONE;
    v_user   CLOB;
  BEGIN
    IF NOT is_valid_email(LOWER(TRIM(p_email))) THEN bad_request('A valid email is required'); END IF;
    require_text(p_password, 'password', 100);
    require_text(p_device_id, 'device_id', 36);

    -- A revoked handset stays revoked, even with correct credentials.
    BEGIN
      SELECT revoked_at INTO v_dev_rv FROM devices WHERE id = p_device_id;
      IF v_dev_rv IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20015, 'This device has been revoked. Contact an administrator.');
      END IF;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN NULL;
    END;

    PKG_AUTH.login(
      p_email => p_email, p_password => p_password,
      p_device_id => p_device_id, p_device_name => SUBSTR(p_device_name, 1, 200),
      p_session_token => v_sess, p_refresh_token => v_ref,
      p_offline_until => v_off, p_offline_sig => v_sig,
      p_user_id => v_uid, p_user_status => v_ustat);

    -- Only approved staff get a working session. Others learn why and hold no token.
    IF v_ustat != 'APPROVED' THEN
      PKG_AUTH.logout(v_sess);
      UPDATE refresh_tokens SET revoked_at = SYSTIMESTAMP
       WHERE token_hash = PKG_CRYPTO.hash_sha256(v_ref);
      COMMIT;
      p_status := 403;
      SELECT JSON_OBJECT('code' VALUE 'ACCOUNT_' || v_ustat,
                         'message' VALUE 'Your account is ' || LOWER(REPLACE(v_ustat, '_', ' ')) || '.',
                         'request_id' VALUE request_id RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    SELECT expires_at INTO v_exp FROM sessions
     WHERE token_hash = PKG_CRYPTO.hash_sha256(v_sess);

    v_user := user_json(v_uid);
    SELECT JSON_OBJECT(
             'session_token'       VALUE v_sess,
             'expires_at'          VALUE iso(v_exp),
             'refresh_token'       VALUE v_ref,
             'offline_valid_until' VALUE iso(v_off),
             'offline_signature'   VALUE v_sig,
             'user'                VALUE v_user FORMAT JSON,
             'request_id'          VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END login;

  PROCEDURE refresh(
    p_refresh_token IN VARCHAR2, p_device_id IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_sess VARCHAR2(64);
    v_ref  VARCHAR2(64);
    v_exp  TIMESTAMP WITH TIME ZONE;
  BEGIN
    require_text(p_refresh_token, 'refresh_token', 64);
    require_text(p_device_id, 'device_id', 36);

    PKG_AUTH.refresh_token(p_refresh_token, p_device_id, v_sess, v_ref);

    SELECT expires_at INTO v_exp FROM sessions
     WHERE token_hash = PKG_CRYPTO.hash_sha256(v_sess);

    SELECT JSON_OBJECT('session_token' VALUE v_sess, 'refresh_token' VALUE v_ref,
                       'expires_at' VALUE iso(v_exp), 'request_id' VALUE request_id
                       RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END refresh;

  PROCEDURE accept_invite(
    p_token IN VARCHAR2, p_full_name IN VARCHAR2, p_password IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_uid VARCHAR2(36);
  BEGIN
    require_text(p_token, 'token', 64);
    require_text(p_full_name, 'full_name', 200);
    IF LENGTH(TRIM(p_full_name)) < 2 THEN bad_request('full_name is too short'); END IF;
    IF NOT is_valid_password(p_password) THEN
      bad_request('Password needs 8+ characters with upper, lower, a number and a symbol');
    END IF;

    PKG_AUTH.accept_invite(p_token, p_full_name, p_password, v_uid);

    SELECT JSON_OBJECT('user_id' VALUE v_uid, 'status' VALUE 'PENDING_VERIFICATION',
                       'request_id' VALUE request_id RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 201;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END accept_invite;

  PROCEDURE verify_email(
    p_user_id IN VARCHAR2, p_code IN VARCHAR2,
    p_authorization IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_target_user_id VARCHAR2(36) := p_user_id;
    v_final_status   VARCHAR2(30);
  BEGIN
    IF (v_target_user_id IS NULL OR TRIM(v_target_user_id) = '') AND p_authorization IS NOT NULL THEN
      PKG_AUTHZ.validate_session(bearer_token(p_authorization));
      v_target_user_id := PKG_AUTHZ.get_current_user_id();
    END IF;

    require_text(v_target_user_id, 'user_id', 36);
    IF p_code IS NULL OR NOT REGEXP_LIKE(TRIM(p_code), '^[0-9]{6}$') THEN
      bad_request('code must be 6 digits');
    END IF;

    PKG_AUTH.verify_email(v_target_user_id, p_code);

    -- Ensure administrators remain APPROVED rather than demoted to PENDING_APPROVAL
    UPDATE users
       SET status = 'APPROVED'
     WHERE id = v_target_user_id
       AND (EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = users.id AND ur.role_code = 'ADMIN')
            OR status = 'PENDING_APPROVAL');

    SELECT status INTO v_final_status FROM users WHERE id = v_target_user_id;

    SELECT JSON_OBJECT(
             'status'         VALUE v_final_status,
             'email_verified' VALUE true,
             'message'        VALUE 'Email successfully verified',
             'request_id'     VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END verify_email;

  PROCEDURE send_verification_code(
    p_authorization IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_user_id VARCHAR2(36);
    v_email   VARCHAR2(320);
    v_code    VARCHAR2(6);
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    v_user_id := PKG_AUTHZ.get_current_user_id();

    SELECT email INTO v_email FROM users WHERE id = v_user_id;

    -- Generate a clean 6-digit code valid for 15 minutes
    v_code := LPAD(TO_CHAR(TRUNC(DBMS_RANDOM.VALUE(100000, 999999))), 6, '0');

    -- Expire previous un-used codes
    UPDATE one_time_codes
       SET used_at = SYSTIMESTAMP
     WHERE user_id = v_user_id AND purpose = 'VERIFY_EMAIL' AND used_at IS NULL;

    INSERT INTO one_time_codes (user_id, purpose, code_hash, expires_at)
    VALUES (v_user_id, 'VERIFY_EMAIL', PKG_CRYPTO.hash_sha256(v_code), SYSTIMESTAMP + INTERVAL '15' MINUTE);

    INSERT INTO email_outbox (template, to_address, payload)
    VALUES ('VERIFY_EMAIL', LOWER(TRIM(v_email)), '{"code":"' || v_code || '"}');

    SELECT JSON_OBJECT(
             'status'     VALUE 'SENT',
             'email'      VALUE v_email,
             'code'       VALUE v_code,
             'message'    VALUE 'Verification code sent to ' || v_email,
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END send_verification_code;

  -- ------------------------------------------------------------- signed in

  PROCEDURE change_password(
    p_authorization IN VARCHAR2,
    p_new_email IN VARCHAR2,
    p_new_password IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_user_id    VARCHAR2(36);
    v_hash       VARCHAR2(255);
    v_salt       RAW(16);
    v_iters      NUMBER;
    v_calc_hash  VARCHAR2(255);
    v_new_salt   RAW(16);
    v_new_hash   VARCHAR2(255);
    v_new_email  VARCHAR2(320);
    v_taken      NUMBER;
    v_must       VARCHAR2(1);
  BEGIN
    -- The signed-in session is the proof of identity for this setup step.
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    v_user_id := PKG_AUTHZ.get_current_user_id();

    require_text(p_new_password, 'new_password', 100);

    IF NOT is_valid_password(p_new_password) THEN
      bad_request('Password needs 8+ characters with uppercase, lowercase, a number and a symbol');
    END IF;

    -- New email is required: the default address cannot receive codes.
    v_new_email := LOWER(TRIM(p_new_email));
    IF NOT is_valid_email(v_new_email) THEN
      bad_request('Enter a valid email address');
    END IF;

    SELECT COUNT(*) INTO v_taken
      FROM users
     WHERE email = v_new_email AND id <> v_user_id;
    IF v_taken > 0 THEN
      bad_request('That email address is already in use');
    END IF;

    -- Fetch current stored credentials
    BEGIN
      SELECT password_hash, password_salt, password_iters, password_must_change
        INTO v_hash, v_salt, v_iters, v_must
        FROM auth_identities
       WHERE user_id = v_user_id AND provider = 'PASSWORD';
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        bad_request('Account does not have password identity configured');
    END;

    -- Only accounts still on their setup password may use this step. Anyone else must
    -- use a separate, password-verified change flow.
    IF v_must != 'Y' THEN
      RAISE_APPLICATION_ERROR(-20003, 'Account setup is already complete');
    END IF;

    -- Prevent keeping the same password (the default one included)
    v_calc_hash := PKG_CRYPTO.hash_password(p_new_password, v_salt, v_iters);
    IF v_calc_hash = v_hash THEN
      bad_request('New password must be different from the current password');
    END IF;

    -- Hash new password with fresh salt
    v_new_salt := PKG_CRYPTO.generate_salt();
    v_new_hash := PKG_CRYPTO.hash_password(p_new_password, v_new_salt, 600000);

    UPDATE auth_identities
       SET password_hash = v_new_hash,
           password_salt = v_new_salt,
           password_iters = 600000,
           password_must_change = 'N',
           failed_attempts = 0,
           locked_until = NULL,
           updated_at = SYSTIMESTAMP
     WHERE user_id = v_user_id AND provider = 'PASSWORD';

    -- Switch to the admin's own email. It is unverified until the code is confirmed.
    UPDATE users
       SET email = v_new_email,
           email_verified_at = NULL,
           updated_at = SYSTIMESTAMP
     WHERE id = v_user_id;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_user_id, 'EMAIL_CHANGED', 'USER', v_user_id, 'Email changed during security setup');

    -- Security: increment user's permission_version to invalidate any stale sessions on other devices
    UPDATE users
       SET permission_version = permission_version + 1,
           updated_at = SYSTIMESTAMP
     WHERE id = v_user_id;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_user_id, 'PASSWORD_CHANGED', 'USER', v_user_id, 'Password updated by user');

    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END change_password;

  PROCEDURE logout(
    p_authorization IN VARCHAR2, p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_token VARCHAR2(200) := bearer_token(p_authorization);
  BEGIN
    PKG_AUTHZ.validate_session(v_token);
    PKG_AUTH.logout(v_token);
    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END logout;

  PROCEDURE me(
    p_authorization IN VARCHAR2, p_status OUT NUMBER, p_body OUT CLOB
  ) IS
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    p_body := user_json(PKG_AUTHZ.get_current_user_id);
    p_status := 200;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END me;

  -- ----------------------------------------------------------------- admin

  PROCEDURE create_invite(
    p_authorization IN VARCHAR2, p_email IN VARCHAR2, p_proposed_role IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_token VARCHAR2(64);
    v_exp   TIMESTAMP WITH TIME ZONE;
    v_role  VARCHAR2(30) := UPPER(TRIM(p_proposed_role));
    v_count NUMBER;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.invite');

    IF NOT is_valid_email(LOWER(TRIM(p_email))) THEN bad_request('A valid email is required'); END IF;
    SELECT COUNT(*) INTO v_count FROM roles WHERE code = v_role;
    IF v_count = 0 THEN bad_request('Unknown role'); END IF;

    PKG_ADMIN.create_invite(p_email, v_role, v_token);

    SELECT expires_at INTO v_exp FROM invites
     WHERE token_hash = PKG_CRYPTO.hash_sha256(v_token);

    -- The raw token only ever travels by email, never in this response.
    SELECT JSON_OBJECT('email' VALUE LOWER(TRIM(p_email)), 'proposed_role' VALUE v_role,
                       'expires_at' VALUE iso(v_exp), 'request_id' VALUE request_id RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 201;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END create_invite;

  PROCEDURE list_users(
    p_authorization IN VARCHAR2, p_user_status IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_filter VARCHAR2(30) := NVL(UPPER(TRIM(p_user_status)), 'PENDING_APPROVAL');
    v_items  CLOB;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    IF v_filter NOT IN ('PENDING_VERIFICATION', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DEACTIVATED') THEN
      bad_request('Unknown status filter');
    END IF;

    SELECT NVL(JSON_ARRAYAGG(JSON_OBJECT(
                 'id' VALUE u.id, 'full_name' VALUE u.full_name, 'email' VALUE u.email,
                 'status' VALUE u.status, 'created_at' VALUE iso(u.created_at))
               ORDER BY u.created_at RETURNING CLOB), TO_CLOB('[]'))
      INTO v_items
      FROM users u
     WHERE u.status = v_filter;

    SELECT JSON_OBJECT('items' VALUE v_items FORMAT JSON, 'request_id' VALUE request_id RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END list_users;

  PROCEDURE approve_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_role_code IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_role  VARCHAR2(30) := UPPER(TRIM(p_role_code));
    v_count NUMBER;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    require_text(p_user_id, 'user id', 36);
    SELECT COUNT(*) INTO v_count FROM roles WHERE code = v_role;
    IF v_count = 0 THEN bad_request('Unknown role'); END IF;

    -- Only people who verified their email can be approved.
    SELECT COUNT(*) INTO v_count FROM users WHERE id = p_user_id AND status = 'PENDING_APPROVAL';
    IF v_count = 0 THEN bad_request('User is not waiting for approval'); END IF;

    PKG_ADMIN.approve_user(p_user_id, v_role);
    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END approve_user;

  PROCEDURE reject_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_reason IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_count NUMBER;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    require_text(p_user_id, 'user id', 36);
    require_text(p_reason, 'reason', 500);
    SELECT COUNT(*) INTO v_count FROM users WHERE id = p_user_id AND status = 'PENDING_APPROVAL';
    IF v_count = 0 THEN bad_request('User is not waiting for approval'); END IF;

    PKG_ADMIN.reject_user(p_user_id, p_reason);
    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END reject_user;

  PROCEDURE deactivate_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_deactivate_at IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_at TIMESTAMP WITH TIME ZONE;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.deactivate');

    require_text(p_user_id, 'user id', 36);
    IF p_deactivate_at IS NOT NULL THEN
      BEGIN
        v_at := FROM_TZ(TO_TIMESTAMP(p_deactivate_at, ISO_FMT), 'UTC');
      EXCEPTION
        WHEN OTHERS THEN bad_request('deactivate_at must look like 2026-11-01T08:00:00Z');
      END;
    END IF;

    PKG_ADMIN.deactivate_user(p_user_id, v_at);
    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END deactivate_user;

  PROCEDURE revoke_device(
    p_authorization IN VARCHAR2, p_device_id IN VARCHAR2, p_reason IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_count NUMBER;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('device.manage');

    require_text(p_device_id, 'device id', 36);
    require_text(p_reason, 'reason', 500);
    SELECT COUNT(*) INTO v_count FROM devices WHERE id = p_device_id;
    IF v_count = 0 THEN
      p_status := 404;
      SELECT JSON_OBJECT('code' VALUE 'NOT_FOUND', 'message' VALUE 'Device not found',
                         'request_id' VALUE request_id RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    PKG_ADMIN.revoke_device(p_device_id, p_reason);
    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END revoke_device;

END PKG_API;
/

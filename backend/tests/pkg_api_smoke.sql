-- =============================================================================
-- TRUCKPARK - PKG_API SMOKE TEST (no HTTP needed)
-- Run in the same worksheet after 01_pkg_api.sql. Prints PASS/FAIL per check.
-- Needs the seeded admin from schema.sql. It creates one test device and removes it.
-- Output appears in the Script Output panel (DBMS_OUTPUT).
-- =============================================================================
DECLARE
  v_status NUMBER;
  v_body   CLOB;
  v_token  VARCHAR2(64);
  v_failed PLS_INTEGER := 0;
  c_dev    CONSTANT VARCHAR2(36) := 'smoke-test-device-0001';

  PROCEDURE check_that(p_name IN VARCHAR2, p_ok IN BOOLEAN) IS
  BEGIN
    IF p_ok THEN
      DBMS_OUTPUT.PUT_LINE('PASS  ' || p_name);
    ELSE
      v_failed := v_failed + 1;
      DBMS_OUTPUT.PUT_LINE('FAIL  ' || p_name);
    END IF;
  END check_that;
BEGIN
  -- Pure helpers
  check_that('email accepts normal address', PKG_API.is_valid_email('admin@hibiscuss.com'));
  check_that('email rejects double dot', NOT PKG_API.is_valid_email('a..b@x.com'));
  check_that('email rejects missing tld', NOT PKG_API.is_valid_email('a@b'));
  check_that('email rejects injection text', NOT PKG_API.is_valid_email('a@b.com'' OR 1=1--'));
  check_that('password needs all five rules', PKG_API.is_valid_password('Abcdef1!'));
  check_that('password rejects no symbol', NOT PKG_API.is_valid_password('Abcdefg1'));
  check_that('password rejects short', NOT PKG_API.is_valid_password('Ab1!'));
  check_that('bearer parses header', PKG_API.bearer_token('Bearer abc123') = 'abc123');
  check_that('bearer rejects other schemes', PKG_API.bearer_token('Basic abc') IS NULL);

  -- Login failures
  PKG_API.login('admin@hibiscuss.com', 'wrong-password', c_dev, 'Smoke', v_status, v_body);
  check_that('wrong password -> 401', v_status = 401);
  check_that('error body has request_id', INSTR(v_body, 'request_id') > 0);
  check_that('error body hides SQL text', INSTR(v_body, 'ORA-') = 0);

  PKG_API.login('not-an-email', 'x', c_dev, 'Smoke', v_status, v_body);
  check_that('bad email -> 400', v_status = 400);

  -- Login success, then /me, logout, /me again
  PKG_API.login('admin@hibiscuss.com', '@Admin', c_dev, 'Smoke', v_status, v_body);
  check_that('admin login -> 200', v_status = 200);
  v_token := JSON_VALUE(v_body, '$.session_token');
  check_that('session token is 64 hex chars', v_token IS NOT NULL AND LENGTH(v_token) = 64);
  check_that('login returns ADMIN role', JSON_EXISTS(v_body, '$.user.roles[*]?(@ == "ADMIN")'));
  check_that('login returns user.invite permission',
             JSON_EXISTS(v_body, '$.user.permissions[*]?(@ == "user.invite")'));

  PKG_API.me('Bearer ' || v_token, v_status, v_body);
  check_that('me with token -> 200', v_status = 200);

  PKG_API.me(NULL, v_status, v_body);
  check_that('me without token -> 401', v_status = 401);

  PKG_API.list_users('Bearer ' || v_token, NULL, v_status, v_body);
  check_that('admin can list pending users', v_status = 200);

  -- Verification code test
  PKG_API.send_verification_code('Bearer ' || v_token, v_status, v_body);
  check_that('send verification code -> 200', v_status = 200);
  check_that('send verification code returns code', JSON_VALUE(v_body, '$.code') IS NOT NULL);

  PKG_API.verify_email(NULL, JSON_VALUE(v_body, '$.code'), 'Bearer ' || v_token, v_status, v_body);
  check_that('verify email with code -> 200', v_status = 200);

  PKG_API.logout('Bearer ' || v_token, v_status, v_body);
  check_that('logout -> 200', v_status = 200);
  PKG_API.me('Bearer ' || v_token, v_status, v_body);
  check_that('token dead after logout -> 401', v_status = 401);

  -- Cleanup (test device and its tokens)
  DELETE FROM sessions WHERE device_id = c_dev;
  DELETE FROM refresh_tokens WHERE device_id = c_dev;
  DELETE FROM devices WHERE id = c_dev;
  UPDATE auth_identities SET failed_attempts = 0, locked_until = NULL;
  COMMIT;

  DBMS_OUTPUT.PUT_LINE(CASE WHEN v_failed = 0 THEN 'ALL CHECKS PASSED'
                            ELSE v_failed || ' CHECK(S) FAILED' END);
END;
/

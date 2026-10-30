-- =============================================================================
-- TRUCKPARK - IAM API LAYER (PKG_API)
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

CREATE OR REPLACE PACKAGE PKG_API AS

  -- Public ----------------------------------------------------------------
  PROCEDURE login(
    p_email IN VARCHAR2, p_password IN VARCHAR2,
    p_device_id IN VARCHAR2, p_device_name IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE refresh(
    p_refresh_token IN VARCHAR2, p_device_id IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE accept_invite(
    p_token IN VARCHAR2, p_full_name IN VARCHAR2, p_password IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE verify_email(
    p_user_id IN VARCHAR2, p_code IN VARCHAR2,
    p_authorization IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE send_verification_code(
    p_authorization IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  -- Signed in (p_authorization is the raw "Authorization" header) ----------
  PROCEDURE change_password(
    p_authorization IN VARCHAR2,
    p_current_password IN VARCHAR2,
    p_new_password IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE logout(
    p_authorization IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE me(
    p_authorization IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  -- Admin -----------------------------------------------------------------
  PROCEDURE create_invite(
    p_authorization IN VARCHAR2, p_email IN VARCHAR2, p_proposed_role IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE list_users(
    p_authorization IN VARCHAR2, p_user_status IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE approve_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_role_code IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE reject_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_reason IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE deactivate_user(
    p_authorization IN VARCHAR2, p_user_id IN VARCHAR2, p_deactivate_at IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  PROCEDURE revoke_device(
    p_authorization IN VARCHAR2, p_device_id IN VARCHAR2, p_reason IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB);

  -- Exposed so tests can check them directly, and because SQL inside the body calls them.
  FUNCTION request_id RETURN VARCHAR2;
  FUNCTION iso(p_ts IN TIMESTAMP WITH TIME ZONE) RETURN VARCHAR2;
  FUNCTION bearer_token(p_authorization IN VARCHAR2) RETURN VARCHAR2;
  FUNCTION is_valid_email(p_email IN VARCHAR2) RETURN BOOLEAN;
  FUNCTION is_valid_password(p_password IN VARCHAR2) RETURN BOOLEAN;
END PKG_API;
/

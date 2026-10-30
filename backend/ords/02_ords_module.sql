-- =============================================================================
-- TRUCKPARK - ORDS MODULE FOR IAM (thin HTTP adapter)
-- Run AFTER schema.sql and 01a/01b. Safe to re-run: it replaces the module.
--
-- Every handler does exactly three things: bind request fields, call one
-- PKG_API procedure, write the (status, body) it returns. No business rules here.
-- Base URL: /ords/truckpark/v1/
-- =============================================================================

-- Only place that knows about HTTP. Replace this package if the gateway ever changes.
CREATE OR REPLACE PACKAGE PKG_API_HTTP AS
  PROCEDURE emit(p_body IN CLOB);
END PKG_API_HTTP;
/

CREATE OR REPLACE PACKAGE BODY PKG_API_HTTP AS
  PROCEDURE emit(p_body IN CLOB) IS
    v_len PLS_INTEGER := NVL(DBMS_LOB.GETLENGTH(p_body), 0);
    v_pos PLS_INTEGER := 1;
    c_chunk CONSTANT PLS_INTEGER := 8000;
  BEGIN
    OWA_UTIL.MIME_HEADER('application/json; charset=utf-8', FALSE);
    -- Tokens and personal data must never be cached by proxies or the client HTTP stack.
    HTP.P('Cache-Control: no-store');
    HTP.P('Pragma: no-cache');
    HTP.P('X-Content-Type-Options: nosniff');
    OWA_UTIL.HTTP_HEADER_CLOSE;
    WHILE v_pos <= v_len LOOP
      HTP.PRN(DBMS_LOB.SUBSTR(p_body, c_chunk, v_pos));
      v_pos := v_pos + c_chunk;
    END LOOP;
  END emit;
END PKG_API_HTTP;
/

-- Turn ORDS on for this schema if not already enabled with truckpark mapping.
BEGIN
  BEGIN
    ORDS.ENABLE_SCHEMA(
      p_enabled             => TRUE,
      p_schema              => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
      p_url_mapping_type    => 'BASE_PATH',
      p_url_mapping_pattern => 'truckpark',
      p_auto_rest_auth      => FALSE);
  EXCEPTION
    WHEN OTHERS THEN
      -- ORA-20049: Schema is already enabled. If mapping is already active, proceed safely.
      IF SQLCODE != -20049 THEN
        RAISE;
      END IF;
  END;
  COMMIT;
END;
/

DECLARE
  c_module CONSTANT VARCHAR2(30) := 'truckpark_v1';

  PROCEDURE add_handler(p_pattern IN VARCHAR2, p_method IN VARCHAR2, p_call IN VARCHAR2) IS
  BEGIN
    ORDS.DEFINE_TEMPLATE(p_module_name => c_module, p_pattern => p_pattern);
    ORDS.DEFINE_HANDLER(
      p_module_name    => c_module,
      p_pattern        => p_pattern,
      p_method         => p_method,
      p_source_type    => ORDS.SOURCE_TYPE_PLSQL,
      p_items_per_page => 0,
      p_source         =>
        'DECLARE v_s NUMBER; v_b CLOB; BEGIN ' || p_call ||
        ' :status_code := v_s; PKG_API_HTTP.emit(v_b); END;');
  END add_handler;
BEGIN
  BEGIN
    ORDS.DELETE_MODULE(p_module_name => c_module);
  EXCEPTION
    WHEN OTHERS THEN NULL;  -- first run: nothing to delete
  END;

  ORDS.DEFINE_MODULE(
    p_module_name    => c_module,
    p_base_path      => '/v1/',
    p_items_per_page => 0,
    p_status         => 'PUBLISHED');

  -- Public ------------------------------------------------------------------
  add_handler('auth/login', 'POST',
    'PKG_API.login(:email, :password, :device_id, :device_name, v_s, v_b);');
  add_handler('auth/refresh', 'POST',
    'PKG_API.refresh(:refresh_token, :device_id, v_s, v_b);');
  add_handler('auth/accept-invite', 'POST',
    'PKG_API.accept_invite(:token, :full_name, :password, v_s, v_b);');
  add_handler('auth/verify-email', 'POST',
    'PKG_API.verify_email(:user_id, :code, :authorization, v_s, v_b);');

  -- Signed in ---------------------------------------------------------------
  add_handler('auth/logout', 'POST',
    'PKG_API.logout(:authorization, v_s, v_b);');
  add_handler('auth/change-password', 'POST',
    'PKG_API.change_password(:authorization, :current_password, :new_email, :new_password, v_s, v_b);');
  add_handler('auth/send-verification-code', 'POST',
    'PKG_API.send_verification_code(:authorization, v_s, v_b);');
  add_handler('me', 'GET',
    'PKG_API.me(:authorization, v_s, v_b);');

  -- Admin (permission checks happen inside the packages) --------------------
  add_handler('admin/invites', 'POST',
    'PKG_API.create_invite(:authorization, :email, :proposed_role, v_s, v_b);');
  add_handler('admin/users', 'GET',
    'PKG_API.list_users(:authorization, :status, v_s, v_b);');
  add_handler('admin/users/:id/approve', 'POST',
    'PKG_API.approve_user(:authorization, :id, :role_code, v_s, v_b);');
  add_handler('admin/users/:id/reject', 'POST',
    'PKG_API.reject_user(:authorization, :id, :reason, v_s, v_b);');
  add_handler('admin/users/:id/deactivate', 'POST',
    'PKG_API.deactivate_user(:authorization, :id, :deactivate_at, v_s, v_b);');
  add_handler('admin/devices/:id/revoke', 'POST',
    'PKG_API.revoke_device(:authorization, :id, :reason, v_s, v_b);');

  COMMIT;
END;
/

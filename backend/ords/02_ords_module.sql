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

-- Ensure users.phone column exists on older database deployments.
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE users ADD (phone VARCHAR2(40))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN -- ORA-01430: column being added already exists in table
      RAISE;
    END IF;
END;
/

-- Ensure truck_visits columns and indexes exist on existing deployments without migrations.
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE truck_visits ADD (
      country_code      VARCHAR2(10) DEFAULT ''ZM'',
      front_plate       VARCHAR2(20),
      horse_plate       VARCHAR2(20),
      transporter       VARCHAR2(100),
      cargo_description VARCHAR2(500),
      cargo_items_json  CLOB,
      seals_json        CLOB
    )';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE != -1430 THEN NULL; END IF;
  END;

  BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE truck_visits ADD (
      cargo_description VARCHAR2(500),
      cargo_items_json  CLOB,
      seals_json        CLOB
    )';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE != -1430 THEN NULL; END IF;
  END;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE visit_corrections (
      id              VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
      visit_id        VARCHAR2(36) NOT NULL REFERENCES truck_visits(id),
      edited_by       VARCHAR2(36) NOT NULL REFERENCES users(id),
      edited_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
      reason          VARCHAR2(500),
      changes_summary VARCHAR2(1000) NOT NULL,
      was_critical    CHAR(1) DEFAULT ''N'' NOT NULL,
      before_json     CLOB,
      after_json      CLOB
    )';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE != -955 THEN NULL; END IF;
  END;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_visit_corrections_visit ON visit_corrections(visit_id, edited_at)';
  EXCEPTION WHEN OTHERS THEN NULL; END;

  UPDATE truck_visits
     SET front_plate  = NVL(front_plate, plate_number),
         horse_plate  = NVL(horse_plate, plate_number),
         country_code = NVL(country_code, 'ZM')
   WHERE front_plate IS NULL OR horse_plate IS NULL;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_visits_front_plate ON truck_visits(front_plate, status)';
  EXCEPTION WHEN OTHERS THEN NULL; END;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_visits_horse_plate ON truck_visits(horse_plate, status)';
  EXCEPTION WHEN OTHERS THEN NULL; END;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_visits_transporter ON truck_visits(transporter)';
  EXCEPTION WHEN OTHERS THEN NULL; END;

  BEGIN
    EXECUTE IMMEDIATE 'INSERT INTO role_permissions VALUES (''RUNNER'', ''visit.override_open'')';
  EXCEPTION WHEN OTHERS THEN NULL; END;

  COMMIT;
END;
/

-- Allow outbound TLS/SMTP connections from Oracle Autonomous Database to Brevo.
BEGIN
  BEGIN
    DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
      host       => 'smtp-relay.brevo.com',
      lower_port => 587,
      upper_port => 587,
      ace        => xs$ace_type(
                      privilege_list => xs$name_list('smtp', 'connect', 'resolve'),
                      principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                      principal_type => xs_acl.ptype_db));
  EXCEPTION
    WHEN OTHERS THEN NULL;
  END;

  BEGIN
    DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
      host       => 'api.brevo.com',
      lower_port => 443,
      upper_port => 443,
      ace        => xs$ace_type(
                      privilege_list => xs$name_list('http', 'connect', 'resolve'),
                      principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                      principal_type => xs_acl.ptype_db));
  EXCEPTION
    WHEN OTHERS THEN NULL;
  END;
  COMMIT;
END;
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

    -- Explicitly bind the HTTP Authorization header for ORDS handlers that require it
    IF INSTR(p_call, ':authorization') > 0 THEN
      ORDS.DEFINE_PARAMETER(
        p_module_name        => c_module,
        p_pattern            => p_pattern,
        p_method             => p_method,
        p_name               => 'authorization',
        p_bind_variable_name => 'authorization',
        p_source_type        => 'HEADER',
        p_param_type         => 'STRING',
        p_access_method      => 'IN');
    END IF;
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
    'PKG_API.accept_invite(:token, :full_name, :password, :device_id, v_s, v_b);');
  add_handler('auth/check-email', 'POST',
    'PKG_API.check_email(:email, :device_id, v_s, v_b);');
  add_handler('auth/request-signup-code', 'POST',
    'PKG_API.request_signup_code(:email, :full_name, :device_id, v_s, v_b);');
  add_handler('auth/register', 'POST',
    'PKG_API.register(:full_name, :email, :phone, :password, :code, :device_id, v_s, v_b);');
  add_handler('auth/verify-email', 'POST',
    'PKG_API.verify_email(:user_id, :code, :authorization, v_s, v_b);');
  add_handler('auth/request-password-reset', 'POST',
    'PKG_API.request_password_reset(:email, :device_id, v_s, v_b);');
  add_handler('auth/reset-password', 'POST',
    'PKG_API.reset_password(:email, :code, :new_password, :device_id, v_s, v_b);');

  -- Signed in ---------------------------------------------------------------
  add_handler('auth/logout', 'POST',
    'PKG_API.logout(:authorization, v_s, v_b);');
  add_handler('auth/change-password', 'POST',
    'PKG_API.change_password(:authorization, :new_email, :new_password, :code, :full_name, :phone, :current_password, v_s, v_b);');
  add_handler('auth/send-verification-code', 'POST',
    'PKG_API.send_verification_code(:authorization, :email, v_s, v_b);');
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
  add_handler('admin/devices', 'GET',
    'PKG_API.list_devices(:authorization, v_s, v_b);');
  add_handler('admin/devices/:id/revoke', 'POST',
    'PKG_API.revoke_device(:authorization, :id, :reason, v_s, v_b);');
  add_handler('admin/devices/:id/reinstate', 'POST',
    'PKG_API.reinstate_device(:authorization, :id, v_s, v_b);');
  add_handler('admin/users/:id/permissions/exceptions', 'GET',
    'PKG_API.list_user_exceptions(:authorization, :id, v_s, v_b);');
  add_handler('admin/users/:id/permissions/exceptions', 'POST',
    'PKG_API.add_user_exception(:authorization, :id, :permission_code, :grant_or_remove, :reason, :expires_in_hours, v_s, v_b);');
  add_handler('admin/permissions/exceptions/:id/revoke', 'POST',
    'PKG_API.revoke_user_exception(:authorization, :id, v_s, v_b);');

  -- Truck visits (Milestone 3–5) --------------------------------------------
  add_handler('visits', 'GET',
    'PKG_API.list_visits(:authorization, :visit_status, v_s, v_b);');
  add_handler('visits/arrive', 'POST',
    'PKG_API.record_arrival(:authorization, :visit_id, :plate_number, :visit_type, :driver_name, :driver_phone, :notes, :arrived_at, :allow_duplicate, :country_code, :front_plate, :horse_plate, :transporter, :cargo_description, :cargo_items_json, :seals_json, v_s, v_b);');
  add_handler('visits/:id/depart', 'POST',
    'PKG_API.record_departure(:authorization, :id, :departed_at, :notes, v_s, v_b);');
  add_handler('visits/:id/correct', 'POST',
    'PKG_API.correct_visit(:authorization, :id, :plate_number, :country_code, :front_plate, :horse_plate, :transporter, :visit_type, :driver_name, :driver_phone, :arrival_notes, :departure_notes, :cargo_description, :cargo_items_json, :seals_json, :reason, :expected_row_version, v_s, v_b);');
  add_handler('visits/:id/history', 'GET',
    'PKG_API.list_visit_history(:authorization, :id, v_s, v_b);');

  COMMIT;
END;
/

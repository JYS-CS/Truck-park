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

  -- =========================================================================
  -- BREVO TRANSACTIONAL EMAIL CONFIGURATION (Server-Side Email Dispatch)
  -- Copy your Brevo credentials from .env here before running in Oracle:
  -- =========================================================================
  C_SMTP_HOST         CONSTANT VARCHAR2(200) := 'smtp-relay.brevo.com';
  C_SMTP_PORT         CONSTANT PLS_INTEGER   := 587;
  C_SMTP_LOGIN        CONSTANT VARCHAR2(200) := 'YOUR_BREVO_SMTP_LOGIN'; -- Set your BREVO_SMTP_LOGIN (e.g. 9b8c...@smtp-brevo.com)
  C_SMTP_KEY          CONSTANT VARCHAR2(200) := 'YOUR_BREVO_API_KEY'; -- Set your BREVO_SMTP_KEY (xsmtpsib-... or xkeysib-...)
  C_SMTP_SENDER_EMAIL CONSTANT VARCHAR2(200) := 'YOUR_BREVO_SENDER_EMAIL'; -- Set your BREVO_SENDER_EMAIL
  C_SMTP_SENDER_NAME  CONSTANT VARCHAR2(200) := 'Hibiscus TruckPark';
  C_ADB_WALLET_PATH   CONSTANT VARCHAR2(200) := 'file:/var/opt/oracle/dbaas_acfs/grid/tcps_wallet';

  -- ---------------------------------------------------------------- helpers

  FUNCTION generate_six_digit_code RETURN VARCHAR2 IS
  BEGIN
    RETURN LPAD(TO_CHAR(MOD(TO_NUMBER(RAWTOHEX(DBMS_CRYPTO.RANDOMBYTES(4)), 'XXXXXXXX'), 900000) + 100000), 6, '0');
  END generate_six_digit_code;

  FUNCTION base64_single_line(p_value IN VARCHAR2) RETURN VARCHAR2 IS
  BEGIN
    RETURN REPLACE(
             REPLACE(
               UTL_RAW.CAST_TO_VARCHAR2(
                 UTL_ENCODE.BASE64_ENCODE(UTL_RAW.CAST_TO_RAW(p_value))),
               CHR(13), ''),
             CHR(10), '');
  END base64_single_line;

  PROCEDURE send_smtp_with_wallet(
    p_wallet_path  IN VARCHAR2,
    p_from_email   IN VARCHAR2,
    p_to_email     IN VARCHAR2,
    p_mime_message IN CLOB
  ) IS
    v_conn   UTL_SMTP.CONNECTION;
    v_reply  UTL_SMTP.REPLY;
    v_len    PLS_INTEGER := DBMS_LOB.GETLENGTH(p_mime_message);
    v_offset PLS_INTEGER := 1;
    c_chunk  CONSTANT PLS_INTEGER := 4000;
  BEGIN
    IF p_wallet_path IS NOT NULL THEN
      v_conn := UTL_SMTP.OPEN_CONNECTION(
        host                          => C_SMTP_HOST,
        port                          => C_SMTP_PORT,
        tx_timeout                    => 20,
        wallet_path                   => p_wallet_path,
        secure_connection_before_smtp => FALSE);
    ELSE
      v_conn := UTL_SMTP.OPEN_CONNECTION(
        host                          => C_SMTP_HOST,
        port                          => C_SMTP_PORT,
        tx_timeout                    => 20,
        secure_connection_before_smtp => FALSE);
    END IF;

    UTL_SMTP.EHLO(v_conn, 'hibiscustruckpark.com');
    UTL_SMTP.STARTTLS(v_conn);
    UTL_SMTP.EHLO(v_conn, 'hibiscustruckpark.com');

    v_reply := UTL_SMTP.COMMAND(v_conn, 'AUTH LOGIN');
    IF v_reply.code != 334 THEN
      RAISE_APPLICATION_ERROR(-20016, 'SMTP AUTH LOGIN rejected (' || v_reply.code || ')');
    END IF;

    v_reply := UTL_SMTP.COMMAND(v_conn, base64_single_line(TRIM(C_SMTP_LOGIN)));
    IF v_reply.code != 334 THEN
      RAISE_APPLICATION_ERROR(-20016, 'SMTP username rejected (' || v_reply.code || ')');
    END IF;

    v_reply := UTL_SMTP.COMMAND(v_conn, base64_single_line(TRIM(C_SMTP_KEY)));
    IF v_reply.code != 235 THEN
      RAISE_APPLICATION_ERROR(-20016, 'SMTP authentication failed (' || v_reply.code || ')');
    END IF;

    UTL_SMTP.MAIL(v_conn, p_from_email);
    UTL_SMTP.RCPT(v_conn, p_to_email);
    UTL_SMTP.OPEN_DATA(v_conn);

    WHILE v_offset <= v_len LOOP
      UTL_SMTP.WRITE_DATA(v_conn, DBMS_LOB.SUBSTR(p_mime_message, c_chunk, v_offset));
      v_offset := v_offset + c_chunk;
    END LOOP;

    UTL_SMTP.CLOSE_DATA(v_conn);
    BEGIN
      UTL_SMTP.QUIT(v_conn);
    EXCEPTION
      WHEN OTHERS THEN NULL;
    END;
  EXCEPTION
    WHEN OTHERS THEN
      BEGIN
        UTL_SMTP.QUIT(v_conn);
      EXCEPTION
        WHEN OTHERS THEN NULL;
      END;
      RAISE;
  END send_smtp_with_wallet;

  PROCEDURE dispatch_transactional_email(
    p_outbox_id      IN VARCHAR2,
    p_to_email       IN VARCHAR2,
    p_recipient_name IN VARCHAR2 DEFAULT NULL,
    p_subject        IN VARCHAR2,
    p_heading        IN VARCHAR2,
    p_copy           IN VARCHAR2,
    p_badge_label    IN VARCHAR2,
    p_badge_value    IN VARCHAR2,
    p_footer_note    IN VARCHAR2,
    p_best_effort    IN BOOLEAN DEFAULT FALSE
  ) IS
    v_from_email    VARCHAR2(320) := TRIM(C_SMTP_SENDER_EMAIL);
    v_to_email      VARCHAR2(320) := LOWER(TRIM(p_to_email));
    v_greeting_name VARCHAR2(200) := NVL(NULLIF(TRIM(p_recipient_name), ''), 'there');
    v_plain_body    CLOB;
    v_html_body     CLOB;
    v_mime_body     CLOB;
    v_boundary      VARCHAR2(80) := '----=_Part_' || LOWER(RAWTOHEX(SYS_GUID()));
    v_crlf          CONSTANT VARCHAR2(2) := CHR(13) || CHR(10);
    v_err           VARCHAR2(500);
    v_req           UTL_HTTP.REQ;
    v_resp          UTL_HTTP.RESP;
    v_json_payload  CLOB;
  BEGIN
    IF TRIM(C_SMTP_KEY) IS NULL OR v_from_email IS NULL THEN
      IF p_best_effort THEN
        RETURN;
      END IF;
      UPDATE email_outbox
         SET status = 'FAILED',
             last_error = 'Brevo credentials not configured in PKG_API',
             updated_at = SYSTIMESTAMP
       WHERE id = p_outbox_id;
      RAISE_APPLICATION_ERROR(
        E_INVALID_REQUEST,
        'Server email service is not configured. Set C_SMTP_LOGIN, C_SMTP_KEY, and C_SMTP_SENDER_EMAIL in 01b_pkg_api_body.sql.');
    END IF;

    v_plain_body :=
      'Hello ' || v_greeting_name || ',' || v_crlf || v_crlf ||
      p_copy || v_crlf || v_crlf ||
      p_badge_label || ': ' || p_badge_value || v_crlf || v_crlf ||
      p_footer_note || v_crlf || v_crlf ||
      '-- Hibiscus TruckPark Security' || v_crlf;

    v_html_body :=
      q'[<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<meta http-equiv="X-UA-Compatible" content="IE=edge" />
<meta name="x-apple-disable-message-reformatting" />
<meta name="color-scheme" content="light dark" />
<meta name="supported-color-schemes" content="light dark" />
<!--[if mso]>
<noscript><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml></noscript>
<![endif]-->
<style>
  :root { color-scheme: light dark; supported-color-schemes: light dark; }
  body, table, td, a { -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; }
  table, td { mso-table-lspace:0pt; mso-table-rspace:0pt; }
  body { margin:0 !important; padding:0 !important; width:100% !important; background-color:transparent; }
  @media only screen and (max-width:520px) {
    .px { padding-left:16px !important; padding-right:16px !important; }
    .card-px { padding:28px 20px !important; }
    .h1 { font-size:21px !important; line-height:28px !important; }
    .code { font-size:24px !important; letter-spacing:3px !important; }
  }
  @media (prefers-color-scheme: dark) {
    .card { background-color:#161E2E !important; border-color:#2B3A55 !important; box-shadow:0 8px 30px rgba(0,0,0,0.45) !important; }
    .accent { background-color:#38BDF8 !important; }
    .brand { color:#7DD3FC !important; }
    .h1 { color:#F8FAFC !important; }
    .copy { color:#CBD5E1 !important; }
    .badge { background-color:#052E16 !important; border-color:#166534 !important; }
    .badge-l { color:#86EFAC !important; }
    .code { color:#4ADE80 !important; }
    .hint { color:#94A3B8 !important; }
    .divider { border-top-color:#2B3A55 !important; }
    .note { color:#94A3B8 !important; }
  }
  [data-ogsc] .h1 { color:#F8FAFC !important; }
  [data-ogsc] .copy { color:#CBD5E1 !important; }
  [data-ogsc] .code { color:#4ADE80 !important; }
  [data-ogsc] .note { color:#94A3B8 !important; }
  [data-ogsb] .card { background-color:#161E2E !important; }
  [data-ogsb] .badge { background-color:#052E16 !important; }
</style>
</head>
<body style="margin:0;padding:0;background-color:transparent;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
<div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;">]'
      || p_heading ||
      q'[&#847;&zwnj;&nbsp;&#847;&zwnj;&nbsp;&#847;&zwnj;&nbsp;</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:transparent;">
<tr><td align="center" class="px" style="padding:32px 24px;">
  <!--[if mso]><table role="presentation" width="480" align="center" cellspacing="0" cellpadding="0" border="0"><tr><td><![endif]-->
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="card"
         style="max-width:480px;width:100%;background-color:#FFFFFF;border:1px solid #CBD5E1;border-radius:20px;box-shadow:0 8px 30px rgba(15,23,42,0.10);">
    <tr><td class="accent" height="5" style="height:5px;line-height:5px;font-size:0;background-color:#0369A1;border-radius:20px 20px 0 0;">&nbsp;</td></tr>
    <tr><td align="center" class="card-px" style="padding:36px 32px 40px 32px;">
      <div class="brand" style="font-size:12px;font-weight:700;letter-spacing:3px;line-height:16px;color:#0369A1;text-transform:uppercase;margin:0 0 12px 0;">HIBISCUS TRUCKPARK</div>
      <h1 class="h1" style="margin:0 0 14px 0;font-size:24px;line-height:32px;font-weight:700;color:#0F172A;">]'
      || p_heading ||
      q'[</h1>
      <p class="copy" style="margin:0 0 28px 0;font-size:15px;line-height:24px;color:#334155;">Hello ]'
      || v_greeting_name || q'[,<br/>]' || p_copy ||
      q'[</p>
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="badge"
             style="background-color:#F0FDF4;border:1px solid #86EFAC;border-radius:14px;margin:0 0 28px 0;">
        <tr><td align="center" style="padding:20px 16px 16px 16px;">
          <div class="badge-l" style="font-size:11px;font-weight:700;letter-spacing:1.5px;line-height:16px;color:#166534;text-transform:uppercase;margin:0 0 8px 0;">]'
      || p_badge_label ||
      q'[</div>
          <div class="code" style="font-size:30px;line-height:38px;font-weight:800;letter-spacing:5px;color:#15803D;font-family:'SF Mono',SFMono-Regular,Menlo,Consolas,'Courier New',monospace;word-break:break-all;">]'
      || p_badge_value ||
      q'[</div>
        </td></tr>
      </table>
      <div class="divider" style="border-top:1px solid #CBD5E1;font-size:0;line-height:0;height:1px;margin:0 0 20px 0;">&nbsp;</div>
      <p class="note" style="margin:0;font-size:12px;line-height:19px;color:#64748B;">]'
      || p_footer_note ||
      q'[</p>
    </td></tr>
  </table>
  <!--[if mso]></td></tr></table><![endif]-->
  <p class="note" style="max-width:480px;margin:20px auto 0 auto;font-size:11px;line-height:16px;color:#64748B;text-align:center;">
    &copy; Hibiscus Truckpark. All rights reserved.
  </p>
</td></tr>
</table>
</body>
</html>]';

    BEGIN
      IF LOWER(TRIM(C_SMTP_KEY)) LIKE 'xkeysib-%' THEN
        SELECT JSON_OBJECT(
                 'sender'      VALUE JSON_OBJECT('name' VALUE C_SMTP_SENDER_NAME, 'email' VALUE v_from_email),
                 'to'          VALUE JSON_ARRAY(JSON_OBJECT('email' VALUE v_to_email, 'name' VALUE v_greeting_name)),
                 'subject'     VALUE p_subject,
                 'textContent' VALUE v_plain_body,
                 'htmlContent' VALUE v_html_body
                 RETURNING CLOB)
          INTO v_json_payload FROM dual;

        v_req := UTL_HTTP.BEGIN_REQUEST('https://api.brevo.com/v3/smtp/email', 'POST', 'HTTP/1.1');
        UTL_HTTP.SET_HEADER(v_req, 'api-key', TRIM(C_SMTP_KEY));
        UTL_HTTP.SET_HEADER(v_req, 'Content-Type', 'application/json; charset=utf-8');
        UTL_HTTP.SET_HEADER(v_req, 'Accept', 'application/json');
        UTL_HTTP.SET_HEADER(v_req, 'Content-Length', TO_CHAR(DBMS_LOB.GETLENGTH(v_json_payload)));

        DECLARE
          v_buf_size   CONSTANT PLS_INTEGER := 4000;
          v_buf_offset PLS_INTEGER := 1;
          v_buf_len    PLS_INTEGER := DBMS_LOB.GETLENGTH(v_json_payload);
        BEGIN
          WHILE v_buf_offset <= v_buf_len LOOP
            UTL_HTTP.WRITE_TEXT(v_req, DBMS_LOB.SUBSTR(v_json_payload, v_buf_size, v_buf_offset));
            v_buf_offset := v_buf_offset + v_buf_size;
          END LOOP;
        END;

        v_resp := UTL_HTTP.GET_RESPONSE(v_req);
        UTL_HTTP.END_RESPONSE(v_resp);

        IF v_resp.status_code NOT BETWEEN 200 AND 299 THEN
          RAISE_APPLICATION_ERROR(-20016, 'Brevo HTTP API returned status ' || v_resp.status_code);
        END IF;
      ELSE
        IF TRIM(C_SMTP_LOGIN) IS NULL THEN
          RAISE_APPLICATION_ERROR(E_INVALID_REQUEST, 'C_SMTP_LOGIN is required when using a Brevo SMTP key.');
        END IF;

        v_mime_body :=
          'From: "' || C_SMTP_SENDER_NAME || '" <' || v_from_email || '>' || v_crlf ||
          'To: <' || v_to_email || '>' || v_crlf ||
          'Subject: ' || p_subject || v_crlf ||
          'MIME-Version: 1.0' || v_crlf ||
          'Content-Type: multipart/alternative; boundary="' || v_boundary || '"' || v_crlf ||
          v_crlf ||
          '--' || v_boundary || v_crlf ||
          'Content-Type: text/plain; charset=UTF-8' || v_crlf ||
          'Content-Transfer-Encoding: 8bit' || v_crlf ||
          v_crlf ||
          v_plain_body || v_crlf ||
          '--' || v_boundary || v_crlf ||
          'Content-Type: text/html; charset=UTF-8' || v_crlf ||
          'Content-Transfer-Encoding: 8bit' || v_crlf ||
          v_crlf ||
          v_html_body || v_crlf ||
          '--' || v_boundary || '--' || v_crlf;

        BEGIN
          send_smtp_with_wallet(NULL, v_from_email, v_to_email, v_mime_body);
        EXCEPTION
          WHEN OTHERS THEN
            send_smtp_with_wallet(C_ADB_WALLET_PATH, v_from_email, v_to_email, v_mime_body);
        END;
      END IF;

      UPDATE email_outbox
         SET status = 'SENT',
             updated_at = SYSTIMESTAMP
       WHERE id = p_outbox_id;
    EXCEPTION
      WHEN OTHERS THEN
        v_err := SUBSTR(SQLERRM, 1, 450);
        UPDATE email_outbox
           SET status = 'FAILED',
               last_error = v_err,
               updated_at = SYSTIMESTAMP
         WHERE id = p_outbox_id;
        IF NOT p_best_effort THEN
          RAISE_APPLICATION_ERROR(
            E_INVALID_REQUEST,
            'Unable to dispatch verification email at this time. Please try again later.');
        END IF;
    END;
  END dispatch_transactional_email;

  PROCEDURE dispatch_verification_email(
    p_outbox_id      IN VARCHAR2,
    p_to_email       IN VARCHAR2,
    p_code           IN VARCHAR2,
    p_recipient_name IN VARCHAR2 DEFAULT NULL,
    p_is_admin_setup IN BOOLEAN DEFAULT FALSE
  ) IS
    v_greeting_name VARCHAR2(200) := NVL(NULLIF(TRIM(p_recipient_name), ''),
                                         CASE WHEN p_is_admin_setup THEN 'Administrator' ELSE 'there' END);
  BEGIN
    dispatch_transactional_email(
      p_outbox_id      => p_outbox_id,
      p_to_email       => p_to_email,
      p_recipient_name => v_greeting_name,
      p_subject        => CASE WHEN p_is_admin_setup
                               THEN p_code || ' is your Hibiscus TruckPark Admin verification code'
                               ELSE p_code || ' is your Hibiscus TruckPark verification code' END,
      p_heading        => CASE WHEN p_is_admin_setup
                               THEN 'Admin Account Security Setup'
                               ELSE 'Verify Your Email Address' END,
      p_copy           => CASE WHEN p_is_admin_setup
                               THEN 'Use the 6-digit verification code below to confirm your official administrator email address and complete your security setup.'
                               ELSE 'Use the 6-digit verification code below to verify your email address and submit your Hibiscus TruckPark account registration.' END,
      p_badge_label    => '6-DIGIT VERIFICATION CODE',
      p_badge_value    => p_code,
      p_footer_note    => 'This code is valid for 15 minutes. Never share this verification code with anyone.',
      p_best_effort    => FALSE);
  END dispatch_verification_email;

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
    IF v_h IS NULL THEN
      BEGIN
        v_h := TRIM(OWA_UTIL.GET_CGI_ENV('HTTP_AUTHORIZATION'));
      EXCEPTION
        WHEN OTHERS THEN
          BEGIN
            v_h := TRIM(OWA_UTIL.GET_CGI_ENV('AUTHORIZATION'));
          EXCEPTION
            WHEN OTHERS THEN v_h := NULL;
          END;
      END;
    END IF;

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
             'phone'          VALUE u.phone,
             'status'         VALUE u.status,
             'email_verified' VALUE CASE WHEN u.email_verified_at IS NOT NULL AND LOWER(u.email) != 'admin@hibiscuss.com' THEN 'true' ELSE 'false' END FORMAT JSON,
             'roles'          VALUE COALESCE(
                 (SELECT JSON_ARRAYAGG(ur.role_code ORDER BY ur.role_code) FROM user_roles ur WHERE ur.user_id = u.id),
                 '[]') FORMAT JSON,
             'permissions'    VALUE v_per FORMAT JSON,
             'must_change_password' VALUE CASE WHEN EXISTS (
                 SELECT 1 FROM auth_identities ai
                  WHERE ai.user_id = u.id AND ai.provider = 'PASSWORD'
                    AND (ai.password_must_change = 'Y'
                         OR LOWER(u.email) = 'admin@hibiscuss.com'
                         OR ai.password_hash = PKG_CRYPTO.hash_password('@Admin', ai.password_salt, ai.password_iters)))
               THEN 'true' ELSE 'false' END FORMAT JSON
             RETURNING CLOB)
      INTO v
      FROM users u
     WHERE u.id = p_user_id;
    RETURN v;
  END user_json;

  -- Autonomous transaction logger to persist security and system exceptions
  -- without being affected by the caller's ROLLBACK.
  PROCEDURE log_error(
    p_code       IN NUMBER,
    p_message    IN VARCHAR2,
    p_request_id IN VARCHAR2,
    p_action     IN VARCHAR2 DEFAULT 'API_ERROR'
  ) IS
    PRAGMA AUTONOMOUS_TRANSACTION;
  BEGIN
    INSERT INTO audit_events (
      action,
      reason,
      after_json,
      occurred_at
    ) VALUES (
      p_action,
      SUBSTR(p_message, 1, 500),
      JSON_OBJECT(
        'request_id' VALUE p_request_id,
        'sqlcode'    VALUE p_code,
        'error'      VALUE SUBSTR(p_message, 1, 1000)
      ),
      SYSTIMESTAMP
    );
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN
      ROLLBACK;
  END log_error;

  -- Turns any raised error into (status, JSON).
  -- Sensitive stack traces, internal ORA errors, and external provider failures
  -- are logged to audit_events autonomously, while the client receives a clean generic response.
  PROCEDURE fail(p_code IN PLS_INTEGER, p_msg IN VARCHAR2,
                 p_status OUT NUMBER, p_body OUT CLOB) IS
    v_req_id VARCHAR2(36) := request_id;
    v_name   VARCHAR2(40);
    v_text   VARCHAR2(500);
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

    -- Log the raw exception and backtrace to the audit trail
    log_error(p_code, p_msg, v_req_id, 'API_EXCEPTION_' || v_name);

    -- Sanitize client-facing messages:
    -- Known business exceptions (-20002 to -20016) return clear, safe error messages to users.
    -- Unhandled internal exceptions or provider errors are sanitized.
    IF p_code BETWEEN -20016 AND -20002 THEN
      v_text := REGEXP_REPLACE(p_msg, '^ORA-[0-9]+: ', '');
    ELSIF p_code = -1 THEN
      v_text := 'An account or record with these details already exists.';
    ELSIF p_code = -2291 THEN
      v_text := 'Referenced record was not found.';
    ELSE
      v_text := 'An unexpected error occurred. Quote request ID: ' || v_req_id || ' if contacting support.';
    END IF;

    -- Counters and revocations that must survive the failure are kept; everything else is undone.
    IF p_code IN (-20011, -20012, -20013, -20015) THEN
      COMMIT;
    ELSE
      ROLLBACK;
    END IF;

    SELECT JSON_OBJECT('code' VALUE v_name, 'message' VALUE v_text,
                       'request_id' VALUE v_req_id RETURNING CLOB)
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

    -- Only approved staff get a working session. Others hold no token and receive a generic 401
    -- so attackers cannot enumerate account statuses.
    IF v_ustat != 'APPROVED' THEN
      PKG_AUTH.logout(v_sess);
      UPDATE refresh_tokens SET revoked_at = SYSTIMESTAMP
       WHERE token_hash = PKG_CRYPTO.hash_sha256(v_ref);
      COMMIT;
      RAISE_APPLICATION_ERROR(-20011, 'Invalid email or password');
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

  PROCEDURE check_email(
    p_email IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_email VARCHAR2(320) := LOWER(TRIM(p_email));
    v_count NUMBER;
  BEGIN
    IF NOT is_valid_email(v_email) THEN
      bad_request('A valid email is required');
    END IF;

    SELECT COUNT(*) INTO v_count FROM users WHERE LOWER(email) = v_email;
    IF v_count > 0 OR v_email = 'admin@hibiscuss.com' THEN
      p_status := 409;
      SELECT JSON_OBJECT(
               'available'  VALUE false,
               'code'       VALUE 'ALREADY_EXISTS',
               'message'    VALUE 'An account with this email already exists.',
               'request_id' VALUE request_id
               RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    p_status := 200;
    SELECT JSON_OBJECT(
             'available'  VALUE true,
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END check_email;

  PROCEDURE request_signup_code(
    p_email IN VARCHAR2, p_full_name IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_email     VARCHAR2(320) := LOWER(TRIM(p_email));
    v_count     NUMBER;
    v_recent    NUMBER;
    v_code      VARCHAR2(6);
    v_code_hash VARCHAR2(64);
    v_outbox_id VARCHAR2(36);
    v_payload   CLOB;
  BEGIN
    IF NOT is_valid_email(v_email) THEN
      bad_request('A valid email is required');
    END IF;

    -- Reject immediately if this email already exists in the database before sending any code
    SELECT COUNT(*) INTO v_count
      FROM users
     WHERE LOWER(email) = v_email
       AND status != 'PENDING_VERIFICATION';
    IF v_count > 0 OR v_email = 'admin@hibiscuss.com' THEN
      p_status := 409;
      SELECT JSON_OBJECT(
               'code'       VALUE 'ALREADY_EXISTS',
               'message'    VALUE 'An account with this email already exists.',
               'request_id' VALUE request_id
               RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    -- Rate limit verification code requests per email per 15-minute window
    SELECT COUNT(*) INTO v_recent
      FROM email_outbox
     WHERE to_address = v_email
       AND template = 'VERIFY_SIGNUP'
       AND created_at > SYSTIMESTAMP - INTERVAL '15' MINUTE;
    IF v_recent >= 10 THEN
      RAISE_APPLICATION_ERROR(E_INVALID_REQUEST, 'Please wait a few minutes before requesting another code');
    END IF;

    v_code      := generate_six_digit_code();
    v_code_hash := PKG_CRYPTO.hash_sha256(v_code);
    v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));

    -- Supersede any previous active sign-up verification codes for this email
    UPDATE email_outbox
       SET row_version = 0,
           updated_at  = SYSTIMESTAMP
     WHERE to_address = v_email
       AND template = 'VERIFY_SIGNUP'
       AND row_version = 1;

    SELECT JSON_OBJECT(
             'code_hash'      VALUE v_code_hash,
             'recipient_name' VALUE TRIM(p_full_name)
             RETURNING CLOB)
      INTO v_payload FROM dual;

    -- Store only the SHA-256 hash and 15-minute expiry in email_outbox; users and auth_identities remain untouched
    INSERT INTO email_outbox (
      id, template, to_address, payload, status, attempts, next_try_at, row_version
    ) VALUES (
      v_outbox_id, 'VERIFY_SIGNUP', v_email, v_payload, 'SENDING', 0,
      SYSTIMESTAMP + INTERVAL '15' MINUTE, 1
    );

    dispatch_verification_email(
      p_outbox_id      => v_outbox_id,
      p_to_email       => v_email,
      p_code           => v_code,
      p_recipient_name => TRIM(p_full_name),
      p_is_admin_setup => FALSE);

    SELECT JSON_OBJECT(
             'status'     VALUE 'SENT',
             'email'      VALUE v_email,
             'message'    VALUE 'Verification code sent to ' || v_email,
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END request_signup_code;

  PROCEDURE register(
    p_full_name IN VARCHAR2, p_email IN VARCHAR2, p_phone IN VARCHAR2 DEFAULT NULL,
    p_password IN VARCHAR2, p_code IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_email          VARCHAR2(320) := LOWER(TRIM(p_email));
    v_uid            VARCHAR2(36);
    v_ustat          VARCHAR2(30);
    v_salt           RAW(16);
    v_hash           VARCHAR2(255);
    v_code           VARCHAR2(6);
    v_code_hash      VARCHAR2(64);
    v_total_attempts NUMBER;
    v_match_count    NUMBER;
  BEGIN
    require_text(p_full_name, 'full_name', 200);
    IF LENGTH(TRIM(p_full_name)) < 2 THEN bad_request('full_name is too short'); END IF;
    IF NOT is_valid_email(v_email) THEN bad_request('A valid email is required'); END IF;
    IF NOT is_valid_password(p_password) THEN
      bad_request('Password needs 8+ characters with upper, lower, a number and a symbol');
    END IF;
    IF p_code IS NULL OR NOT REGEXP_LIKE(TRIM(p_code), '^[0-9]{6}$') THEN
      bad_request('A 6-digit confirmation code is required');
    END IF;

    v_code      := TRIM(p_code);
    v_code_hash := PKG_CRYPTO.hash_sha256(v_code);

    -- Check if email already belongs to an active/registered account
    BEGIN
      SELECT id, status INTO v_uid, v_ustat FROM users WHERE LOWER(email) = v_email;
      IF v_ustat != 'PENDING_VERIFICATION' THEN
        p_status := 409;
        SELECT JSON_OBJECT(
                 'code'       VALUE 'ALREADY_EXISTS',
                 'message'    VALUE 'An account with this email already exists.',
                 'request_id' VALUE request_id
                 RETURNING CLOB)
          INTO p_body FROM dual;
        RETURN;
      END IF;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        v_uid := NULL;
    END;

    -- Verify the 6-digit code on the server BEFORE inserting or modifying users / auth_identities
    SELECT NVL(SUM(attempts), 0) INTO v_total_attempts
      FROM email_outbox
     WHERE to_address = v_email
       AND template = 'VERIFY_SIGNUP'
       AND created_at > SYSTIMESTAMP - INTERVAL '1' HOUR;
    IF v_total_attempts >= 5 THEN
      RAISE_APPLICATION_ERROR(-20013, 'Too many failed attempts. Please tap Resend for a new confirmation code.');
    END IF;

    SELECT COUNT(*) INTO v_match_count
      FROM email_outbox
     WHERE to_address = v_email
       AND template = 'VERIFY_SIGNUP'
       AND row_version = 1
       AND next_try_at > SYSTIMESTAMP
       AND JSON_VALUE(payload, '$.code_hash') = v_code_hash;

    IF v_match_count = 0 THEN
      UPDATE email_outbox
         SET attempts   = attempts + 1,
             updated_at = SYSTIMESTAMP
       WHERE to_address = v_email
         AND template = 'VERIFY_SIGNUP'
         AND row_version = 1;
      RAISE_APPLICATION_ERROR(-20013, 'Invalid or expired confirmation code.');
    END IF;

    -- Mark verification code consumed
    UPDATE email_outbox
       SET row_version = 2,
           updated_at  = SYSTIMESTAMP
     WHERE to_address = v_email
       AND template = 'VERIFY_SIGNUP'
       AND row_version = 1;

    -- Code is verified on the server: now create the user and auth_identities records (auto-approved as RUNNER)
    IF v_uid IS NOT NULL THEN
      UPDATE users
         SET full_name         = TRIM(p_full_name),
             phone             = SUBSTR(TRIM(p_phone), 1, 40),
             email_verified_at = SYSTIMESTAMP,
             status            = 'APPROVED',
             updated_at        = SYSTIMESTAMP
       WHERE id = v_uid;

      v_salt := PKG_CRYPTO.generate_salt();
      v_hash := PKG_CRYPTO.hash_password(p_password, v_salt, 600000);
      UPDATE auth_identities
         SET password_hash  = v_hash,
             password_salt  = v_salt,
             password_iters = 600000,
             updated_at     = SYSTIMESTAMP
       WHERE user_id = v_uid AND provider = 'PASSWORD';
    ELSE
      v_uid := LOWER(RAWTOHEX(SYS_GUID()));
      INSERT INTO users (id, full_name, email, phone, email_verified_at, status)
      VALUES (v_uid, TRIM(p_full_name), v_email, SUBSTR(TRIM(p_phone), 1, 40), SYSTIMESTAMP, 'APPROVED');

      v_salt := PKG_CRYPTO.generate_salt();
      v_hash := PKG_CRYPTO.hash_password(p_password, v_salt, 600000);
      INSERT INTO auth_identities (user_id, provider, password_hash, password_salt, password_iters)
      VALUES (v_uid, 'PASSWORD', v_hash, v_salt, 600000);

      INSERT INTO user_roles (user_id, role_code) VALUES (v_uid, 'RUNNER');
    END IF;

    INSERT INTO one_time_codes (user_id, purpose, code_hash, expires_at, used_at)
    VALUES (v_uid, 'VERIFY_EMAIL', v_code_hash, SYSTIMESTAMP + INTERVAL '15' MINUTE, SYSTIMESTAMP);

    SELECT JSON_OBJECT(
             'user_id'    VALUE v_uid,
             'email'      VALUE v_email,
             'status'     VALUE 'APPROVED',
             'role'       VALUE 'RUNNER',
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 201;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END register;

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

    -- Auto-approve verified accounts
    UPDATE users
       SET status = 'APPROVED'
     WHERE id = v_target_user_id;

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
    p_email IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_user_id        VARCHAR2(36);
    v_current_email  VARCHAR2(320);
    v_full_name      VARCHAR2(200);
    v_target_email   VARCHAR2(320);
    v_code           VARCHAR2(6);
    v_code_hash      VARCHAR2(64);
    v_recent         NUMBER;
    v_taken          NUMBER;
    v_setup_flag     NUMBER;
    v_is_admin_setup BOOLEAN;
    v_outbox_id      VARCHAR2(36);
    v_payload        CLOB;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    v_user_id := PKG_AUTHZ.get_current_user_id();

    SELECT email, full_name INTO v_current_email, v_full_name
      FROM users
     WHERE id = v_user_id;

    IF p_email IS NOT NULL AND TRIM(p_email) IS NOT NULL THEN
      v_target_email := LOWER(TRIM(p_email));
      IF v_target_email = 'admin@hibiscuss.com' THEN
        bad_request('Enter your official email address instead of the default setup email.');
      END IF;
    ELSE
      v_target_email := LOWER(TRIM(v_current_email));
    END IF;

    IF NOT is_valid_email(v_target_email) THEN
      bad_request('Enter a valid email address');
    END IF;

    -- Check that the target email is not already used by another user in the database
    SELECT COUNT(*) INTO v_taken
      FROM users
     WHERE LOWER(email) = v_target_email
       AND id <> v_user_id;
    IF v_taken > 0 THEN
      p_status := 409;
      SELECT JSON_OBJECT(
               'code'       VALUE 'ALREADY_EXISTS',
               'message'    VALUE 'An account with this email already exists.',
               'request_id' VALUE request_id
               RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    -- Rate limit verification code requests per 15-minute window
    SELECT COUNT(*) INTO v_recent
      FROM one_time_codes
     WHERE user_id = v_user_id AND purpose = 'VERIFY_EMAIL'
       AND created_at > SYSTIMESTAMP - INTERVAL '15' MINUTE;
    IF v_recent >= 10 THEN
      RAISE_APPLICATION_ERROR(-20016, 'Please wait a few minutes before requesting another code');
    END IF;

    -- Determine from database state whether this user is undergoing Admin Security Setup
    SELECT COUNT(*) INTO v_setup_flag
      FROM users u
      JOIN auth_identities ai ON ai.user_id = u.id AND ai.provider = 'PASSWORD'
     WHERE u.id = v_user_id
       AND (ai.password_must_change = 'Y'
            OR LOWER(u.email) = 'admin@hibiscuss.com'
            OR ai.password_hash = PKG_CRYPTO.hash_password('@Admin', ai.password_salt, ai.password_iters));
    v_is_admin_setup := (v_setup_flag > 0);

    v_code      := generate_six_digit_code();
    v_code_hash := PKG_CRYPTO.hash_sha256(v_code);
    v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));

    -- Expire previous un-used codes
    UPDATE one_time_codes
       SET used_at = SYSTIMESTAMP
     WHERE user_id = v_user_id AND purpose = 'VERIFY_EMAIL' AND used_at IS NULL;

    INSERT INTO one_time_codes (user_id, purpose, code_hash, expires_at)
    VALUES (v_user_id, 'VERIFY_EMAIL', v_code_hash, SYSTIMESTAMP + INTERVAL '15' MINUTE);

    -- Supersede previous pending verification outbox entries for this user
    UPDATE email_outbox
       SET row_version = 0,
           updated_at  = SYSTIMESTAMP
     WHERE template IN ('VERIFY_EMAIL', 'ADMIN_SETUP_VERIFY')
       AND row_version = 1
       AND JSON_VALUE(payload, '$.user_id') = v_user_id;

    SELECT JSON_OBJECT(
             'user_id'      VALUE v_user_id,
             'target_email' VALUE v_target_email,
             'code_hash'    VALUE v_code_hash
             RETURNING CLOB)
      INTO v_payload FROM dual;

    INSERT INTO email_outbox (
      id, template, to_address, payload, status, attempts, next_try_at, row_version
    ) VALUES (
      v_outbox_id,
      CASE WHEN v_is_admin_setup THEN 'ADMIN_SETUP_VERIFY' ELSE 'VERIFY_EMAIL' END,
      v_target_email,
      v_payload,
      'SENDING',
      0,
      SYSTIMESTAMP + INTERVAL '15' MINUTE,
      1
    );

    dispatch_verification_email(
      p_outbox_id      => v_outbox_id,
      p_to_email       => v_target_email,
      p_code           => v_code,
      p_recipient_name => CASE WHEN v_is_admin_setup THEN 'Administrator' ELSE v_full_name END,
      p_is_admin_setup => v_is_admin_setup);

    SELECT JSON_OBJECT(
             'status'     VALUE 'SENT',
             'email'      VALUE v_target_email,
             'message'    VALUE 'Verification code sent to ' || v_target_email,
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END send_verification_code;

  PROCEDURE request_password_reset(
    p_email IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_email     VARCHAR2(320) := LOWER(TRIM(p_email));
    v_user_id   VARCHAR2(36);
    v_full_name VARCHAR2(200);
    v_ustat     VARCHAR2(30);
    v_recent    NUMBER;
    v_code      VARCHAR2(6);
    v_code_hash VARCHAR2(64);
    v_outbox_id VARCHAR2(36);
    v_payload   CLOB;
  BEGIN
    IF NOT is_valid_email(v_email) THEN
      bad_request('A valid email address is required');
    END IF;

    -- Always generate a code and SHA-256 hash upfront so both valid and invalid emails do the same work
    v_code      := generate_six_digit_code();
    v_code_hash := PKG_CRYPTO.hash_sha256(v_code);

    IF v_email != 'admin@hibiscuss.com' THEN
      BEGIN
        SELECT id, full_name, status INTO v_user_id, v_full_name, v_ustat
          FROM users
         WHERE LOWER(email) = v_email;
      EXCEPTION
        WHEN NO_DATA_FOUND THEN
          v_user_id := NULL;
      END;
    END IF;

    -- Only store a reset code and send an email if the account exists, is active, and is within rate limits.
    -- Otherwise, silently skip sending and return the exact same 200 OK response (OWASP anti-enumeration).
    IF v_user_id IS NOT NULL AND v_ustat NOT IN ('REJECTED', 'DEACTIVATED') THEN
      SELECT COUNT(*) INTO v_recent
        FROM one_time_codes
       WHERE user_id = v_user_id
         AND purpose = 'RESET_PASSWORD'
         AND created_at > SYSTIMESTAMP - INTERVAL '15' MINUTE;

      IF v_recent < 5 THEN
        v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));

        UPDATE one_time_codes
           SET used_at = SYSTIMESTAMP
         WHERE user_id = v_user_id AND purpose = 'RESET_PASSWORD' AND used_at IS NULL;

        INSERT INTO one_time_codes (user_id, purpose, code_hash, expires_at)
        VALUES (v_user_id, 'RESET_PASSWORD', v_code_hash, SYSTIMESTAMP + INTERVAL '15' MINUTE);

        SELECT JSON_OBJECT(
                 'user_id'   VALUE v_user_id,
                 'code_hash' VALUE v_code_hash
                 RETURNING CLOB)
          INTO v_payload FROM dual;

        INSERT INTO email_outbox (
          id, template, to_address, payload, status, attempts, next_try_at, row_version
        ) VALUES (
          v_outbox_id, 'RESET_PASSWORD', v_email, v_payload, 'SENDING', 0,
          SYSTIMESTAMP + INTERVAL '15' MINUTE, 1
        );

        dispatch_transactional_email(
          p_outbox_id      => v_outbox_id,
          p_to_email       => v_email,
          p_recipient_name => v_full_name,
          p_subject        => v_code || ' is your Hibiscus TruckPark password reset code',
          p_heading        => 'Reset Your Password',
          p_copy           => 'Use the 6-digit security code below to reset your Hibiscus TruckPark account password.',
          p_badge_label    => '6-DIGIT RESET CODE',
          p_badge_value    => v_code,
          p_footer_note    => 'This code expires in 15 minutes. If you did not request a password reset, ignore this email.',
          p_best_effort    => TRUE);
      END IF;
    END IF;

    SELECT JSON_OBJECT(
             'status'     VALUE 'SENT',
             'email'      VALUE v_email,
             'message'    VALUE 'Enter the 6-digit reset code sent to ' || v_email,
             'request_id' VALUE request_id
             RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END request_password_reset;

  PROCEDURE reset_password(
    p_email IN VARCHAR2,
    p_code IN VARCHAR2,
    p_new_password IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_email          VARCHAR2(320) := LOWER(TRIM(p_email));
    v_user_id        VARCHAR2(36);
    v_code_hash      VARCHAR2(64);
    v_total_attempts NUMBER;
    v_match_count    NUMBER;
    v_new_salt       RAW(16);
    v_new_hash       VARCHAR2(255);
  BEGIN
    IF NOT is_valid_email(v_email) THEN
      bad_request('A valid email address is required');
    END IF;
    IF p_code IS NULL OR NOT REGEXP_LIKE(TRIM(p_code), '^[0-9]{6}$') THEN
      bad_request('A 6-digit reset code is required');
    END IF;
    IF NOT is_valid_password(p_new_password) OR p_new_password = '@Admin' THEN
      bad_request('Password needs 8+ characters with uppercase, lowercase, a number and a symbol');
    END IF;

    -- Always compute the 600,000-iteration PBKDF2 hash and SHA-256 code hash upfront so
    -- timing and error responses are identical whether or not the email exists (OWASP anti-enumeration).
    v_code_hash := PKG_CRYPTO.hash_sha256(TRIM(p_code));
    v_new_salt  := PKG_CRYPTO.generate_salt();
    v_new_hash  := PKG_CRYPTO.hash_password(p_new_password, v_new_salt, 600000);

    BEGIN
      SELECT id INTO v_user_id FROM users WHERE LOWER(email) = v_email;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20013, 'Invalid or expired reset code');
    END;

    SELECT NVL(SUM(attempts), 0) INTO v_total_attempts
      FROM one_time_codes
     WHERE user_id = v_user_id
       AND purpose = 'RESET_PASSWORD'
       AND created_at > SYSTIMESTAMP - INTERVAL '1' HOUR;
    IF v_total_attempts >= 5 THEN
      RAISE_APPLICATION_ERROR(-20013, 'Invalid or expired reset code');
    END IF;

    SELECT COUNT(*) INTO v_match_count
      FROM one_time_codes
     WHERE user_id = v_user_id
       AND purpose = 'RESET_PASSWORD'
       AND code_hash = v_code_hash
       AND used_at IS NULL
       AND expires_at > SYSTIMESTAMP;

    IF v_match_count = 0 THEN
      UPDATE one_time_codes
         SET attempts = attempts + 1
       WHERE user_id = v_user_id AND purpose = 'RESET_PASSWORD' AND used_at IS NULL;
      RAISE_APPLICATION_ERROR(-20013, 'Invalid or expired reset code');
    END IF;

    UPDATE one_time_codes
       SET used_at = SYSTIMESTAMP
     WHERE user_id = v_user_id AND purpose = 'RESET_PASSWORD' AND code_hash = v_code_hash;

    UPDATE auth_identities
       SET password_hash        = v_new_hash,
           password_salt        = v_new_salt,
           password_iters       = 600000,
           password_must_change = 'N',
           failed_attempts      = 0,
           locked_until         = NULL,
           updated_at           = SYSTIMESTAMP
     WHERE user_id = v_user_id AND provider = 'PASSWORD';

    UPDATE sessions SET revoked_at = SYSTIMESTAMP WHERE user_id = v_user_id AND revoked_at IS NULL;
    UPDATE users SET permission_version = permission_version + 1, updated_at = SYSTIMESTAMP WHERE id = v_user_id;

    INSERT INTO audit_events (actor_user_id, action, target_type, target_id, reason)
    VALUES (v_user_id, 'PASSWORD_RESET', 'USER', v_user_id, 'Password reset via email verification code');

    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END reset_password;

  -- ------------------------------------------------------------- signed in

  PROCEDURE change_password(
    p_authorization IN VARCHAR2,
    p_new_email IN VARCHAR2,
    p_new_password IN VARCHAR2,
    p_code IN VARCHAR2 DEFAULT NULL,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_user_id      VARCHAR2(36);
    v_hash         VARCHAR2(255);
    v_salt         RAW(16);
    v_iters        NUMBER;
    v_calc_hash    VARCHAR2(255);
    v_new_salt     RAW(16);
    v_new_hash     VARCHAR2(255);
    v_new_email    VARCHAR2(320);
    v_taken        NUMBER;
    v_must         VARCHAR2(1);
    v_issued       TIMESTAMP WITH TIME ZONE;
    v_target_match NUMBER;
  BEGIN
    -- The signed-in session is the proof of identity for this setup step.
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    v_user_id := PKG_AUTHZ.get_current_user_id();

    -- Only a fresh sign-in (within 30 minutes) may change credentials, so a session
    -- left open on a shared device cannot be used to take over the account.
    SELECT created_at INTO v_issued
      FROM sessions
     WHERE token_hash = PKG_CRYPTO.hash_sha256(TRIM(bearer_token(p_authorization)));
    IF v_issued < SYSTIMESTAMP - INTERVAL '30' MINUTE THEN
      RAISE_APPLICATION_ERROR(-20003, 'Sign in again to finish account setup');
    END IF;

    require_text(p_new_password, 'new_password', 100);

    IF NOT is_valid_password(p_new_password) THEN
      bad_request('Password needs 8+ characters with uppercase, lowercase, a number and a symbol');
    END IF;

    -- New email is required: the default address cannot receive codes.
    v_new_email := LOWER(TRIM(p_new_email));
    IF NOT is_valid_email(v_new_email) OR v_new_email = 'admin@hibiscuss.com' THEN
      bad_request('Enter a valid email address');
    END IF;

    SELECT COUNT(*) INTO v_taken
      FROM users
     WHERE LOWER(email) = v_new_email AND id <> v_user_id;
    IF v_taken > 0 THEN
      p_status := 409;
      SELECT JSON_OBJECT(
               'code'       VALUE 'ALREADY_EXISTS',
               'message'    VALUE 'An account with this email already exists.',
               'request_id' VALUE request_id
               RETURNING CLOB)
        INTO p_body FROM dual;
      RETURN;
    END IF;

    IF p_code IS NULL OR NOT REGEXP_LIKE(TRIM(p_code), '^[0-9]{6}$') THEN
      bad_request('A 6-digit confirmation code is required');
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

    -- Only accounts still undergoing initial setup (password_must_change = 'Y' OR email not yet verified)
    -- may use this step. Once fully verified and set up, changes require the standard change flow.
    DECLARE
      v_verified_at   TIMESTAMP WITH TIME ZONE;
      v_current_email VARCHAR2(320);
    BEGIN
      SELECT email_verified_at, LOWER(email) INTO v_verified_at, v_current_email FROM users WHERE id = v_user_id;
      IF v_must != 'Y' AND v_verified_at IS NOT NULL AND v_current_email != 'admin@hibiscuss.com' THEN
        RAISE_APPLICATION_ERROR(-20003, 'Account setup is already complete');
      END IF;
    END;

    -- Prevent keeping the default setup password
    v_calc_hash := PKG_CRYPTO.hash_password(p_new_password, v_salt, v_iters);
    IF (v_must = 'Y' AND v_calc_hash = v_hash) OR p_new_password = '@Admin' THEN
      bad_request('New password must be different from the default password');
    END IF;

    -- Ensure the email being saved matches the address to which the verification code was sent
    SELECT COUNT(*) INTO v_target_match
      FROM email_outbox
     WHERE template IN ('VERIFY_EMAIL', 'ADMIN_SETUP_VERIFY')
       AND row_version = 1
       AND JSON_VALUE(payload, '$.user_id') = v_user_id
       AND LOWER(to_address) != v_new_email;
    IF v_target_match > 0 THEN
      RAISE_APPLICATION_ERROR(-20013, 'Email address changed after code was sent. Please request a new confirmation code.');
    END IF;

    -- Verify the 6-digit code on the server FIRST before updating users or auth_identities!
    -- If invalid or expired, PKG_AUTH.verify_email increments attempts and raises -20013.
    PKG_AUTH.verify_email(v_user_id, TRIM(p_code));

    UPDATE email_outbox
       SET row_version = 2,
           updated_at  = SYSTIMESTAMP
     WHERE template IN ('VERIFY_EMAIL', 'ADMIN_SETUP_VERIFY')
       AND row_version = 1
       AND JSON_VALUE(payload, '$.user_id') = v_user_id;

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

    -- Switch to the admin's verified email and ensure ADMIN accounts stay APPROVED
    UPDATE users
       SET email = v_new_email,
           email_verified_at = SYSTIMESTAMP,
           status = CASE WHEN EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = users.id AND ur.role_code = 'ADMIN')
                         THEN 'APPROVED' ELSE status END,
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
    v_token     VARCHAR2(64);
    v_exp       TIMESTAMP WITH TIME ZONE;
    v_role      VARCHAR2(30) := UPPER(TRIM(p_proposed_role));
    v_email     VARCHAR2(320) := LOWER(TRIM(p_email));
    v_outbox_id VARCHAR2(36);
    v_count     NUMBER;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.invite');

    IF NOT is_valid_email(v_email) THEN bad_request('A valid email is required'); END IF;
    SELECT COUNT(*) INTO v_count FROM roles WHERE code = v_role;
    IF v_count = 0 THEN bad_request('Unknown role'); END IF;

    PKG_ADMIN.create_invite(v_email, v_role, v_token);

    SELECT expires_at INTO v_exp FROM invites
     WHERE token_hash = PKG_CRYPTO.hash_sha256(v_token);

    BEGIN
      SELECT id INTO v_outbox_id
        FROM email_outbox
       WHERE template = 'INVITE' AND to_address = v_email
       ORDER BY created_at DESC
       FETCH FIRST 1 ROWS ONLY;

      dispatch_transactional_email(
        p_outbox_id      => v_outbox_id,
        p_to_email       => v_email,
        p_recipient_name => NULL,
        p_subject        => 'You are invited to join Hibiscus TruckPark (' || v_role || ')',
        p_heading        => 'You Are Invited to Hibiscus TruckPark',
        p_copy           => 'An administrator has invited you to join Hibiscus TruckPark with the ' || v_role || ' role. Use the invitation token below in the app to set up your account.',
        p_badge_label    => 'INVITATION TOKEN',
        p_badge_value    => v_token,
        p_footer_note    => 'This invitation expires in 72 hours.',
        p_best_effort    => TRUE);
    EXCEPTION
      WHEN OTHERS THEN NULL;
    END;

    -- The raw token only ever travels by email, never in this response.
    SELECT JSON_OBJECT('email' VALUE v_email, 'proposed_role' VALUE v_role,
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
    v_filter VARCHAR2(30) := NVL(UPPER(TRIM(p_user_status)), 'ALL');
    v_items  CLOB;
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    IF v_filter NOT IN ('ALL', 'PENDING_VERIFICATION', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DEACTIVATED') THEN
      bad_request('Unknown status filter');
    END IF;

    SELECT NVL(JSON_ARRAYAGG(JSON_OBJECT(
                 'id'         VALUE u.id,
                 'full_name'  VALUE u.full_name,
                 'email'      VALUE u.email,
                 'phone'      VALUE u.phone,
                 'status'     VALUE u.status,
                 'role'       VALUE COALESCE(
                                      (SELECT MAX(ur.role_code) FROM user_roles ur WHERE ur.user_id = u.id),
                                      'RUNNER'),
                 'created_at' VALUE iso(u.created_at))
               ORDER BY u.created_at DESC RETURNING CLOB), TO_CLOB('[]'))
      INTO v_items
      FROM users u
     WHERE (v_filter = 'ALL' OR u.status = v_filter);

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
    v_role      VARCHAR2(30) := UPPER(TRIM(p_role_code));
    v_count     NUMBER;
    v_email     VARCHAR2(320);
    v_full_name VARCHAR2(200);
    v_outbox_id VARCHAR2(36);
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    require_text(p_user_id, 'user id', 36);
    SELECT COUNT(*) INTO v_count FROM roles WHERE code = v_role;
    IF v_count = 0 THEN bad_request('Unknown role'); END IF;

    BEGIN
      SELECT email, full_name INTO v_email, v_full_name
        FROM users
       WHERE id = p_user_id;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        bad_request('User not found');
    END;

    -- Prevent an administrator from accidentally removing their own ADMIN role if they are the only admin
    IF p_user_id = PKG_AUTHZ.get_current_user_id() AND v_role != 'ADMIN' THEN
      SELECT COUNT(*) INTO v_count
        FROM user_roles
       WHERE role_code = 'ADMIN' AND user_id <> p_user_id;
      IF v_count = 0 THEN
        bad_request('Cannot remove ADMIN role from the only administrator account');
      END IF;
    END IF;

    -- Ensure single primary role assignment when updating role
    DELETE FROM user_roles WHERE user_id = p_user_id AND role_code <> v_role;
    PKG_ADMIN.approve_user(p_user_id, v_role);

    v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));
    INSERT INTO email_outbox (id, template, to_address, payload, status)
    VALUES (v_outbox_id, 'ACCOUNT_APPROVED', v_email,
            JSON_OBJECT('user_id' VALUE p_user_id, 'role' VALUE v_role), 'SENDING');

    dispatch_transactional_email(
      p_outbox_id      => v_outbox_id,
      p_to_email       => v_email,
      p_recipient_name => v_full_name,
      p_subject        => 'Your Hibiscus TruckPark role is now ' || v_role,
      p_heading        => 'Account Role Updated',
      p_copy           => 'An administrator has updated your Hibiscus TruckPark role assignment.',
      p_badge_label    => 'ASSIGNED ROLE',
      p_badge_value    => v_role,
      p_footer_note    => 'Open the Hibiscus TruckPark app to access your ' || v_role || ' workspace.',
      p_best_effort    => TRUE);

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
    v_email     VARCHAR2(320);
    v_full_name VARCHAR2(200);
    v_outbox_id VARCHAR2(36);
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('user.approve');

    require_text(p_user_id, 'user id', 36);
    require_text(p_reason, 'reason', 500);

    BEGIN
      SELECT email, full_name INTO v_email, v_full_name
        FROM users
       WHERE id = p_user_id AND status = 'PENDING_APPROVAL';
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        bad_request('User is not waiting for approval');
    END;

    PKG_ADMIN.reject_user(p_user_id, p_reason);

    v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));
    INSERT INTO email_outbox (id, template, to_address, payload, status)
    VALUES (v_outbox_id, 'ACCOUNT_REJECTED', v_email,
            JSON_OBJECT('user_id' VALUE p_user_id, 'reason' VALUE TRIM(p_reason)), 'SENDING');

    dispatch_transactional_email(
      p_outbox_id      => v_outbox_id,
      p_to_email       => v_email,
      p_recipient_name => v_full_name,
      p_subject        => 'Update on your Hibiscus TruckPark account registration',
      p_heading        => 'Account Registration Not Approved',
      p_copy           => 'Your Hibiscus TruckPark account request could not be approved at this time.',
      p_badge_label    => 'REASON',
      p_badge_value    => TRIM(p_reason),
      p_footer_note    => 'Contact your site supervisor or administrator if you believe this was in error.',
      p_best_effort    => TRUE);

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

  PROCEDURE list_devices(
    p_authorization IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_items      CLOB;
    v_cur_dev_id VARCHAR2(36);
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('device.manage');
    v_cur_dev_id := PKG_AUTHZ.get_current_device_id();

    SELECT NVL(JSON_ARRAYAGG(JSON_OBJECT(
                 'id'                VALUE d.id,
                 'user_id'           VALUE d.user_id,
                 'user_name'         VALUE u.full_name,
                 'user_email'        VALUE u.email,
                 'name'              VALUE NVL(d.name, 'Android Handset'),
                 'registered_at'     VALUE iso(d.registered_at),
                 'last_seen_at'      VALUE iso(d.last_seen_at),
                 'revoked_at'        VALUE iso(d.revoked_at),
                 'revoke_reason'     VALUE d.revoke_reason,
                 'is_current_device' VALUE CASE WHEN d.id = v_cur_dev_id THEN 'true' ELSE 'false' END FORMAT JSON)
               ORDER BY CASE WHEN d.revoked_at IS NULL THEN 0 ELSE 1 END,
                        d.last_seen_at DESC NULLS LAST,
                        d.registered_at DESC
               RETURNING CLOB), TO_CLOB('[]'))
      INTO v_items
      FROM devices d
      JOIN users u ON u.id = d.user_id;

    SELECT JSON_OBJECT('items' VALUE v_items FORMAT JSON, 'request_id' VALUE request_id RETURNING CLOB)
      INTO p_body FROM dual;
    p_status := 200;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END list_devices;

  PROCEDURE revoke_device(
    p_authorization IN VARCHAR2, p_device_id IN VARCHAR2, p_reason IN VARCHAR2,
    p_status OUT NUMBER, p_body OUT CLOB
  ) IS
    v_count       NUMBER;
    v_dev_name    VARCHAR2(200);
    v_owner_email VARCHAR2(320);
    v_owner_name  VARCHAR2(200);
    v_outbox_id   VARCHAR2(36);
  BEGIN
    PKG_AUTHZ.validate_session(bearer_token(p_authorization));
    PKG_AUTHZ.require_permission('device.manage');

    require_text(p_device_id, 'device id', 36);
    require_text(p_reason, 'reason', 500);

    IF p_device_id = PKG_AUTHZ.get_current_device_id() THEN
      bad_request('Cannot revoke the handset you are currently signed in on');
    END IF;

    BEGIN
      SELECT NVL(d.name, 'Android Handset'), u.email, u.full_name
        INTO v_dev_name, v_owner_email, v_owner_name
        FROM devices d
        JOIN users u ON u.id = d.user_id
       WHERE d.id = p_device_id;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        p_status := 404;
        SELECT JSON_OBJECT('code' VALUE 'NOT_FOUND', 'message' VALUE 'Device not found',
                           'request_id' VALUE request_id RETURNING CLOB)
          INTO p_body FROM dual;
        RETURN;
    END;

    PKG_ADMIN.revoke_device(p_device_id, p_reason);

    IF v_owner_email IS NOT NULL AND LOWER(v_owner_email) != 'admin@hibiscuss.com' THEN
      v_outbox_id := LOWER(RAWTOHEX(SYS_GUID()));
      INSERT INTO email_outbox (id, template, to_address, payload, status)
      VALUES (v_outbox_id, 'DEVICE_REVOKED', v_owner_email,
              JSON_OBJECT('device_id' VALUE p_device_id, 'reason' VALUE TRIM(p_reason)), 'SENDING');

      dispatch_transactional_email(
        p_outbox_id      => v_outbox_id,
        p_to_email       => v_owner_email,
        p_recipient_name => v_owner_name,
        p_subject        => 'Security alert: Handset access revoked on Hibiscus TruckPark',
        p_heading        => 'Device Access Revoked',
        p_copy           => 'An administrator has revoked access for device "' || v_dev_name || '" on your Hibiscus TruckPark account.',
        p_badge_label    => 'REVOCATION REASON',
        p_badge_value    => TRIM(p_reason),
        p_footer_note    => 'All active sessions on that device have been terminated immediately.',
        p_best_effort    => TRUE);
    END IF;

    p_body := ok_body;
    p_status := 200;
    COMMIT;
  EXCEPTION
    WHEN OTHERS THEN fail(SQLCODE, SQLERRM, p_status, p_body);
  END revoke_device;

END PKG_API;
/

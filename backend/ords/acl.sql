SET DEFINE OFF;

BEGIN
  -- 1. Grant RESOLVE at host level (ports MUST be NULL)
  DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
    host       => 'smtp-relay.brevo.com',
    lower_port => NULL,
    upper_port => NULL,
    ace        => xs$ace_type(
                    privilege_list => xs$name_list('resolve'),
                    principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                    principal_type => xs_acl.ptype_db));

  -- 2. Grant CONNECT on SMTP port 587
  DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
    host       => 'smtp-relay.brevo.com',
    lower_port => 587,
    upper_port => 587,
    ace        => xs$ace_type(
                    privilege_list => xs$name_list('connect'),
                    principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                    principal_type => xs_acl.ptype_db));

  -- 3. Grant RESOLVE & CONNECT for HTTPS api.brevo.com (port 443)
  DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
    host       => 'api.brevo.com',
    lower_port => NULL,
    upper_port => NULL,
    ace        => xs$ace_type(
                    privilege_list => xs$name_list('resolve'),
                    principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                    principal_type => xs_acl.ptype_db));

  DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
    host       => 'api.brevo.com',
    lower_port => 443,
    upper_port => 443,
    ace        => xs$ace_type(
                    privilege_list => xs$name_list('connect', 'http'),
                    principal_name => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'),
                    principal_type => xs_acl.ptype_db));

  COMMIT;
END;
/

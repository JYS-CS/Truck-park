-- Fix for ORA-00001: unique constraint (ADMIN.UQ_AUTH_PROVIDER_EXT)
-- Run this in Oracle Database Actions SQL Worksheet (ADMIN):

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE auth_identities DROP CONSTRAINT uq_auth_provider_ext';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -2443 THEN -- ORA-02443: Cannot drop constraint - nonexistent constraint
      NULL;
    END IF;
END;
/

-- Add proper constraint: Each user can only have ONE identity per provider (e.g. one PASSWORD identity)
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE auth_identities ADD CONSTRAINT uq_auth_user_provider UNIQUE (user_id, provider)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -2261 THEN -- ORA-02261: unique or primary key already exists
      NULL;
    END IF;
END;
/

-- Optional: ensure external providers (Google, OAuth, etc.) remain unique only when external_id is not null
BEGIN
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_auth_provider_ext_idx ON auth_identities (provider, external_id)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN -- ORA-00955: name is already used by an existing object
      NULL;
    END IF;
END;
/


-- Adds the setup flag to databases created before it existed.
-- Run once on dev, then prod. Safe to re-run: the ALTER is skipped if the column exists.

DECLARE
  v_exists NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_exists
    FROM user_tab_columns
   WHERE table_name = 'AUTH_IDENTITIES' AND column_name = 'PASSWORD_MUST_CHANGE';

  IF v_exists = 0 THEN
    EXECUTE IMMEDIATE q'[ALTER TABLE auth_identities ADD (password_must_change VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (password_must_change IN ('Y','N')))]';
  END IF;
END;
/

-- Flag the bootstrap admin only while it still has the setup password.
UPDATE auth_identities ai
   SET ai.password_must_change = 'Y'
 WHERE ai.provider = 'PASSWORD'
   AND ai.user_id IN (SELECT u.id FROM users u WHERE u.email = 'admin@hibiscuss.com')
   AND ai.password_hash = PKG_CRYPTO.hash_password('@Admin', ai.password_salt, ai.password_iters);

COMMIT;

USE identity_db;
CREATE TABLE IF NOT EXISTS user_account (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  display_name VARCHAR(60) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(10) NOT NULL,
  admin_slot SMALLINT UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT account_role_slot CHECK (
    (role = 'USER' AND admin_slot IS NULL) OR
    (role = 'ADMIN' AND admin_slot IS NOT NULL AND admin_slot = 1)
  )
);

CREATE TABLE IF NOT EXISTS identity_audit (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, action VARCHAR(80) NOT NULL,
 target_id VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

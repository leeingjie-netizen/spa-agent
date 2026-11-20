-- Additive authentication storage; existing spa_user and appointment rows are untouched.
CREATE TABLE IF NOT EXISTS spa_account (
 id BIGINT NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL,
 identity_key VARCHAR(160) NOT NULL, username VARCHAR(80) NULL,
 password_hash VARCHAR(256) NULL, nickname VARCHAR(50) NOT NULL,
 role VARCHAR(16) NOT NULL DEFAULT 'USER', staff_id BIGINT NULL,
 status TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_account_identity(identity_key), UNIQUE KEY uk_account_username(username),
 UNIQUE KEY uk_account_user(user_id), UNIQUE KEY uk_account_staff(staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS spa_auth_token (
 token_hash CHAR(64) NOT NULL PRIMARY KEY, account_id BIGINT NOT NULL,
 expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_token_account(account_id), KEY idx_token_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Additive coupon, redemption and explicitly simulated wallet ledgers.
CREATE TABLE IF NOT EXISTS spa_loyalty_guard(user_id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS spa_coupon_template(
 id BIGINT PRIMARY KEY,title VARCHAR(100) NOT NULL,discount_amount DECIMAL(10,2) NOT NULL,
 min_amount DECIMAL(10,2) NOT NULL DEFAULT 0,expires_at DATETIME NOT NULL,remaining INT NOT NULL,
 active TINYINT NOT NULL DEFAULT 1,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS spa_user_coupon(
 id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,template_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',order_id BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_coupon_claim(user_id,template_id),KEY idx_coupon_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS spa_order_benefit(
 order_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,coupon_id BIGINT NULL,points_used INT NOT NULL DEFAULT 0,
 original_amount DECIMAL(10,2) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS spa_points_adjustment(
 id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,order_id BIGINT NOT NULL,points INT NOT NULL,
 kind VARCHAR(20) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_points_adjustment(order_id,kind),KEY idx_points_adjustment_user(user_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS spa_mock_wallet(
 user_id BIGINT PRIMARY KEY,balance DECIMAL(12,2) NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS spa_mock_wallet_ledger(
 id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,request_id VARCHAR(100) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,balance_after DECIMAL(12,2) NOT NULL,kind VARCHAR(20) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_wallet_request(user_id,request_id),KEY idx_wallet_user(user_id,created_at)
) ENGINE=InnoDB;

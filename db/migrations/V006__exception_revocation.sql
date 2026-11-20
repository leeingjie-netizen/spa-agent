-- Additive revocation audit. Keep every original exception and all appointments unchanged.
CREATE TABLE IF NOT EXISTS schedule_exception_revocation (
 exception_id BIGINT NOT NULL PRIMARY KEY,
 revoked_by BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 revoked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

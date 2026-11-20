-- Additive only. Preserve every existing appointment and schedule.
CREATE TABLE IF NOT EXISTS schedule_day_lock (work_date DATE PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS staff_leave (
 id BIGINT PRIMARY KEY, staff_id BIGINT NOT NULL, work_date DATE NOT NULL,
 all_day BOOLEAN NOT NULL, start_time TIME NULL, end_time TIME NULL, reason VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', requested_by BIGINT NOT NULL,
 reviewed_by BIGINT NULL, review_reason VARCHAR(500) NULL, reviewed_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_leave_staff_date(staff_id,work_date,status)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS schedule_exception (
 id BIGINT PRIMARY KEY, staff_id BIGINT NOT NULL, work_date DATE NOT NULL,
 start_time TIME NOT NULL, end_time TIME NOT NULL, reason VARCHAR(500) NOT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_exception_staff_date(staff_id,work_date)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS business_day (
 work_date DATE PRIMARY KEY, closed BOOLEAN NOT NULL DEFAULT FALSE,
 open_time TIME NULL, close_time TIME NULL, reason VARCHAR(500) NOT NULL,
 updated_by BIGINT NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS schedule_change_log (
 id BIGINT PRIMARY KEY, staff_id BIGINT NOT NULL, work_date DATE NOT NULL,
 previous_value TEXT NULL, new_value TEXT NOT NULL, reason VARCHAR(500) NOT NULL,
 operator_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_change_staff_date(staff_id,work_date)
) ENGINE=InnoDB;

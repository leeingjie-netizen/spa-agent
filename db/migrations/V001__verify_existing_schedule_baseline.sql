-- 2026-11-16: Existing local spa schema already contains the schedule migration.
-- This version deliberately performs no ALTER or data rewrite.
-- Execute with mysql --database=spa. Require all checks to return 1.
SELECT DATABASE() = 'spa' AS correct_database;
SELECT COUNT(*) = 3 AS schedule_columns_present
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_schedule'
  AND COLUMN_NAME IN ('source', 'break_start_time', 'break_end_time');
SELECT COUNT(*) = 1 AS manual_default_protected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_schedule'
  AND COLUMN_NAME = 'source' AND COLUMN_DEFAULT = 'MANUAL' AND IS_NULLABLE = 'NO';
SELECT COUNT(*) = 2 AS rest_day_times_nullable
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_schedule'
  AND COLUMN_NAME IN ('start_time', 'end_time') AND IS_NULLABLE = 'YES';
SELECT COUNT(*) >= 1 AS schedule_unique_present FROM (
  SELECT INDEX_NAME FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_schedule' AND NON_UNIQUE = 0
  GROUP BY INDEX_NAME
  HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'staff_id,work_date'
) AS indexes_found;
SELECT COUNT(*) >= 1 AS weekly_unique_present FROM (
  SELECT INDEX_NAME FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_weekly_rule' AND NON_UNIQUE = 0
  GROUP BY INDEX_NAME
  HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'staff_id,day_of_week'
) AS indexes_found;
SELECT COUNT(*) = 0 AS schedule_sources_valid FROM staff_schedule
WHERE source IS NULL OR source NOT IN ('MANUAL', 'AUTO');
SELECT COUNT(*) = 0 AS working_schedule_times_valid FROM staff_schedule
WHERE status = 1 AND (start_time IS NULL OR end_time IS NULL OR start_time >= end_time);
SELECT COUNT(*) = 0 AS schedule_breaks_valid FROM staff_schedule
WHERE (break_start_time IS NULL) <> (break_end_time IS NULL)
   OR (break_start_time IS NOT NULL AND (break_start_time >= break_end_time
       OR start_time IS NULL OR end_time IS NULL
       OR break_start_time < start_time OR break_end_time > end_time));
SELECT COUNT(*) = 0 AS weekly_rules_valid FROM staff_weekly_rule
WHERE day_of_week NOT BETWEEN 1 AND 7 OR is_working NOT IN (0, 1)
   OR (is_working = 1 AND (start_time IS NULL OR end_time IS NULL OR start_time >= end_time))
   OR (break_start_time IS NULL) <> (break_end_time IS NULL)
   OR (break_start_time IS NOT NULL AND (break_start_time >= break_end_time
       OR start_time IS NULL OR end_time IS NULL
       OR break_start_time < start_time OR break_end_time > end_time));

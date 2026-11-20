-- Companion SQL for LocalDatabaseIntegrityTest, saved before running the test.
-- This is a verification fixture, not a schema migration or seed script.
-- Only verified local development 127.0.0.1:3306/spa is allowed.
-- Java supplies freshly generated negative BIGINT IDs after asserting no matching
-- IDs or staff IDs exist. It asserts expected 1062 / SQLSTATE 23000 and always
-- rolls back in finally. No SQL statement below changes existing rows.
-- :id, :id_minus_one and :test_number are prepared-statement parameters, not
-- mysql client variables. Execute the Java test for all assertions and cleanup.

SELECT DATABASE(), VERSION();
SELECT COLUMN_DEFAULT, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='spa' AND TABLE_NAME='staff_schedule' AND COLUMN_NAME='source';
SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_index
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='spa' AND TABLE_NAME IN
 ('staff_schedule','staff_weekly_rule','appointment','appointment_slot')
 AND NON_UNIQUE=0 GROUP BY TABLE_NAME, INDEX_NAME;

START TRANSACTION;
SELECT COUNT(*) FROM staff_schedule WHERE id IN (:id,:id_minus_one) OR staff_id=:id;
-- Require count = 0 before fixture insertion.
INSERT INTO staff_schedule (id,staff_id,work_date,status)
VALUES (:id,:id,'2099-01-05',0);
SELECT source, start_time, end_time FROM staff_schedule WHERE id=:id;
-- Require MANUAL, NULL, NULL.
INSERT INTO staff_schedule (id,staff_id,work_date,status)
VALUES (:id_minus_one,:id,'2099-01-05',0);
-- Require duplicate-key error 1062, SQLSTATE 23000.
ROLLBACK;
SELECT COUNT(*) FROM staff_schedule WHERE id IN (:id,:id_minus_one) OR staff_id=:id;
-- Require count = 0.

START TRANSACTION;
SELECT COUNT(*) FROM appointment WHERE id=:id OR staff_id=:id;
SELECT COUNT(*) FROM appointment_slot WHERE id IN (:id,:id_minus_one) OR staff_id=:id;
-- Require both counts = 0 before fixture insertion.
INSERT INTO appointment (id,appointment_no,user_id,service_id,staff_id,
 appointment_date,start_time,end_time,amount,status,request_id)
VALUES (:id,:test_number,:id,:id,:id,'2099-01-05','10:00:00','10:45:00',1.00,1,:test_number);
INSERT INTO appointment_slot (id,appointment_id,staff_id,slot_time)
VALUES (:id,:id,:id,'2099-01-05 10:00:00');
INSERT INTO appointment_slot (id,appointment_id,staff_id,slot_time)
VALUES (:id_minus_one,:id,:id,'2099-01-05 10:00:00');
-- Require duplicate-key error 1062, SQLSTATE 23000.
ROLLBACK;
SELECT COUNT(*) FROM appointment WHERE id=:id OR staff_id=:id;
SELECT COUNT(*) FROM appointment_slot WHERE id IN (:id,:id_minus_one) OR staff_id=:id;
-- Require both counts = 0; Java rolls back its final read transaction as well.

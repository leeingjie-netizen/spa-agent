-- BUG-001..013 verification scope, saved before tests. Local development spa only.
-- No schema migration is required. All fixtures in BugfixRegressionIntegrationTest
-- use negative IDs and the Spring test transaction always rolls back.
SELECT DATABASE(), @@hostname, @@port, CURRENT_USER();
SELECT COUNT(*) FROM appointment;
SELECT COUNT(*) FROM spa_order;
SELECT COUNT(*) FROM spa_payment;
SELECT COUNT(*) FROM spa_points_adjustment;
SELECT COUNT(*) FROM spa_mock_wallet_ledger;
SELECT a.id,a.appointment_date,a.status,o.id order_id
 FROM appointment a LEFT JOIN spa_order o ON o.appointment_id=a.id
 WHERE a.appointment_date>='2026-01-01' AND a.appointment_date<'2027-01-01' AND o.id IS NULL;
SELECT o.id,o.user_id,b.original_amount,b.points_used,o.amount,b.status
 FROM spa_order o JOIN spa_order_benefit b ON b.order_id=o.id WHERE b.points_used>0;
SELECT p.id,p.order_id,p.kind,p.amount,p.channel,p.status,p.created_at FROM spa_payment p;
-- Rollback-only fixture writes (the Java test supplies unique negative :fixture_id):
-- START TRANSACTION;
-- INSERT INTO staff(id,name,status) VALUES(:fixture_id,'bugfix-rollback-only',0);
-- INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(:fixture_id,'bugfix-rollback-only',1,30,1);
-- INSERT INTO staff_service(id,staff_id,service_id) VALUES(:fixture_id,:fixture_id,:fixture_id);
-- INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source)
-- VALUES(:fixture_id,:fixture_id,:fixture_date,'09:00','18:00',1,'MANUAL');
-- UPDATE staff SET status=1 WHERE id=:fixture_id;
-- The normal booking service inserts an appointment, occupied slots and its real unpaid order.
-- No payment/refund/cancellation/points adjustment calls are made.
-- Staff-only leave and valid catalog creation likewise use test-created rows only.
-- ROLLBACK;
-- After tests compare every original INSERT from the verified dump; no persistent business changes expected.

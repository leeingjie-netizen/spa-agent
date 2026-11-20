-- Opt-in only: RealConcurrentBusinessTest. Target must be localhost:3306/spa.
-- This test commits isolated fixtures so worker threads see identical state.
-- Never run these templates with real IDs. All fixture staff/user/service IDs are negative.
-- date is selected only when no existing schedule/appointment/business_day uses it.
-- Save this script before the test runner is enabled.
INSERT INTO spa_user(id,nickname,status) VALUES (:fixture_user,'concurrency-test',1);
INSERT INTO staff(id,name,status) VALUES (:fixture_staff,'concurrency-test',1);
INSERT INTO spa_service(id,name,duration_minutes,price,status) VALUES (:fixture_service,'concurrency-test',45,1.00,1);
INSERT INTO staff_service(id,staff_id,service_id) VALUES (:fixture_relation,:fixture_staff,:fixture_service);
INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source)
VALUES (:fixture_schedule,:fixture_staff,:unused_future_date,'09:00','18:00',1,'MANUAL');
-- Real application calls, not raw fixture SQL, create appointments/slots/orders concurrently.
-- Test 1 requires exactly one success for five distinct requests on same slot.
-- Test 2 requires identical request ID to return same appointment with no duplicate slots/orders.
-- Test 3 injects an exception in real slot mapper INSERT and requires transaction rollback.
-- Test 4 races store closure with booking; closed+active-booking must never coexist.
-- Cleanup in FK-safe order, restricted to appointments of :fixture_staff and own IDs:
DELETE FROM spa_payment WHERE order_id IN (SELECT id FROM spa_order WHERE user_id=:fixture_user);
DELETE FROM spa_review WHERE order_id IN (SELECT id FROM spa_order WHERE user_id=:fixture_user);
DELETE FROM spa_points_entry WHERE order_id IN (SELECT id FROM spa_order WHERE user_id=:fixture_user);
DELETE FROM spa_order WHERE user_id=:fixture_user;
DELETE FROM appointment_slot WHERE staff_id=:fixture_staff;
DELETE FROM appointment WHERE staff_id=:fixture_staff;
DELETE FROM staff_schedule WHERE staff_id=:fixture_staff;
DELETE FROM staff_service WHERE id=:fixture_relation;
DELETE FROM spa_service WHERE id=:fixture_service;
DELETE FROM staff WHERE id=:fixture_staff;
DELETE FROM spa_user WHERE id=:fixture_user;
-- DELETE business_day/schedule_day_lock ONLY for the originally unused :unused_future_date.

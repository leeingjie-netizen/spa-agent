-- Test fixture SQL blueprint. Execute ScheduleManagementIntegrationTest for assertions.
-- All inserts and service changes run inside Spring @Transactional @Rollback.
-- No COMMIT, DELETE, DDL, or existing data overwrites are performed.
START TRANSACTION;
-- INSERT INTO staff(id,name,status) VALUES (:negative_fixture_id,'transaction-test-fixture',1);
-- INSERT INTO staff_leave(...status...) VALUES (...,'PENDING',...);
-- Service locks schedule_day_lock(date), then checks appointment conflicts FOR UPDATE.
-- UPDATE staff_leave SET status='APPROVED',reviewed_by=:test_admin,reviewed_at=NOW()
-- WHERE id=:test_leave_id AND status='PENDING';
-- INSERT INTO schedule_exception(...) VALUES (:test_fixture_values);
-- INSERT INTO business_day(...) VALUES (:test_fixture_values);
-- INSERT INTO staff_schedule(...) VALUES (:test_fixture_values);
ROLLBACK;

-- Revocation test: insert a new random negative-ID technician and exception in rollback transaction;
-- INSERT schedule_exception_revocation for that new exception, retry idempotently;
-- verify original row remains and availability resumes; USER cannot revoke. Always ROLLBACK.

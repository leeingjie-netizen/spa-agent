-- Saved before executing LoyaltyBusinessIntegrationTest. Never apply to production.
-- :fixture is a unique negative test ID; all dynamic IDs created by services belong
-- to this same Spring-managed transaction, which is ALWAYS rolled back.
START TRANSACTION;
SELECT DATABASE(); -- must be spa on jdbc:mysql://127.0.0.1:3306
INSERT INTO spa_user(id,nickname,status) VALUES(:fixture,'loyalty-integration',1);
INSERT INTO staff(id,name,status) VALUES(:fixture,'loyalty-integration',1);
INSERT INTO spa_service(id,name,duration_minutes,price,status) VALUES(:fixture,'loyalty-integration',60,100,1);
INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source)
 VALUES(:fixture,:fixture,'2097-01-15','09:00','18:00',1,'MANUAL');
INSERT INTO appointment(id,appointment_no,user_id,service_id,staff_id,appointment_date,start_time,end_time,amount,status,request_id)
 VALUES(:fixture,:fixture_number,:fixture,:fixture,:fixture,'2097-01-15','10:00','11:00',100,1,:fixture_number);
INSERT INTO appointment_slot(id,appointment_id,staff_id,slot_time)
 VALUES(:fixture,:fixture,:fixture,'2097-01-15 10:00:00');
INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,expires_at)
 VALUES(:fixture,:fixture,:fixture_number,:fixture,100,DATE_ADD(NOW(),INTERVAL 1 DAY));
INSERT INTO spa_coupon_template(id,title,discount_amount,min_amount,expires_at,remaining)
 VALUES(:fixture,'loyalty-integration',10,50,DATE_ADD(NOW(),INTERVAL 1 DAY),1);
-- Invoke real proxied services to claim, apply points/coupon, mock-wallet topup,
-- pay, cancel/refund, complete, review. Assert resulting SQL rows in transaction.
-- Service SQL is versioned in LoyaltyService, OrderService, OperationsController.
-- Tests cover request replay, ownership, stock, reservation, refund ledger,
-- insufficient balance, expiry and review eligibility; no outside data is changed.
ROLLBACK;

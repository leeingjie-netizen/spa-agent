-- Only independently tagged fixtures, after verified backup. Historical orders and schedules retained.
START TRANSACTION;
UPDATE staff SET status=0 WHERE id=2122623883579772930 AND name='acceptance_56389bdb10ed4023';
UPDATE spa_service SET status=0 WHERE id=2122623883982426113 AND name='acceptance_56389bdb10ed4023';
UPDATE spa_coupon_template SET active=0 WHERE id=2122623908753985537 AND title='acceptance_56389bdb10ed4023';
DELETE FROM business_day WHERE work_date IN ('2027-04-22','2027-04-23') AND reason='acceptance_56389bdb10ed4023';
COMMIT;

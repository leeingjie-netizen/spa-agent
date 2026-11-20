-- Only independently tagged fixtures, after verified backup. Historical orders and schedules retained.
START TRANSACTION;
UPDATE staff SET status=0 WHERE id=2122512518718435330 AND name='acceptance_ec726241f3414c63';
UPDATE spa_service SET status=0 WHERE id=2122512519158837250 AND name='acceptance_ec726241f3414c63';
DELETE FROM business_day WHERE work_date IN ('2027-04-18','2027-04-19') AND reason='acceptance_ec726241f3414c63';
UPDATE staff SET status=0 WHERE id=2122514532252831746 AND name='acceptance_8274d0470b614894';
UPDATE spa_service SET status=0 WHERE id=2122514532823257089 AND name='acceptance_8274d0470b614894';
UPDATE spa_coupon_template SET active=0 WHERE id=2122514560967036930 AND title='acceptance_8274d0470b614894';
DELETE FROM business_day WHERE work_date IN ('2027-04-20','2027-04-21') AND reason='acceptance_8274d0470b614894';
COMMIT;

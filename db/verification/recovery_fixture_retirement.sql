-- Recovery acceptance run acceptance_e995875332de48fb; only newly created fixtures from its manifest.
-- Preserve all appointment, payment, wallet and audit records for inspection.
START TRANSACTION;
UPDATE staff SET status=0 WHERE id=2122493293547323394 AND name='acceptance_e995875332de48fb';
UPDATE spa_service SET status=0 WHERE id=2122493293744455681 AND name='acceptance_e995875332de48fb';
UPDATE spa_coupon_template SET active=0 WHERE id=2122493309116579841 AND title='acceptance_e995875332de48fb';
-- Remove only this run's two new global overrides: both dates were empty before the test.
DELETE FROM business_day WHERE work_date IN ('2027-04-16','2027-04-17') AND reason='acceptance_e995875332de48fb';
COMMIT;

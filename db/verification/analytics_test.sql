-- AnalyticsIntegrationTest uses random negative fixture IDs inside a Spring rollback transaction.
-- No DDL, update, delete or commit. Table schema was previously verified local-only.
START TRANSACTION;
-- INSERT spa_order with random negative id/appointment_id/user_id, explicit 2096-01-05 timestamp.
-- INSERT one successful MOCK_WALLET PAY of 130 and successful REFUND of 30 for that order.
-- INSERT unrelated TEST_TOPUP wallet ledger of 200 for same user.
-- Expect SIMULATED daily net=100.00, refunds=30.00, payment ledger total=2; REAL net=0.00.
-- Expect page two of size one contains exactly one item; USER/STAFF are forbidden.
ROLLBACK;

-- Saved before integration tests. Only random negative IDs, @Transactional rollback.
-- No DDL; no changes to existing business rows.
START TRANSACTION;
-- Insert one isolated order on 2095-02-05.
-- Insert MOCK_WALLET PAY130 and REFUND30, both SIMULATED_SUCCESS.
-- Insert wallet TOPUP200, PAYMENT-130, REFUND30, retaining independent audit rows.
-- Expect 5 audit rows, 3 wallet rows, correct order linkage and exact decimal strings.
-- Revenue must remain 100.00; REAL scope empty; USER and STAFF denied both ledger routes.
ROLLBACK;

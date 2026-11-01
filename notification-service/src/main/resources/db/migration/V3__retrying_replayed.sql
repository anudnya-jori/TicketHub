-- Scenario 10: RETRYING (mail provider failed, a delayed retry is scheduled). Scenario 12: REPLAYED (rebuilt from history, nothing sent).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_status_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_status_check
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING', 'REPLAYED'));

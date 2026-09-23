-- Align customer.status with V1 / Java (ProvisioningStatuses.CUSTOMER_IN_PROGRESS).
-- Older local DBs still had job-like statuses on customer (queued/running/...).

UPDATE customer
SET status = CASE status
    WHEN 'queued' THEN 'in_progress'
    WHEN 'running' THEN 'in_progress'
    WHEN 'completed_with_errors' THEN 'completed'
    ELSE status
END
WHERE status IN ('queued', 'running', 'completed_with_errors');

ALTER TABLE customer DROP CONSTRAINT IF EXISTS customer_status_check;

ALTER TABLE customer
    ALTER COLUMN status SET DEFAULT 'in_progress';

ALTER TABLE customer
    ADD CONSTRAINT customer_status_check
        CHECK (status IN ('in_progress', 'completed', 'failed'));

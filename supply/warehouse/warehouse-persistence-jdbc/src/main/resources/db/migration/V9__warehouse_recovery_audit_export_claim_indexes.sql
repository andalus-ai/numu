-- Keep the worker's ordered claim query efficient as the export queue grows.
-- Pending/verification jobs do not need the lease column in their index.
CREATE INDEX warehouse_recovery_export_job_pending_claim_idx
    ON warehouse_recovery_audit_export_job (created_at, export_id)
    WHERE status IN ('REQUESTED', 'VERIFYING');

-- Expired running jobs are the resumable work queue; the partial predicate avoids
-- indexing terminal and currently non-running rows for this claim path.
CREATE INDEX warehouse_recovery_export_job_expired_claim_idx
    ON warehouse_recovery_audit_export_job (lease_until, created_at, export_id)
    WHERE status = 'RUNNING';

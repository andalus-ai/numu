-- M23: bind immutable POD attempt references to persisted delivery tasks.
-- Legacy tasks receive a deterministic reference derived from their stable task ID.
ALTER TABLE courier_delivery_tasks
    ADD COLUMN delivery_attempt_reference VARCHAR(160);

UPDATE courier_delivery_tasks
SET delivery_attempt_reference = 'TASK:' || id::text
WHERE delivery_attempt_reference IS NULL;

ALTER TABLE courier_delivery_tasks
    ALTER COLUMN delivery_attempt_reference SET NOT NULL;

ALTER TABLE courier_delivery_tasks
    ADD CONSTRAINT ck_courier_delivery_attempt_reference
    CHECK (length(trim(delivery_attempt_reference)) BETWEEN 1 AND 160);

CREATE UNIQUE INDEX uq_courier_delivery_attempt_reference
    ON courier_delivery_tasks (tenant_id, delivery_attempt_reference);

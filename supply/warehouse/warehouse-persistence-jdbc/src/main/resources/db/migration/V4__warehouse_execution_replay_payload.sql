-- Persist the inventory command inputs needed to resume after a process restart.
ALTER TABLE warehouse_execution_attempt
    ADD COLUMN payload_item_id VARCHAR(200),
    ADD COLUMN payload_warehouse_id VARCHAR(200),
    ADD COLUMN payload_reservation_id VARCHAR(300),
    ADD COLUMN payload_quantity VARCHAR(100),
    ADD COLUMN payload_unit_cost VARCHAR(100),
    ADD COLUMN payload_currency VARCHAR(12),
    ADD COLUMN payload_reference VARCHAR(500);

ALTER TABLE warehouse_execution_attempt
    ADD CONSTRAINT ck_warehouse_execution_payload_shape CHECK (
        (payload_item_id IS NULL AND payload_warehouse_id IS NULL AND payload_reservation_id IS NULL
            AND payload_quantity IS NULL AND payload_unit_cost IS NULL AND payload_currency IS NULL
            AND payload_reference IS NULL)
        OR
        (payload_item_id IS NOT NULL AND payload_warehouse_id IS NOT NULL AND payload_quantity IS NOT NULL
            AND payload_currency IS NOT NULL AND payload_reference IS NOT NULL
            AND ((operation = 'receive' AND payload_unit_cost IS NOT NULL AND payload_reservation_id IS NULL)
              OR (operation = 'pick' AND payload_unit_cost IS NULL AND payload_reservation_id IS NOT NULL))
        )
    );

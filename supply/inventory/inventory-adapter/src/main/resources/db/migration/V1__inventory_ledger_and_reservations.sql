-- Tenant-scoped inventory ledger and durable command receipts.
-- Reservation and movement writes serialize on inventory_stock_locks to prevent overselling.
CREATE TABLE IF NOT EXISTS inventory_items (
    tenant_id VARCHAR(80) NOT NULL,
    item_id VARCHAR(200) NOT NULL,
    sku VARCHAR(200) NOT NULL,
    name VARCHAR(500) NOT NULL,
    valuation_method VARCHAR(40) NOT NULL,
    tracking_method VARCHAR(40) NOT NULL,
    uom VARCHAR(80) NOT NULL,
    PRIMARY KEY (tenant_id, item_id),
    UNIQUE (tenant_id, sku)
);

CREATE TABLE IF NOT EXISTS inventory_warehouses (
    tenant_id VARCHAR(80) NOT NULL,
    warehouse_id VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(300) NOT NULL,
    location TEXT,
    site_id VARCHAR(200),
    facility_id VARCHAR(200),
    PRIMARY KEY (tenant_id, warehouse_id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS inventory_stock_locks (
    tenant_id VARCHAR(80) NOT NULL,
    item_id VARCHAR(200) NOT NULL,
    warehouse_id VARCHAR(200) NOT NULL,
    PRIMARY KEY (tenant_id, item_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS inventory_stock_movements (
    tenant_id VARCHAR(80) NOT NULL,
    movement_id VARCHAR(200) NOT NULL,
    item_id VARCHAR(200) NOT NULL,
    warehouse_id VARCHAR(200) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity <> 0),
    unit_cost NUMERIC(24, 8) NOT NULL CHECK (unit_cost >= 0),
    currency CHAR(3) NOT NULL,
    reference_number VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, movement_id)
);
CREATE INDEX IF NOT EXISTS ix_inventory_movements_item_warehouse
    ON inventory_stock_movements (tenant_id, item_id, warehouse_id, occurred_at, movement_id);

CREATE TABLE IF NOT EXISTS inventory_reservations (
    tenant_id VARCHAR(80) NOT NULL,
    reservation_id VARCHAR(200) NOT NULL,
    item_id VARCHAR(200) NOT NULL,
    warehouse_id VARCHAR(200) NOT NULL,
    storage_location_id VARCHAR(200),
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    uom VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED')),
    source_reference VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    version INTEGER NOT NULL CHECK (version >= 1),
    PRIMARY KEY (tenant_id, reservation_id)
);
CREATE INDEX IF NOT EXISTS ix_inventory_reservations_availability
    ON inventory_reservations (tenant_id, item_id, warehouse_id, status, expires_at);

CREATE TABLE IF NOT EXISTS inventory_reservation_commands (
    tenant_id VARCHAR(80) NOT NULL,
    command_scope VARCHAR(200) NOT NULL,
    command_key VARCHAR(200) NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    reservation_id VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, command_scope, command_key),
    FOREIGN KEY (tenant_id, reservation_id)
        REFERENCES inventory_reservations (tenant_id, reservation_id)
);

CREATE TABLE IF NOT EXISTS inventory_command_receipts (
    tenant_id VARCHAR(80) NOT NULL,
    command_scope VARCHAR(200) NOT NULL,
    command_key VARCHAR(200) NOT NULL,
    command_type VARCHAR(40) NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    result_ids TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, command_scope, command_key)
);

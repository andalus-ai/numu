-- Fuel Transactions
CREATE TABLE IF NOT EXISTS fuel_transactions (
    id UUID PRIMARY KEY,
    vehicle_id VARCHAR(255) NOT NULL,
    driver_id VARCHAR(255),
    fuel_card_id VARCHAR(255),
    transaction_number VARCHAR(50) NOT NULL UNIQUE,
    transaction_date TIMESTAMP NOT NULL,
    station_name VARCHAR(255),
    station_address TEXT,
    fuel_type VARCHAR(50) NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    quantity_unit VARCHAR(10) DEFAULT 'LITERS',
    unit_price DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) DEFAULT 0,
    tax_amount DECIMAL(19,2) DEFAULT 0,
    net_amount DECIMAL(19,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    odometer_reading DECIMAL(19,4),
    status VARCHAR(20) DEFAULT 'PENDING',
    authorization_code VARCHAR(50),
    transaction_id VARCHAR(255),
    fuel_card_number_masked VARCHAR(50),
    promotion_code VARCHAR(50),
    notes TEXT,
    active BOOLEAN DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Routes
CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY,
    route_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    start_location VARCHAR(255) NOT NULL,
    end_location VARCHAR(255) NOT NULL,
    stops_json TEXT,
    total_distance DECIMAL(19,4),
    estimated_duration DECIMAL(19,4),
    actual_duration DECIMAL(19,4),
    vehicle_id VARCHAR(255),
    driver_id VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PLANNED',
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    fuel_consumption DECIMAL(19,4),
    cost DECIMAL(19,2),
    currency_code VARCHAR(3),
    waypoints_json TEXT,
    notes TEXT,
    active BOOLEAN DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Geofences
CREATE TABLE IF NOT EXISTS geofences (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,
    center_latitude DECIMAL(10,8),
    center_longitude DECIMAL(11,8),
    radius DECIMAL(19,4),
    points_json TEXT,
    min_latitude DECIMAL(10,8),
    min_longitude DECIMAL(11,8),
    max_latitude DECIMAL(10,8),
    max_longitude DECIMAL(11,8),
    vehicle_ids_json TEXT,
    driver_ids_json TEXT,
    alerts_json TEXT,
    active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Geofence Events
CREATE TABLE IF NOT EXISTS geofence_events (
    id UUID PRIMARY KEY,
    geofence_id UUID NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    vehicle_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    FOREIGN KEY (geofence_id) REFERENCES geofences(id)
);

-- Indexes
CREATE INDEX idx_fuel_vehicle ON fuel_transactions(vehicle_id);
CREATE INDEX idx_fuel_date ON fuel_transactions(transaction_date);
CREATE INDEX idx_fuel_status ON fuel_transactions(status);

CREATE INDEX idx_routes_status ON routes(status);
CREATE INDEX idx_routes_vehicle ON routes(vehicle_id);
CREATE INDEX idx_routes_driver ON routes(driver_id);

CREATE INDEX idx_geofences_type ON geofences(type);
CREATE INDEX idx_geofences_active ON geofences(active);
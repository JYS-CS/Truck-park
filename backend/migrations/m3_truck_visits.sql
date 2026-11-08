-- Milestone 3: Truck visits and vehicles.
-- Run once on existing databases. The same DDL is included in schema.sql for fresh installs.

CREATE SEQUENCE seq_visit_ref START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE vehicles (
  id           VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  plate_number VARCHAR2(20) NOT NULL,
  created_by   VARCHAR2(36) REFERENCES users(id),
  created_at   TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_vehicles_plate UNIQUE (plate_number)
);

-- id is generated on the handset so that retried syncs are idempotent.
CREATE TABLE truck_visits (
  id                VARCHAR2(36) PRIMARY KEY,
  reference         VARCHAR2(30) NOT NULL,
  vehicle_id        VARCHAR2(36) NOT NULL REFERENCES vehicles(id),
  plate_number      VARCHAR2(20) NOT NULL,
  visit_type        VARCHAR2(20) NOT NULL,
  status            VARCHAR2(12) DEFAULT 'OPEN' NOT NULL,
  driver_name       VARCHAR2(200),
  driver_phone      VARCHAR2(40),
  arrival_notes     VARCHAR2(1000),
  arrived_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  arrived_by        VARCHAR2(36) NOT NULL REFERENCES users(id),
  arrival_device_id VARCHAR2(36),
  departed_at       TIMESTAMP WITH TIME ZONE,
  departed_by       VARCHAR2(36) REFERENCES users(id),
  departure_notes   VARCHAR2(1000),
  row_version       NUMBER DEFAULT 1 NOT NULL,
  created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_visit_reference UNIQUE (reference),
  CONSTRAINT chk_visit_type CHECK (visit_type IN ('QUICK', 'OVERNIGHT', 'TRANSIT', 'LOADING', 'OTHER')),
  CONSTRAINT chk_visit_status CHECK (status IN ('OPEN', 'COMPLETED')),
  CONSTRAINT chk_visit_departure CHECK (departed_at IS NULL OR departed_at >= arrived_at)
);

CREATE INDEX idx_visits_plate_status ON truck_visits(plate_number, status);
CREATE INDEX idx_visits_status_arrived ON truck_visits(status, arrived_at);


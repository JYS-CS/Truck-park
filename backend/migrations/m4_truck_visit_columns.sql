-- Milestone 4: Add country_code, front_plate, horse_plate, transporter to truck_visits.
-- Run once on existing databases. The same DDL is included in schema.sql for fresh installs.

ALTER TABLE truck_visits ADD (
  country_code VARCHAR2(10) DEFAULT 'ZM',
  front_plate  VARCHAR2(20),
  horse_plate  VARCHAR2(20),
  transporter  VARCHAR2(100)
);

-- Backfill front_plate, horse_plate, and country_code from plate_number for existing records
UPDATE truck_visits
   SET front_plate  = NVL(front_plate, plate_number),
       horse_plate  = NVL(horse_plate, plate_number),
       country_code = NVL(country_code, 'ZM')
 WHERE front_plate IS NULL OR horse_plate IS NULL;

COMMIT;

CREATE INDEX idx_visits_front_plate ON truck_visits(front_plate, status);
CREATE INDEX idx_visits_horse_plate ON truck_visits(horse_plate, status);
CREATE INDEX idx_visits_transporter ON truck_visits(transporter);


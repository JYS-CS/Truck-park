-- Milestone 3–5: Add cargo, seals, and visit correction history tables/columns.

ALTER TABLE truck_visits ADD (
  cargo_description VARCHAR2(500),
  cargo_items_json  CLOB,
  seals_json        CLOB
);

CREATE TABLE visit_corrections (
  id              VARCHAR2(36) DEFAULT LOWER(RAWTOHEX(SYS_GUID())) PRIMARY KEY,
  visit_id        VARCHAR2(36) NOT NULL REFERENCES truck_visits(id),
  edited_by       VARCHAR2(36) NOT NULL REFERENCES users(id),
  edited_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  reason          VARCHAR2(500),
  changes_summary VARCHAR2(1000) NOT NULL,
  was_critical    CHAR(1) DEFAULT 'N' NOT NULL,
  before_json     CLOB,
  after_json      CLOB,
  CONSTRAINT chk_vc_critical CHECK (was_critical IN ('Y', 'N'))
);

CREATE INDEX idx_visit_corrections_visit ON visit_corrections(visit_id, edited_at);


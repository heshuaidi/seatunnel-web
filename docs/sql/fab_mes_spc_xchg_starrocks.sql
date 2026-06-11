-- Fab MES/SPC translator StarRocks xchg layer example.
-- These are business target tables, not seatunnel-web metadata tables.

CREATE TABLE IF NOT EXISTS xchg_batch (
  batch_id           VARCHAR(128) NOT NULL,
  task_code          VARCHAR(128),
  source_system      VARCHAR(64),
  batch_start_value  VARCHAR(256),
  batch_end_value    VARCHAR(256),
  batch_start_time   DATETIME,
  batch_end_time     DATETIME,
  status             VARCHAR(32),
  source_count       BIGINT,
  header_count       BIGINT,
  site_count         BIGINT,
  error_count        BIGINT,
  created_time       DATETIME,
  updated_time       DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id)
DISTRIBUTED BY HASH(batch_id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS xchg_meas_header (
  batch_id       VARCHAR(128) NOT NULL,
  meas_id        VARCHAR(256) NOT NULL,
  source_system  VARCHAR(64),
  source_table   VARCHAR(128),
  lot_id         VARCHAR(128),
  wafer_id       VARCHAR(128),
  product_id     VARCHAR(128),
  step_id        VARCHAR(128),
  tool_id        VARCHAR(128),
  chamber_id     VARCHAR(128),
  recipe_id      VARCHAR(128),
  param_code     VARCHAR(128),
  measure_time   DATETIME,
  update_time    DATETIME,
  ingest_time    DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, meas_id)
DISTRIBUTED BY HASH(batch_id, meas_id) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS xchg_meas_site (
  batch_id      VARCHAR(128) NOT NULL,
  meas_id       VARCHAR(256) NOT NULL,
  site_no       BIGINT NOT NULL,
  die_x         DOUBLE,
  die_y         DOUBLE,
  site_x        DOUBLE,
  site_y        DOUBLE,
  value_num     DOUBLE,
  spec_low      DOUBLE,
  spec_high     DOUBLE,
  is_oos        BOOLEAN,
  update_time   DATETIME,
  ingest_time   DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, meas_id, site_no)
DISTRIBUTED BY HASH(batch_id, meas_id, site_no) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS xchg_meas_error (
  batch_id       VARCHAR(128) NOT NULL,
  error_id       VARCHAR(256) NOT NULL,
  source_system  VARCHAR(64),
  source_table   VARCHAR(128),
  source_key     VARCHAR(512),
  error_type     VARCHAR(64),
  error_reason   VARCHAR(1000),
  raw_payload    STRING,
  created_time   DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, error_id)
DISTRIBUTED BY HASH(batch_id, error_id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

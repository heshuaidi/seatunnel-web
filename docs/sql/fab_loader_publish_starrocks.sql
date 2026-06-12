-- StarRocks DDL example for Fab loader publish target layers.
-- These are business tables, not seatunnel-web metadata tables.

CREATE TABLE IF NOT EXISTS eda_stg_measure_header (
  batch_id        VARCHAR(128) NOT NULL,
  meas_id         VARCHAR(256) NOT NULL,
  source_system   VARCHAR(64),
  source_table    VARCHAR(128),
  lot_id          VARCHAR(128),
  wafer_id        VARCHAR(128),
  product_id      VARCHAR(128),
  step_id         VARCHAR(128),
  tool_id         VARCHAR(128),
  chamber_id      VARCHAR(128),
  recipe_id       VARCHAR(128),
  param_code      VARCHAR(128),
  measure_time    DATETIME,
  update_time     DATETIME,
  ingest_time     DATETIME,
  publish_time    DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, meas_id)
DISTRIBUTED BY HASH(batch_id, meas_id) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS eda_stg_measure_site (
  batch_id       VARCHAR(128) NOT NULL,
  meas_id        VARCHAR(256) NOT NULL,
  site_no        BIGINT NOT NULL,
  die_x          DOUBLE,
  die_y          DOUBLE,
  site_x         DOUBLE,
  site_y         DOUBLE,
  value_num      DOUBLE,
  spec_low       DOUBLE,
  spec_high      DOUBLE,
  is_oos         BOOLEAN,
  update_time    DATETIME,
  ingest_time    DATETIME,
  publish_time   DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, meas_id, site_no)
DISTRIBUTED BY HASH(batch_id, meas_id, site_no) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS eda_stg_measure_error (
  batch_id        VARCHAR(128) NOT NULL,
  error_id        VARCHAR(256) NOT NULL,
  source_system   VARCHAR(64),
  source_table    VARCHAR(128),
  source_key      VARCHAR(512),
  error_type      VARCHAR(64),
  error_reason    VARCHAR(1000),
  raw_payload     STRING,
  created_time    DATETIME,
  publish_time    DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, error_id)
DISTRIBUTED BY HASH(batch_id, error_id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS dwd_measure_header (
  meas_id          VARCHAR(256) NOT NULL,
  latest_batch_id  VARCHAR(128),
  source_system    VARCHAR(64),
  source_table     VARCHAR(128),
  lot_id           VARCHAR(128),
  wafer_id         VARCHAR(128),
  product_id       VARCHAR(128),
  step_id          VARCHAR(128),
  tool_id          VARCHAR(128),
  chamber_id       VARCHAR(128),
  recipe_id        VARCHAR(128),
  param_code       VARCHAR(128),
  measure_time     DATETIME,
  update_time      DATETIME,
  publish_time     DATETIME
)
ENGINE=OLAP
PRIMARY KEY(meas_id)
DISTRIBUTED BY HASH(meas_id) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS dwd_measure_site (
  meas_id           VARCHAR(256) NOT NULL,
  site_no           BIGINT NOT NULL,
  latest_batch_id   VARCHAR(128),
  lot_id            VARCHAR(128),
  wafer_id          VARCHAR(128),
  product_id        VARCHAR(128),
  step_id           VARCHAR(128),
  tool_id           VARCHAR(128),
  param_code        VARCHAR(128),
  value_num         DOUBLE,
  spec_low          DOUBLE,
  spec_high         DOUBLE,
  is_oos            BOOLEAN,
  update_time       DATETIME,
  publish_time      DATETIME
)
ENGINE=OLAP
PRIMARY KEY(meas_id, site_no)
DISTRIBUTED BY HASH(meas_id, site_no) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS ads_measure_summary_daily (
  summary_date   DATE NOT NULL,
  source_system  VARCHAR(64) NOT NULL,
  product_id     VARCHAR(128) NOT NULL,
  step_id        VARCHAR(128) NOT NULL,
  tool_id        VARCHAR(128) NOT NULL,
  param_code     VARCHAR(128) NOT NULL,
  measure_count  BIGINT,
  oos_count      BIGINT,
  avg_value      DOUBLE,
  min_value      DOUBLE,
  max_value      DOUBLE,
  updated_time   DATETIME
)
ENGINE=OLAP
PRIMARY KEY(summary_date, source_system, product_id, step_id, tool_id, param_code)
DISTRIBUTED BY HASH(summary_date, source_system, product_id, step_id, tool_id, param_code) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);

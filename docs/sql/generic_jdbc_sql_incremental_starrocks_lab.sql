-- Generic JDBC/SQL -> StarRocks incremental lab tables.
-- This script uses StarRocks as both JDBC source and StarRocks sink so the
-- increment/watermark/check loop can be tested without a separate source system.

CREATE TABLE IF NOT EXISTS lab_src_order (
  id BIGINT NOT NULL,
  biz_no VARCHAR(128),
  amount DECIMAL(18, 2),
  update_time DATETIME NOT NULL
)
ENGINE=OLAP
PRIMARY KEY(id)
DISTRIBUTED BY HASH(id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS lab_sink_order (
  batch_id VARCHAR(128) NOT NULL,
  id BIGINT NOT NULL,
  biz_no VARCHAR(128),
  amount DECIMAL(18, 2),
  update_time DATETIME,
  run_id VARCHAR(128),
  ingest_time DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, id)
DISTRIBUTED BY HASH(batch_id, id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS lab_sink_order_error (
  batch_id VARCHAR(128) NOT NULL,
  error_id VARCHAR(256) NOT NULL,
  source_key VARCHAR(256),
  error_type VARCHAR(64),
  error_reason VARCHAR(1000),
  created_time DATETIME
)
ENGINE=OLAP
PRIMARY KEY(batch_id, error_id)
DISTRIBUTED BY HASH(batch_id, error_id) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);

-- Initial batch for UPDATE_TIME_RANGE and ID_RANGE examples.
INSERT INTO lab_src_order VALUES
(1, 'ORD_001', 10.50, '2026-06-01 00:01:00'),
(2, 'ORD_002', 20.00, '2026-06-01 00:02:00'),
(3, 'ORD_003', 30.00, '2026-06-01 00:03:00');

-- Second batch for repeat-run tests.
INSERT INTO lab_src_order VALUES
(4, 'ORD_004', 40.00, '2026-06-01 01:01:00'),
(5, 'ORD_005', 50.00, '2026-06-01 01:02:00');

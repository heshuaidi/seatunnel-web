-- StarRocks publish SQL example for Fab loader publish.
-- This file is a document/template for eda_stg -> dwd/ads. It is not executed by
-- the current sync run coordinator.

-- 1. Publish header
INSERT INTO dwd_measure_header (
  meas_id,
  latest_batch_id,
  source_system,
  source_table,
  lot_id,
  wafer_id,
  product_id,
  step_id,
  tool_id,
  chamber_id,
  recipe_id,
  param_code,
  measure_time,
  update_time,
  publish_time
)
SELECT
  meas_id,
  batch_id AS latest_batch_id,
  source_system,
  source_table,
  lot_id,
  wafer_id,
  product_id,
  step_id,
  tool_id,
  chamber_id,
  recipe_id,
  param_code,
  measure_time,
  update_time,
  CURRENT_TIMESTAMP AS publish_time
FROM eda_stg_measure_header
WHERE batch_id = '${xchg_batch_id}';

-- 2. Publish site
INSERT INTO dwd_measure_site (
  meas_id,
  site_no,
  latest_batch_id,
  lot_id,
  wafer_id,
  product_id,
  step_id,
  tool_id,
  param_code,
  value_num,
  spec_low,
  spec_high,
  is_oos,
  update_time,
  publish_time
)
SELECT
  s.meas_id,
  s.site_no,
  s.batch_id AS latest_batch_id,
  h.lot_id,
  h.wafer_id,
  h.product_id,
  h.step_id,
  h.tool_id,
  h.param_code,
  s.value_num,
  s.spec_low,
  s.spec_high,
  s.is_oos,
  s.update_time,
  CURRENT_TIMESTAMP AS publish_time
FROM eda_stg_measure_site s
JOIN eda_stg_measure_header h
  ON s.batch_id = h.batch_id
 AND s.meas_id = h.meas_id
WHERE s.batch_id = '${xchg_batch_id}';

-- 3. Refresh ADS daily summary for touched dates
INSERT INTO ads_measure_summary_daily (
  summary_date,
  source_system,
  product_id,
  step_id,
  tool_id,
  param_code,
  measure_count,
  oos_count,
  avg_value,
  min_value,
  max_value,
  updated_time
)
SELECT
  DATE(h.measure_time) AS summary_date,
  h.source_system,
  h.product_id,
  h.step_id,
  h.tool_id,
  h.param_code,
  COUNT(*) AS measure_count,
  SUM(CASE WHEN s.is_oos THEN 1 ELSE 0 END) AS oos_count,
  AVG(s.value_num) AS avg_value,
  MIN(s.value_num) AS min_value,
  MAX(s.value_num) AS max_value,
  CURRENT_TIMESTAMP AS updated_time
FROM dwd_measure_site s
JOIN dwd_measure_header h
  ON s.meas_id = h.meas_id
WHERE s.latest_batch_id = '${xchg_batch_id}'
GROUP BY
  DATE(h.measure_time),
  h.source_system,
  h.product_id,
  h.step_id,
  h.tool_id,
  h.param_code;

CREATE DATABASE IF NOT EXISTS balan_metrics DURATION 1d KEEP 7d;
CREATE STABLE IF NOT EXISTS balan_metrics.device_metric (ts TIMESTAMP, voltage FLOAT, current_a FLOAT, temperature FLOAT, power_kw FLOAT, energy_total FLOAT, signal_strength INT) TAGS (station_id BIGINT, charger_id BIGINT, port_no INT, tenant_id BIGINT);

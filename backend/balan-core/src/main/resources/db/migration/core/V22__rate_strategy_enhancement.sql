ALTER TABLE rate_strategy
    ADD COLUMN strategy_type  VARCHAR(16) NOT NULL DEFAULT 'ENERGY'
        COMMENT '策略类型：ENERGY 电价策略 / SERVICE 服务费策略' AFTER billing_mode,
    ADD COLUMN effective_from DATE NULL COMMENT '生效开始日期（含）' AFTER strategy_type,
    ADD COLUMN effective_to   DATE NULL COMMENT '生效结束日期（含）' AFTER effective_from;

CREATE INDEX idx_rate_strategy_station_type
    ON rate_strategy (tenant_id, station_id, strategy_type, enabled);

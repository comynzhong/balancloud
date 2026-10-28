CREATE TABLE IF NOT EXISTS rate_strategy (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '策略ID',
    station_id   BIGINT       NOT NULL COMMENT '所属场站',
    name         VARCHAR(100) NOT NULL COMMENT '策略名称',
    billing_mode VARCHAR(20)  NOT NULL COMMENT 'TIME / ENERGY / MIXED',
    enabled      TINYINT      NOT NULL DEFAULT 0 COMMENT '1 启用',
    remark       VARCHAR(500) DEFAULT NULL,
    create_by    VARCHAR(64)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(64)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    KEY idx_rate_station (station_id, enabled, del_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '费率策略';

CREATE TABLE IF NOT EXISTS rate_period (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '时段ID',
    strategy_id   BIGINT        NOT NULL COMMENT '策略ID',
    period_type   VARCHAR(20)   NOT NULL COMMENT 'PEAK / FLAT / VALLEY',
    start_time    TIME          NOT NULL COMMENT '开始时间，包含',
    end_time      TIME          NOT NULL COMMENT '结束时间，不包含；start=end 表示全天',
    time_price    DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '时间单价 元/小时',
    energy_price  DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '电量单价 元/kWh',
    service_price DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '服务费单价',
    service_unit  VARCHAR(10)   NOT NULL DEFAULT 'KWH' COMMENT 'KWH / HOUR',
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    KEY idx_rate_period_strategy (strategy_id, del_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '费率峰平谷时段';

CREATE TABLE IF NOT EXISTS charge_order (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no             VARCHAR(64)   NOT NULL COMMENT '订单号',
    station_id           BIGINT        NOT NULL COMMENT '场站ID',
    charger_id           BIGINT        NOT NULL COMMENT '设备ID',
    port_no              INT           NOT NULL COMMENT '端口号',
    rate_strategy_id     BIGINT        NOT NULL COMMENT '费率策略ID',
    rate_strategy_name   VARCHAR(100)  DEFAULT NULL COMMENT '费率策略名称快照',
    billing_mode         VARCHAR(20)   NOT NULL COMMENT 'TIME / ENERGY / MIXED',
    rate_snapshot_json   LONGTEXT      NOT NULL COMMENT '启动时费率快照',
    start_time           DATETIME      DEFAULT NULL COMMENT '计费开始时间',
    end_time             DATETIME      DEFAULT NULL COMMENT '结束时间',
    duration_seconds     INT           NOT NULL DEFAULT 0 COMMENT '累计计费秒数',
    start_meter          DECIMAL(14,4) DEFAULT NULL COMMENT '起始电表',
    end_meter            DECIMAL(14,4) DEFAULT NULL COMMENT '结束电表',
    energy_kwh           DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '累计电量',
    time_fee             DECIMAL(12,4) NOT NULL DEFAULT 0,
    energy_fee           DECIMAL(12,4) NOT NULL DEFAULT 0,
    service_fee          DECIMAL(12,4) NOT NULL DEFAULT 0,
    total_fee            DECIMAL(12,4) NOT NULL DEFAULT 0,
    max_duration_minutes INT           NOT NULL COMMENT '最大时长分钟',
    charge_limit_amount  DECIMAL(12,4) NOT NULL COMMENT '金额上限',
    status               VARCHAR(20)   NOT NULL COMMENT 'PENDING_START/CHARGING/STOPPING/COMPLETED/FAILED/ABNORMAL',
    payment_status       VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID / PAID',
    stop_reason          VARCHAR(30)   DEFAULT NULL,
    stop_message         VARCHAR(255)  DEFAULT NULL,
    paid_at              DATETIME      DEFAULT NULL,
    start_cmd_id         VARCHAR(64)   DEFAULT NULL,
    stop_cmd_id          VARCHAR(64)   DEFAULT NULL,
    last_metric_ts       BIGINT        DEFAULT NULL COMMENT '最近计费测点 epoch 毫秒',
    last_meter           DECIMAL(14,4) DEFAULT NULL COMMENT '最近计费电表',
    last_period_type     VARCHAR(20)   DEFAULT NULL COMMENT '最近计费时段',
    create_by            VARCHAR(64)   DEFAULT NULL,
    create_time          DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_by            VARCHAR(64)   DEFAULT NULL,
    update_time          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag             CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_station_start (station_id, start_time),
    KEY idx_order_charger_port (charger_id, port_no, status),
    KEY idx_order_status_start (status, start_time),
    KEY idx_order_payment (payment_status, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '端口级充电订单';

CREATE TABLE IF NOT EXISTS order_rate_detail (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    order_id         BIGINT        NOT NULL COMMENT '订单ID',
    period_type      VARCHAR(20)   NOT NULL COMMENT 'PEAK / FLAT / VALLEY',
    time_price       DECIMAL(12,4) NOT NULL DEFAULT 0,
    energy_price     DECIMAL(12,4) NOT NULL DEFAULT 0,
    service_price    DECIMAL(12,4) NOT NULL DEFAULT 0,
    service_unit     VARCHAR(10)   NOT NULL DEFAULT 'KWH',
    duration_seconds INT           NOT NULL DEFAULT 0,
    energy_kwh       DECIMAL(14,4) NOT NULL DEFAULT 0,
    time_fee         DECIMAL(12,4) NOT NULL DEFAULT 0,
    energy_fee       DECIMAL(12,4) NOT NULL DEFAULT 0,
    service_fee      DECIMAL(12,4) NOT NULL DEFAULT 0,
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_period (order_id, period_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单分时段计费明细';

CREATE TABLE IF NOT EXISTS device_command (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '指令ID',
    cmd_id         VARCHAR(64)   NOT NULL COMMENT '指令唯一号',
    charger_id     BIGINT        NOT NULL COMMENT '设备ID',
    port_no        INT           DEFAULT NULL COMMENT '端口号',
    type           VARCHAR(20)   NOT NULL COMMENT 'START/STOP/UNLOCK/SET_THRESHOLD',
    payload_json   VARCHAR(2000) DEFAULT NULL COMMENT '下发参数',
    status         VARCHAR(20)   NOT NULL COMMENT 'SENT/ACKED/FAILED/TIMEOUT',
    retry_count    INT           NOT NULL DEFAULT 0,
    sent_at        DATETIME      DEFAULT NULL,
    acked_at       DATETIME      DEFAULT NULL,
    result_message VARCHAR(255)  DEFAULT NULL,
    create_by      VARCHAR(64)   DEFAULT NULL,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64)   DEFAULT NULL,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_command_cmd_id (cmd_id),
    KEY idx_command_port (charger_id, port_no, status),
    KEY idx_command_status_sent (status, sent_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备指令流水';

INSERT INTO rate_strategy (id, station_id, name, billing_mode, enabled, remark, create_by)
VALUES (1, 1, '滨江分时费率（演示）', 'MIXED', 1, '峰平谷三段，服务费按 kWh；时段覆盖全天且不重叠', 'system');

INSERT INTO rate_period (strategy_id, period_type, start_time, end_time, time_price, energy_price, service_price, service_unit)
VALUES (1, 'VALLEY', '22:00:00', '06:00:00', 0.5000, 0.3000, 0.2000, 'KWH'),
       (1, 'FLAT', '06:00:00', '08:00:00', 0.7000, 0.6000, 0.3000, 'KWH'),
       (1, 'PEAK', '08:00:00', '22:00:00', 1.0000, 0.9000, 0.4000, 'KWH');

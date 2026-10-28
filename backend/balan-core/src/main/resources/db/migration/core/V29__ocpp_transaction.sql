-- §48 OCPP 1.6J：CP 交易号与平台订单的映射。
-- id 直接作为下发给充电桩的 transactionId；电表单位沿用 OCPP 的 Wh，接进订单时再换成 kWh。
CREATE TABLE IF NOT EXISTS ocpp_transaction (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '同时作为下发给 CP 的 transactionId',
    tenant_id    BIGINT       NOT NULL DEFAULT 0,
    charger_id   BIGINT       NOT NULL,
    device_no    VARCHAR(64)  NOT NULL COMMENT 'CP identity',
    connector_id INT          NOT NULL COMMENT 'OCPP connectorId，从 1 开始',
    order_no     VARCHAR(32)  NOT NULL,
    id_tag       VARCHAR(64)  DEFAULT NULL,
    meter_start  INT          DEFAULT NULL COMMENT 'Wh',
    meter_stop   INT          DEFAULT NULL COMMENT 'Wh',
    start_time   DATETIME     DEFAULT NULL,
    stop_time    DATETIME     DEFAULT NULL,
    stop_reason  VARCHAR(40)  DEFAULT NULL COMMENT 'OCPP 原始 reason',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ocpp_order (tenant_id, order_no),
    KEY idx_ocpp_charger_connector (tenant_id, charger_id, connector_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OCPP 1.6J 交易映射';

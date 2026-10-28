CREATE TABLE IF NOT EXISTS station (
    id          BIGINT       NOT NULL COMMENT '场站ID',
    name        VARCHAR(100) NOT NULL COMMENT '场站名称',
    address     VARCHAR(255) DEFAULT '' COMMENT '场站地址',
    capacity_kw DECIMAL(10, 2) DEFAULT 0 COMMENT '额定容量 kW',
    manager     VARCHAR(50)  DEFAULT '' COMMENT '负责人',
    phone       VARCHAR(30)  DEFAULT '' COMMENT '联系电话',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 运营中 DISABLED 停用',
    remark      VARCHAR(500) DEFAULT NULL,
    create_by   VARCHAR(64)  DEFAULT NULL,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64)  DEFAULT NULL,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    KEY idx_station_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '充电场站';

CREATE TABLE IF NOT EXISTS charger (
    id               BIGINT       NOT NULL COMMENT '设备ID',
    station_id       BIGINT       NOT NULL COMMENT '归属场站',
    device_no        VARCHAR(64)  NOT NULL COMMENT '设备编号 / MQTT deviceId',
    name             VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_type      VARCHAR(20)  NOT NULL COMMENT 'SOCKET_2 / SOCKET_10 / SOCKET_12',
    port_count       INT          NOT NULL COMMENT '端口数',
    protocol         VARCHAR(30)  NOT NULL DEFAULT 'BALAN_MQTT' COMMENT '协议适配器',
    firmware_version VARCHAR(30)  DEFAULT '' COMMENT '固件版本',
    rated_voltage    DECIMAL(8, 2) DEFAULT 220.00 COMMENT '额定电压 V',
    rated_power_kw   DECIMAL(8, 3) DEFAULT 2.200 COMMENT '单端口额定功率 kW',
    over_temp_c      DECIMAL(6, 2) DEFAULT 60.00 COMMENT '过温阈值 °C',
    over_current_a   DECIMAL(6, 2) DEFAULT 10.00 COMMENT '过流阈值 A',
    online_status    VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE / OFFLINE',
    last_online_at   DATETIME     DEFAULT NULL,
    last_metric_at   DATETIME     DEFAULT NULL,
    is_simulated     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 仿真设备',
    remark           VARCHAR(500) DEFAULT NULL,
    create_by        VARCHAR(64)  DEFAULT NULL,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(64)  DEFAULT NULL,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_charger_device_no (device_no),
    KEY idx_charger_station (station_id),
    KEY idx_charger_online (online_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '充电设备（多路插座控制器）';

CREATE TABLE IF NOT EXISTS charger_port (
    id               BIGINT       NOT NULL COMMENT '端口ID',
    charger_id       BIGINT       NOT NULL COMMENT '设备ID',
    port_no          INT          NOT NULL COMMENT '端口号 1..N',
    rated_current_a  DECIMAL(6, 2) DEFAULT 10.00 COMMENT '额定电流 A',
    rated_power_kw   DECIMAL(8, 3) DEFAULT 2.200 COMMENT '额定功率 kW',
    status           VARCHAR(20)  NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE / CHARGING / FAULT',
    current_order_no VARCHAR(64)  DEFAULT NULL COMMENT '进行中订单号',
    last_metric_at   DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_charger_port (charger_id, port_no),
    KEY idx_charger_port_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备端口（最小控制与计量单元）';

CREATE TABLE IF NOT EXISTS sim_config (
    id                BIGINT       NOT NULL COMMENT '固定为 1',
    enabled           TINYINT      NOT NULL DEFAULT 0 COMMENT '仿真总开关',
    interval_seconds  INT          NOT NULL DEFAULT 5 COMMENT '上报间隔秒',
    fault_probability DECIMAL(6, 4) NOT NULL DEFAULT 0.0200 COMMENT '每次上报的随机故障概率',
    updated_by        VARCHAR(64)  DEFAULT NULL,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '仿真全局配置';

INSERT INTO station (id, name, address, capacity_kw, manager, phone, status, remark, create_by)
VALUES (1, '滨江光储充电站', '杭州市滨江区江南大道 128 号', 120.00, '陈工', '13800006621', 'ACTIVE', 'M1 演示场站', 'system');

INSERT INTO charger (id, station_id, device_no, name, device_type, port_count, protocol, firmware_version,
                     rated_voltage, rated_power_kw, over_temp_c, over_current_a, is_simulated, remark, create_by)
VALUES (1, 1, 'BC-S12-001', '1号插座群', 'SOCKET_12', 12, 'BALAN_MQTT', 'v1.4.2', 220.00, 2.200, 60.00, 10.00, 1, '12 路仿真设备', 'system'),
       (2, 1, 'BC-S10-007', '2号插座群', 'SOCKET_10', 10, 'BALAN_MQTT', 'v1.4.0', 220.00, 2.200, 60.00, 10.00, 1, '10 路仿真设备', 'system'),
       (3, 1, 'BC-S02-013', '3号插座组', 'SOCKET_2', 2, 'BALAN_MQTT', 'v1.3.8', 220.00, 2.200, 60.00, 10.00, 1, '2 路仿真设备', 'system');

INSERT INTO charger_port (id, charger_id, port_no, rated_current_a, rated_power_kw, status)
VALUES (1001, 1, 1, 10.00, 2.200, 'IDLE'), (1002, 1, 2, 10.00, 2.200, 'IDLE'),
       (1003, 1, 3, 10.00, 2.200, 'IDLE'), (1004, 1, 4, 10.00, 2.200, 'IDLE'),
       (1005, 1, 5, 10.00, 2.200, 'IDLE'), (1006, 1, 6, 10.00, 2.200, 'IDLE'),
       (1007, 1, 7, 10.00, 2.200, 'IDLE'), (1008, 1, 8, 10.00, 2.200, 'IDLE'),
       (1009, 1, 9, 10.00, 2.200, 'IDLE'), (1010, 1, 10, 10.00, 2.200, 'IDLE'),
       (1011, 1, 11, 10.00, 2.200, 'IDLE'), (1012, 1, 12, 10.00, 2.200, 'IDLE'),
       (2001, 2, 1, 10.00, 2.200, 'IDLE'), (2002, 2, 2, 10.00, 2.200, 'IDLE'),
       (2003, 2, 3, 10.00, 2.200, 'IDLE'), (2004, 2, 4, 10.00, 2.200, 'IDLE'),
       (2005, 2, 5, 10.00, 2.200, 'IDLE'), (2006, 2, 6, 10.00, 2.200, 'IDLE'),
       (2007, 2, 7, 10.00, 2.200, 'IDLE'), (2008, 2, 8, 10.00, 2.200, 'IDLE'),
       (2009, 2, 9, 10.00, 2.200, 'IDLE'), (2010, 2, 10, 10.00, 2.200, 'IDLE'),
       (3001, 3, 1, 10.00, 2.200, 'IDLE'), (3002, 3, 2, 10.00, 2.200, 'IDLE');

INSERT INTO sim_config (id, enabled, interval_seconds, fault_probability, updated_by)
VALUES (1, 1, 5, 0.0200, 'system');

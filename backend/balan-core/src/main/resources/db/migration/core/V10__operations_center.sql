-- 运营后台补齐：实时充电、小程序用户、车辆、退款、字典
ALTER TABLE charge_order
    ADD COLUMN vehicle_id BIGINT DEFAULT NULL COMMENT '小程序车辆ID，NULL 表示未绑定' AFTER app_user_id,
    ADD KEY idx_order_vehicle (vehicle_id);

CREATE TABLE IF NOT EXISTS app_vehicle (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '车辆ID',
    app_user_id BIGINT      NOT NULL COMMENT '小程序用户ID',
    plate_no    VARCHAR(16) NOT NULL COMMENT '车牌号',
    brand       VARCHAR(32)  DEFAULT NULL COMMENT '品牌',
    model       VARCHAR(64)  DEFAULT NULL COMMENT '车型',
    color       VARCHAR(16)  DEFAULT NULL COMMENT '颜色',
    is_default  CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0否 1默认车辆',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_user_plate (app_user_id, plate_no),
    KEY idx_vehicle_user (app_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小程序车辆';

CREATE TABLE IF NOT EXISTS order_refund (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '退款ID',
    refund_no   VARCHAR(32)   NOT NULL COMMENT '退款单号',
    order_id    BIGINT        NOT NULL COMMENT '订单ID',
    order_no    VARCHAR(32)   NOT NULL COMMENT '订单号',
    station_id  BIGINT        NOT NULL COMMENT '场站ID',
    app_user_id BIGINT        DEFAULT NULL COMMENT '小程序用户ID',
    amount      DECIMAL(12, 4) NOT NULL COMMENT '退款金额',
    reason      VARCHAR(255)  DEFAULT NULL COMMENT '退款原因',
    status      VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT '退款状态',
    operator    VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
    refund_time DATETIME      NOT NULL COMMENT '退款时间',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order (order_id),
    KEY idx_refund_time (refund_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '模拟退款记录';

CREATE TABLE IF NOT EXISTS sys_dict_type (
    dict_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    dict_name   VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_type   VARCHAR(100) NOT NULL COMMENT '字典类型',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
    remark      VARCHAR(255) DEFAULT NULL,
    create_by   VARCHAR(64)  DEFAULT NULL,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64)  DEFAULT NULL,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_dict_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_data (
    dict_code  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    dict_sort  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典键值',
    dict_type  VARCHAR(100) NOT NULL COMMENT '字典类型',
    css_class  VARCHAR(100) DEFAULT NULL,
    list_class VARCHAR(100) DEFAULT NULL,
    is_default CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '是否默认 Y/N',
    status     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
    remark     VARCHAR(255) DEFAULT NULL,
    create_by  VARCHAR(64)  DEFAULT NULL,
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_by  VARCHAR(64)  DEFAULT NULL,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dict_code),
    KEY idx_dict_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据';

INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, remark)
VALUES ('系统开关', 'sys_normal_disable', '通用启用/停用状态'),
       ('订单状态', 'order_status', '充电订单状态'),
       ('支付状态', 'payment_status', '订单支付/退款状态'),
       ('停止原因', 'stop_reason', '订单停止原因'),
       ('计费模式', 'billing_mode', '费率计费方式'),
       ('设备类型', 'device_type', '低功率插座路数'),
       ('端口状态', 'port_status', '设备端口实时状态'),
       ('告警类型', 'alert_type', '平台告警分类'),
       ('告警级别', 'alert_level', '告警严重程度'),
       ('告警状态', 'alert_status', '告警处理状态'),
       ('小程序用户状态', 'app_user_status', '小程序账号状态'),
       ('车辆类型', 'vehicle_type', '车辆使用场景');

INSERT IGNORE INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status)
VALUES (1, '正常', '0', 'sys_normal_disable', 'success', 'Y', '0'),
       (2, '停用', '1', 'sys_normal_disable', 'danger', 'N', '0'),
       (1, '启动中', 'PENDING_START', 'order_status', 'warning', 'N', '0'),
       (2, '充电中', 'CHARGING', 'order_status', 'success', 'N', '0'),
       (3, '停止中', 'STOPPING', 'order_status', 'warning', 'N', '0'),
       (4, '已完成', 'COMPLETED', 'order_status', 'success', 'N', '0'),
       (5, '启动失败', 'FAILED', 'order_status', 'danger', 'N', '0'),
       (6, '异常结束', 'ABNORMAL', 'order_status', 'danger', 'N', '0'),
       (1, '未支付', 'UNPAID', 'payment_status', 'info', 'Y', '0'),
       (2, '已支付', 'PAID', 'payment_status', 'success', 'N', '0'),
       (3, '已退款', 'REFUNDED', 'payment_status', 'warning', 'N', '0'),
       (1, '用户停止', 'USER_STOP', 'stop_reason', 'info', 'N', '0'),
       (2, '设备停止', 'DEVICE_STOP', 'stop_reason', 'info', 'N', '0'),
       (3, '充满自停', 'FULL', 'stop_reason', 'success', 'N', '0'),
       (4, '达到时长上限', 'TIME_LIMIT', 'stop_reason', 'warning', 'N', '0'),
       (5, '达到金额上限', 'AMOUNT_LIMIT', 'stop_reason', 'warning', 'N', '0'),
       (6, '空载自停', 'NO_LOAD', 'stop_reason', 'info', 'N', '0'),
       (7, '设备故障', 'FAULT', 'stop_reason', 'danger', 'N', '0'),
       (8, '设备离线', 'DEVICE_OFFLINE', 'stop_reason', 'danger', 'N', '0'),
       (9, '停止超时', 'STOP_TIMEOUT', 'stop_reason', 'danger', 'N', '0'),
       (10, '启动失败', 'START_FAILED', 'stop_reason', 'danger', 'N', '0'),
       (1, '按时长', 'TIME', 'billing_mode', 'info', 'N', '0'),
       (2, '按电量', 'ENERGY', 'billing_mode', 'info', 'N', '0'),
       (3, '混合计费', 'MIXED', 'billing_mode', 'info', 'N', '0'),
       (1, '2 路插座', 'SOCKET_2', 'device_type', 'info', 'N', '0'),
       (2, '10 路插座', 'SOCKET_10', 'device_type', 'info', 'N', '0'),
       (3, '12 路插座', 'SOCKET_12', 'device_type', 'info', 'N', '0'),
       (1, '空闲', 'IDLE', 'port_status', 'success', 'N', '0'),
       (2, '充电中', 'CHARGING', 'port_status', 'success', 'N', '0'),
       (3, '故障', 'FAULT', 'port_status', 'danger', 'N', '0'),
       (4, '离线', 'OFFLINE', 'port_status', 'info', 'N', '0'),
       (1, '过温', 'OVER_TEMP', 'alert_type', 'danger', 'N', '0'),
       (2, '过流', 'OVER_CURRENT', 'alert_type', 'danger', 'N', '0'),
       (3, '过压', 'OVER_VOLTAGE', 'alert_type', 'warning', 'N', '0'),
       (4, '欠压', 'UNDER_VOLTAGE', 'alert_type', 'warning', 'N', '0'),
       (5, '过载', 'OVERLOAD', 'alert_type', 'warning', 'N', '0'),
       (6, '设备离线', 'OFFLINE', 'alert_type', 'danger', 'N', '0'),
       (7, '端口故障', 'PORT_FAULT', 'alert_type', 'danger', 'N', '0'),
       (8, '状态不一致', 'STATE_MISMATCH', 'alert_type', 'warning', 'N', '0'),
       (1, '紧急', 'CRITICAL', 'alert_level', 'danger', 'N', '0'),
       (2, '重要', 'MAJOR', 'alert_level', 'warning', 'N', '0'),
       (3, '提示', 'INFO', 'alert_level', 'info', 'N', '0'),
       (1, '未处理', 'OPEN', 'alert_status', 'danger', 'N', '0'),
       (2, '已确认', 'ACKNOWLEDGED', 'alert_status', 'warning', 'N', '0'),
       (3, '已恢复', 'RESOLVED', 'alert_status', 'success', 'N', '0'),
       (1, '正常', '0', 'app_user_status', 'success', 'Y', '0'),
       (2, '停用', '1', 'app_user_status', 'danger', 'N', '0'),
       (1, '电动汽车', 'EV', 'vehicle_type', 'info', 'Y', '0'),
       (2, '电动自行车', 'EBIKE', 'vehicle_type', 'info', 'N', '0'),
       (3, '其他', 'OTHER', 'vehicle_type', 'info', 'N', '0');

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (130, '实时充电', 1, 4, 'charging', 'balan/order/active', NULL, '1', '0', 'C', '0', '0', 'order:list', 'eye-open'),
       (140, '小程序用户', 1, 5, 'app-user', 'balan/appuser/index', NULL, '1', '0', 'C', '0', '0', 'appuser:list', 'user'),
       (141, '用户查询', 140, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'appuser:query', '#'),
       (142, '用户状态', 140, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'appuser:edit', '#'),
       (150, '车辆管理', 1, 6, 'vehicle', 'balan/vehicle/index', NULL, '1', '0', 'C', '0', '0', 'vehicle:list', 'guide'),
       (151, '车辆查询', 150, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'vehicle:query', '#'),
       (152, '车辆新增', 150, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'vehicle:add', '#'),
       (153, '车辆修改', 150, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'vehicle:edit', '#'),
       (154, '车辆删除', 150, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'vehicle:remove', '#'),
       (204, '订单退款', 200, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'order:refund', '#'),
       (210, '退款记录', 2, 2, 'refund', 'balan/order/refund', NULL, '1', '0', 'C', '0', '0', 'order:list', 'money'),
       (530, '字典管理', 5, 4, 'dict', 'balan/system/dict/index', NULL, '1', '0', 'C', '0', '0', 'system:dict:list', 'dict'),
       (531, '字典查询', 530, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:dict:query', '#'),
       (532, '字典新增', 530, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:dict:add', '#'),
       (533, '字典修改', 530, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:dict:edit', '#'),
       (534, '字典删除', 530, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:dict:remove', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id IN (130, 140, 141, 142, 150, 151, 152, 153, 154, 204, 210, 530, 531, 532, 533, 534);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (2, 130), (2, 140), (2, 141), (2, 142), (2, 150), (2, 151), (2, 204), (2, 210),
       (4, 130), (4, 140), (4, 141), (4, 150), (4, 151), (4, 210),
       (3, 130);

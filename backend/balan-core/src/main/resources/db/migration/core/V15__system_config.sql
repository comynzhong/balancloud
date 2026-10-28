-- A2 系统配置（全局平台级）：运行期可调的业务开关，不承接密钥等部署级参数。
-- 隔离口径：本表不参与租户过滤，配置项对所有租户生效；租户级覆盖留给 T1 的 sys_tenant_config。
CREATE TABLE IF NOT EXISTS sys_config (
    config_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    config_key   VARCHAR(100) NOT NULL COMMENT '参数键名',
    config_name  VARCHAR(100) NOT NULL COMMENT '参数名称',
    config_value VARCHAR(500) NOT NULL COMMENT '参数值',
    value_type   VARCHAR(20)  NOT NULL DEFAULT 'STRING' COMMENT 'BOOLEAN/INT/DECIMAL/STRING/ENUM',
    config_group VARCHAR(30)  NOT NULL COMMENT '分组：platform/payment/security/miniapp/operations/brand',
    options_json VARCHAR(1000) DEFAULT NULL COMMENT '枚举选项 JSON，disabled=true 表示当前不可选',
    min_value    DECIMAL(18, 4) DEFAULT NULL COMMENT '数值下限',
    max_value    DECIMAL(18, 4) DEFAULT NULL COMMENT '数值上限',
    max_length   INT          DEFAULT NULL COMMENT '字符串最大长度',
    editable     CHAR(1)      NOT NULL DEFAULT '1' COMMENT '是否允许后台修改：1是 0否',
    is_system    CHAR(1)      NOT NULL DEFAULT '1' COMMENT '是否内置参数：1是 0否',
    status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0正常 1停用',
    remark       VARCHAR(500) DEFAULT NULL COMMENT '说明',
    create_by    VARCHAR(64)  DEFAULT '',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(64)  DEFAULT '',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统配置（全局平台级）';

INSERT IGNORE INTO sys_config
(config_key, config_name, config_value, value_type, config_group, options_json, min_value, max_value, max_length, remark)
VALUES
('tenant.multiTenant.enabled', '多租户模式', 'false', 'BOOLEAN', 'platform', NULL, NULL, NULL, NULL,
 '仅控制平台入口与品牌展示；数据隔离始终开启，租户编码登录随 T1 接入'),
('payment.mode', '支付模式', 'mock', 'ENUM', 'payment',
 '[{"label":"模拟支付","value":"mock"},{"label":"微信支付（未接入）","value":"wechat","disabled":true}]',
 NULL, NULL, NULL, '真实支付渠道未接入前只允许模拟支付'),
('security.captcha.enabled', '登录滑块验证码', 'true', 'BOOLEAN', 'security', NULL, NULL, NULL, NULL,
 '关闭后登录页隐藏验证行；仅建议内网演示环境关闭'),
('miniapp.defaultMaxDurationMinutes', '小程序单笔时长上限（分钟）', '480', 'INT', 'miniapp', NULL, 1, 1440, NULL,
 '用户下单时可申请的充电时长上限，只能调低不能超过该值'),
('miniapp.defaultChargeLimitAmount', '小程序单笔金额上限（元）', '50', 'DECIMAL', 'miniapp', NULL, 0.01, 10000, NULL,
 '用户下单时可申请的金额上限'),
('miniapp.maxActiveOrdersPerUser', '单用户并行订单数', '1', 'INT', 'miniapp', NULL, 1, 5, NULL,
 '同一小程序用户允许同时进行的充电订单数'),
('operations.simulator.enabled', '仿真器开关', 'true', 'BOOLEAN', 'operations', NULL, NULL, NULL, NULL,
 '关闭后不能启动/扩容/注入故障，仍可在仿真页停止正在运行的实例'),
('operations.refund.enabled', '退款开关', 'true', 'BOOLEAN', 'operations', NULL, NULL, NULL, NULL,
 '关闭后运营后台的订单退款接口直接拒绝，已有退款记录不受影响'),
('operations.maintenance.enabled', '维护模式', 'false', 'BOOLEAN', 'operations', NULL, NULL, NULL, NULL,
 '开启后小程序不能发起新充电，登录页与小程序展示维护公告；后台不受影响'),
('operations.maintenance.message', '维护公告文案', '系统维护中，暂停新充电业务，请稍后再试。', 'STRING', 'operations', NULL, NULL, NULL, 200,
 '维护模式开启时展示给用户'),
('brand.platformName', '平台名称', '衡电云控', 'STRING', 'brand', NULL, NULL, NULL, 30,
 '后台登录页、侧边栏与小程序展示的平台名称'),
('brand.platformSubtitle', '平台副标题', '充电桩场站智能运营平台', 'STRING', 'brand', NULL, NULL, NULL, 60,
 '后台登录页品牌区副标题'),
('brand.servicePhone', '客服电话', '400-000-0000', 'STRING', 'brand', NULL, NULL, NULL, 30,
 '小程序「联系客服」拨打的号码');

-- 系统管理 → 系统配置
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (560, '系统配置', 5, 6, 'config', 'balan/system/config/index', NULL, '1', '0', 'C', '0', '0',
        'system:config:list', 'edit'),
       (561, '配置查询', 560, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:config:query', '#'),
       (562, '配置修改', 560, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:config:edit', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (560, 561, 562);

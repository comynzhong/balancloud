-- A7 系统配置动态化：分组注册表 + 配置项排序 + 扩展项管理权限。
-- 口径：分组由 Flyway/标准版 jar 注册，界面不增删分组；内置配置项（is_system='1'）只改值，
-- 扩展项（is_system='0'，键以 ext. 开头）允许界面增删改，二者共用同一套类型与校验。
CREATE TABLE IF NOT EXISTS sys_config_group (
    group_key   VARCHAR(30)  NOT NULL COMMENT '分组键',
    group_name  VARCHAR(50)  NOT NULL COMMENT '分组名称',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    builtin     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否版本预置：1是 0否',
    remark      VARCHAR(200) DEFAULT NULL COMMENT '说明',
    create_by   VARCHAR(64)  DEFAULT '',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64)  DEFAULT '',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (group_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统配置分组';

INSERT IGNORE INTO sys_config_group (group_key, group_name, sort, builtin, remark)
VALUES ('platform', '平台能力', 1, '1', '多租户等平台级开关'),
       ('payment', '支付', 2, '1', '支付通道与模式'),
       ('security', '登录安全', 3, '1', '登录验证相关'),
       ('miniapp', '小程序充电', 4, '1', '用户下单限额'),
       ('operations', '运营开关', 5, '1', '仿真、退款、维护'),
       ('brand', '品牌信息', 6, '1', '登录页与小程序品牌');

ALTER TABLE sys_config
    ADD COLUMN sort INT NOT NULL DEFAULT 0 COMMENT '同组内显示顺序' AFTER config_group;

-- 内置项沿用登记顺序；后续扩展项由接口追加到组尾。
UPDATE sys_config SET sort = config_id;

-- 系统管理 → 系统配置 → 扩展项管理
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (563, '扩展配置项管理', 560, 3, '', NULL, NULL, '1', '0', 'F', '0', '0',
        'system:config:manage', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id = 563;

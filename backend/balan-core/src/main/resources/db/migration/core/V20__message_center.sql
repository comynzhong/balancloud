-- A6 站内消息中心：消息主体 + 按用户已读回执，告警与订单事件落库
CREATE TABLE IF NOT EXISTS sys_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    msg_type    VARCHAR(20)  NOT NULL COMMENT 'ALERT / ORDER / SYSTEM',
    severity    VARCHAR(20)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO / MAJOR / CRITICAL',
    station_id  BIGINT       DEFAULT NULL COMMENT '关联场站，空表示租户级消息',
    title       VARCHAR(120) NOT NULL COMMENT '标题',
    content     VARCHAR(500) DEFAULT NULL COMMENT '正文',
    biz_type    VARCHAR(30)  DEFAULT NULL COMMENT '业务类型 ALERT / ORDER',
    biz_id      VARCHAR(64)  DEFAULT NULL COMMENT '业务主键，用于跳转',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_message_tenant_time (tenant_id, create_time),
    KEY idx_message_biz (tenant_id, biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息';

CREATE TABLE IF NOT EXISTS sys_message_read (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '回执ID',
    tenant_id  BIGINT   NOT NULL DEFAULT 1 COMMENT '租户ID',
    message_id BIGINT   NOT NULL COMMENT '消息ID',
    user_id    BIGINT   NOT NULL COMMENT '用户ID',
    read_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_read (tenant_id, message_id, user_id),
    KEY idx_message_read_user (tenant_id, user_id, message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息已读回执';

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (570, '消息中心', 5, 7, 'message', 'balan/system/message/index', NULL, '1', '0', 'C', '0', '0',
        'system:message:list', 'message');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 570
FROM sys_role r
WHERE r.role_key IN ('admin', 'operator', 'maintainer', 'viewer');

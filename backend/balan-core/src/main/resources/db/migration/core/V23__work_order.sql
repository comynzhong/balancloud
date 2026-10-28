-- 批次 B5：运维工单。告警一键生成、指派、处理记录、闭环归档。

CREATE TABLE IF NOT EXISTS work_order (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    tenant_id      BIGINT        NOT NULL DEFAULT 1 COMMENT '租户ID',
    work_order_no  VARCHAR(32)   NOT NULL COMMENT '工单号',
    title          VARCHAR(120)  NOT NULL COMMENT '标题',
    description    VARCHAR(1000) DEFAULT NULL COMMENT '问题描述',
    source         VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源：ALERT / MANUAL',
    alert_id       BIGINT        DEFAULT NULL COMMENT '关联告警ID',
    station_id     BIGINT        NOT NULL COMMENT '场站ID',
    charger_id     BIGINT        DEFAULT NULL COMMENT '设备ID',
    device_no      VARCHAR(64)   DEFAULT NULL COMMENT '设备编号（冗余，便于列表展示）',
    port_no        INT           DEFAULT NULL COMMENT '端口号',
    priority       VARCHAR(20)   NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：LOW / NORMAL / HIGH / URGENT',
    status         VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN / PROCESSING / RESOLVED / CLOSED',
    assignee_id    BIGINT        DEFAULT NULL COMMENT '处理人ID',
    assignee_name  VARCHAR(64)   DEFAULT NULL COMMENT '处理人昵称',
    creator        VARCHAR(64)   NOT NULL COMMENT '创建人',
    resolution     VARCHAR(500)  DEFAULT NULL COMMENT '处理结论',
    closed_by      VARCHAR(64)   DEFAULT NULL COMMENT '归档人',
    resolved_at    DATETIME      DEFAULT NULL COMMENT '解决时间',
    closed_at      DATETIME      DEFAULT NULL COMMENT '归档时间',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志：0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_no (tenant_id, work_order_no),
    KEY idx_work_order_status (tenant_id, status, priority),
    KEY idx_work_order_station (tenant_id, station_id),
    KEY idx_work_order_alert (tenant_id, alert_id),
    KEY idx_work_order_assignee (tenant_id, assignee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运维工单';

CREATE TABLE IF NOT EXISTS work_order_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    tenant_id     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    work_order_id BIGINT       NOT NULL COMMENT '工单ID',
    action        VARCHAR(20)  NOT NULL COMMENT '动作：CREATE / ASSIGN / RECORD / RESOLVE / REOPEN / CLOSE / EDIT',
    content       VARCHAR(500) NOT NULL COMMENT '记录内容',
    operator      VARCHAR(64)  NOT NULL COMMENT '操作人',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_work_order_record (tenant_id, work_order_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运维工单处理记录';

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (310, '运维工单', 3, 2, 'work-order', 'balan/work-order/index', NULL, '1', '0', 'C', '0', '0',
        'workorder:list', 'documentation'),
       (311, '工单查询', 310, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:query', '#'),
       (312, '工单新增', 310, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:add', '#'),
       (313, '工单指派', 310, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:assign', '#'),
       (314, '工单处理', 310, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:process', '#'),
       (315, '工单归档', 310, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:close', '#'),
       (316, '工单删除', 310, 6, '', NULL, NULL, '1', '0', 'F', '0', '0', 'workorder:remove', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         JOIN sys_menu m ON m.menu_id IN (310, 311, 312, 313, 314, 315)
WHERE r.role_key IN ('admin', 'operator', 'maintainer');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 310
FROM sys_role r
WHERE r.role_key = 'viewer';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 311
FROM sys_role r
WHERE r.role_key = 'viewer';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 316
FROM sys_role r
WHERE r.role_key = 'admin';

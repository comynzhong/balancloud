-- 批次 A5：异常订单人工处理（强制结束 / 金额修正），保留人工处理审计记录。

CREATE TABLE IF NOT EXISTS order_manual_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    tenant_id      BIGINT        NOT NULL DEFAULT 1 COMMENT '租户ID',
    order_id       BIGINT        NOT NULL COMMENT '订单ID',
    order_no       VARCHAR(32)   NOT NULL COMMENT '订单号',
    action_type    VARCHAR(32)   NOT NULL COMMENT '动作：FORCE_FINISH / CORRECT_AMOUNT',
    reason         VARCHAR(200)  NOT NULL COMMENT '人工处理原因',
    before_status  VARCHAR(20)   DEFAULT NULL COMMENT '处理前订单状态',
    after_status   VARCHAR(20)   DEFAULT NULL COMMENT '处理后订单状态',
    before_amount  DECIMAL(18,4) DEFAULT NULL COMMENT '处理前订单金额',
    after_amount   DECIMAL(18,4) DEFAULT NULL COMMENT '处理后订单金额',
    operator       VARCHAR(64)   NOT NULL COMMENT '操作人',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_manual_order (tenant_id, order_id),
    KEY idx_order_manual_no (tenant_id, order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单人工处理记录';

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (205, '订单强制结束', 200, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'order:forceFinish', '#'),
       (206, '订单金额修正', 200, 6, '', NULL, NULL, '1', '0', 'F', '0', '0', 'order:correctAmount', '#');

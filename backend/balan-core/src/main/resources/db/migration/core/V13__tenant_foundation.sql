-- 租户地基 T0：门牌号打进数据层，不改变任何现有行为。
-- 单租户部署 = sys_tenant 里只有 id=1 这一条记录，全部存量数据归属租户 1。

CREATE TABLE IF NOT EXISTS sys_tenant (
    tenant_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    tenant_name    VARCHAR(50)  NOT NULL COMMENT '租户名称',
    tenant_code    VARCHAR(50)  NOT NULL COMMENT '租户编码',
    contact_person VARCHAR(30)  DEFAULT '' COMMENT '联系人',
    contact_phone  VARCHAR(20)  DEFAULT '' COMMENT '联系电话',
    status         CHAR(1)      DEFAULT '0' COMMENT '状态 0正常 1停用',
    del_flag       CHAR(1)      DEFAULT '0' COMMENT '删除标志 0存在 2删除',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark         VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (tenant_id),
    UNIQUE KEY uk_sys_tenant_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户（运营商主体）';

INSERT IGNORE INTO sys_tenant (tenant_id, tenant_name, tenant_code, contact_person, contact_phone, remark)
VALUES (1, '衡电云控', 'DEFAULT', '管理员', '', '内置默认租户，单租户部署下所有数据归属此租户');

ALTER TABLE sys_user         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_role         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_user_role    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_role_menu    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_user_station ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_oper_log     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_logininfor   ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE station          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE charger          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE charger_port     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sim_config       ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE rate_strategy    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE rate_period      ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE charge_order     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE order_rate_detail ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE device_command   ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE alert_record     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE app_user         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE app_vehicle      ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE order_refund     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_dict_type    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE sys_dict_data    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';

-- 全局唯一改成租户内唯一：否则第二个商家无法创建同名账号/设备编号/字典类型。
ALTER TABLE sys_user      DROP INDEX uk_sys_user_user_name,
                          ADD UNIQUE KEY uk_sys_user_tenant_user_name (tenant_id, user_name);
ALTER TABLE sys_role      DROP INDEX uk_sys_role_role_key,
                          ADD UNIQUE KEY uk_sys_role_tenant_role_key (tenant_id, role_key);
ALTER TABLE charger       DROP INDEX uk_charger_device_no,
                          ADD UNIQUE KEY uk_charger_tenant_device_no (tenant_id, device_no);
ALTER TABLE charge_order  DROP INDEX uk_order_no,
                          ADD UNIQUE KEY uk_order_tenant_order_no (tenant_id, order_no);
ALTER TABLE app_user      DROP INDEX uk_app_user_openid,
                          ADD UNIQUE KEY uk_app_user_tenant_openid (tenant_id, openid),
                          DROP INDEX uk_app_user_phone,
                          ADD UNIQUE KEY uk_app_user_tenant_phone (tenant_id, phone);
ALTER TABLE sys_dict_type DROP INDEX uk_dict_type,
                          ADD UNIQUE KEY uk_dict_tenant_type (tenant_id, dict_type);

-- A4 设备分组：设备增加分组字段，分组候选值进字典
ALTER TABLE charger
    ADD COLUMN group_name VARCHAR(64) NULL COMMENT '设备分组' AFTER name;

ALTER TABLE charger
    ADD KEY idx_charger_group (tenant_id, group_name);

INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, remark)
VALUES ('设备分组', 'device_group', '设备列表分组筛选与表单候选值');

INSERT IGNORE INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class)
VALUES (1, 'A 区', 'A 区', 'device_group', 'primary'),
       (2, 'B 区', 'B 区', 'device_group', 'success'),
       (3, 'C 区', 'C 区', 'device_group', 'info');

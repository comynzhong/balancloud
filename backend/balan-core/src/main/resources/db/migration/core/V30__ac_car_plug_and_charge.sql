-- §49 P-3 四轮交流桩：启动方式与 VIN。
-- 启动方式落在设备上（同一现场可能混装扫码桩与插枪即充桩），订单落快照便于事后追溯。
ALTER TABLE charger
    ADD COLUMN start_method VARCHAR(20) NOT NULL DEFAULT 'SCAN' COMMENT 'SCAN扫码 PLUG_AND_CHARGE插枪即充' AFTER scene;

ALTER TABLE charge_order
    ADD COLUMN start_method VARCHAR(20) NOT NULL DEFAULT 'SCAN' COMMENT '下单时的启动方式' AFTER payment_status,
    ADD COLUMN vin          VARCHAR(17) DEFAULT NULL COMMENT '即插即充匹配到的VIN' AFTER start_method;

-- VIN 为空的行不参与唯一约束（MySQL 唯一键允许多个 NULL）；填了就必须租户内唯一，
-- 这是即插即充按 idTag 反查车主的唯一依据。
ALTER TABLE app_vehicle
    ADD COLUMN vin VARCHAR(17) DEFAULT NULL COMMENT '车辆识别代号，即插即充用' AFTER plate_no,
    ADD UNIQUE KEY uk_vehicle_tenant_vin (tenant_id, vin);

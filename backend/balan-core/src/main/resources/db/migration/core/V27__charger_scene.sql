-- 场景维度落在设备级：一个场站混装两轮插座与交流/直流车桩是常态，所以不能放场站级。
-- 与 device_type 正交：device_type 是端口数工厂（同时被 OTA 当型号），
-- scene 描述业务时序（要不要插枪/握手、结束后算不算占位），业务代码不得把两者混用。
ALTER TABLE charger
    ADD COLUMN scene VARCHAR(20) NOT NULL DEFAULT 'TWOWHEEL'
        COMMENT 'TWOWHEEL / AC_CAR / DC_CAR / HEAVY，业务时序，与 device_type 正交'
        AFTER protocol;

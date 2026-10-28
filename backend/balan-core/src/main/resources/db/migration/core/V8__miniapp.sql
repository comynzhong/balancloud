CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '小程序用户ID',
    openid        VARCHAR(64)  DEFAULT NULL COMMENT '微信 openid',
    unionid       VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    status        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
    last_login_at DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_openid (openid),
    UNIQUE KEY uk_app_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小程序用户';

ALTER TABLE charge_order
    ADD COLUMN app_user_id BIGINT DEFAULT NULL COMMENT '小程序用户ID，NULL 表示后台发起' AFTER port_no,
    ADD KEY idx_order_app_user (app_user_id, status);

ALTER TABLE station
    ADD COLUMN latitude  DECIMAL(10, 7) DEFAULT NULL COMMENT '纬度' AFTER address,
    ADD COLUMN longitude DECIMAL(10, 7) DEFAULT NULL COMMENT '经度' AFTER latitude;

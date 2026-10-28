-- B6 首次登录强制改密：记录密码最后修改时间与待改密标记
ALTER TABLE sys_user
    ADD COLUMN pwd_update_date DATETIME NULL COMMENT '密码最近修改时间' AFTER login_date,
    ADD COLUMN pwd_reset_required CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否需修改密码（1 初始密码或管理员重置后）' AFTER pwd_update_date;

UPDATE sys_user
SET pwd_update_date = NOW()
WHERE pwd_update_date IS NULL;

CREATE TABLE IF NOT EXISTS sys_user (
    user_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    user_name    VARCHAR(30)  NOT NULL COMMENT '登录账号',
    nick_name    VARCHAR(30)  NOT NULL COMMENT '用户昵称',
    password     VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    email        VARCHAR(50)  DEFAULT '' COMMENT '邮箱',
    phone_number VARCHAR(20)  DEFAULT '' COMMENT '手机号',
    avatar       VARCHAR(255) DEFAULT '' COMMENT '头像',
    status       CHAR(1)      DEFAULT '0' COMMENT '状态 0正常 1停用',
    del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志 0存在 2删除',
    login_ip     VARCHAR(128) DEFAULT '' COMMENT '最后登录IP',
    login_date   DATETIME     NULL COMMENT '最后登录时间',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark       VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_user_name (user_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台用户';

CREATE TABLE IF NOT EXISTS sys_role (
    role_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name   VARCHAR(30)  NOT NULL COMMENT '角色名称',
    role_key    VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
    role_sort   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status      CHAR(1)      DEFAULT '0' COMMENT '状态 0正常 1停用',
    del_flag    CHAR(1)      DEFAULT '0' COMMENT '删除标志 0存在 2删除',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    remark      VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_sys_role_role_key (role_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE IF NOT EXISTS sys_menu (
    menu_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    menu_name   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID',
    order_num   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    path        VARCHAR(200) DEFAULT '' COMMENT '路由地址',
    component   VARCHAR(255) DEFAULT NULL COMMENT '前端组件',
    query       VARCHAR(255) DEFAULT NULL COMMENT '路由参数',
    is_frame    CHAR(1)      DEFAULT '1' COMMENT '是否外链 0是 1否',
    is_cache    CHAR(1)      DEFAULT '0' COMMENT '是否缓存 0否 1是',
    menu_type   CHAR(1)      DEFAULT '' COMMENT '类型 M目录 C菜单 F按钮',
    visible     CHAR(1)      DEFAULT '0' COMMENT '显示状态 0显示 1隐藏',
    status      CHAR(1)      DEFAULT '0' COMMENT '状态 0正常 1停用',
    perms       VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    icon        VARCHAR(100) DEFAULT '#' COMMENT '菜单图标',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户与角色关联';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色与菜单关联';

CREATE TABLE IF NOT EXISTS sys_user_station (
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    station_id BIGINT NOT NULL COMMENT '场站ID',
    PRIMARY KEY (user_id, station_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户场站数据权限';

CREATE TABLE IF NOT EXISTS sys_oper_log (
    oper_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    title          VARCHAR(50)  DEFAULT '' COMMENT '模块标题',
    business_type  INT          DEFAULT 0 COMMENT '业务类型',
    method         VARCHAR(200) DEFAULT '' COMMENT '方法名称',
    request_method VARCHAR(10)  DEFAULT '' COMMENT '请求方式',
    oper_name      VARCHAR(50)  DEFAULT '' COMMENT '操作人',
    oper_url       VARCHAR(255) DEFAULT '' COMMENT '请求URL',
    oper_ip        VARCHAR(128) DEFAULT '' COMMENT '操作IP',
    oper_param     VARCHAR(2000) DEFAULT '' COMMENT '请求参数',
    json_result    VARCHAR(2000) DEFAULT '' COMMENT '返回参数',
    status         INT          DEFAULT 0 COMMENT '0成功 1失败',
    error_msg      VARCHAR(2000) DEFAULT '' COMMENT '错误信息',
    oper_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    cost_time      BIGINT       DEFAULT 0 COMMENT '耗时毫秒',
    PRIMARY KEY (oper_id),
    KEY idx_sys_oper_log_oper_time (oper_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志';

CREATE TABLE IF NOT EXISTS sys_logininfor (
    info_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    user_name      VARCHAR(50)  DEFAULT '' COMMENT '登录账号',
    ipaddr         VARCHAR(128) DEFAULT '' COMMENT '登录IP',
    login_location VARCHAR(255) DEFAULT '' COMMENT '登录地点',
    browser        VARCHAR(50)  DEFAULT '' COMMENT '浏览器',
    os             VARCHAR(50)  DEFAULT '' COMMENT '操作系统',
    status         CHAR(1)      DEFAULT '0' COMMENT '0成功 1失败',
    msg            VARCHAR(255) DEFAULT '' COMMENT '提示信息',
    login_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (info_id),
    KEY idx_sys_logininfor_login_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录审计日志';

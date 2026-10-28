-- C1 非功能加固：接口限流与敏感信息脱敏的运行期开关。
-- 键全局唯一、不参与租户隔离；关闭脱敏仅影响展示与导出，不改变库内数据。
INSERT IGNORE INTO sys_config
(config_key, config_name, config_value, value_type, config_group, options_json, min_value, max_value, max_length,
 editable, is_system, sort, remark)
VALUES
('security.rateLimit.enabled', '接口限流', 'true', 'BOOLEAN', 'security', NULL, NULL, NULL, NULL,
 '1', '1', 101, '按客户端 IP 的固定窗口限流；关闭后所有接口不限流'),
('security.rateLimit.windowSeconds', '限流窗口（秒）', '60', 'INT', 'security', NULL, 1, 3600, NULL,
 '1', '1', 102, '统计窗口长度，窗口结束后计数自动清零'),
('security.rateLimit.maxRequests', '普通接口窗口上限', '600', 'INT', 'security', NULL, 1, 100000, NULL,
 '1', '1', 103, '同一 IP 在一个窗口内允许的普通接口请求数'),
('security.rateLimit.loginMaxRequests', '登录接口窗口上限', '20', 'INT', 'security', NULL, 1, 10000, NULL,
 '1', '1', 104, '同一 IP 在一个窗口内允许的登录请求数，用于弱口令爆破防护'),
('security.rateLimit.trustForwarded', '信任代理转发头', 'false', 'BOOLEAN', 'security', NULL, NULL, NULL, NULL,
 '1', '1', 105, '开启后按 X-Forwarded-For 首个地址识别客户端；仅在可信反向代理后开启'),
('security.mask.enabled', '列表敏感信息脱敏', 'true', 'BOOLEAN', 'security', NULL, NULL, NULL, NULL,
 '1', '1', 106, '手机号与车牌在后台列表/详情中脱敏展示；关闭后展示原文'),
('security.mask.export', '导出文件脱敏', 'true', 'BOOLEAN', 'security', NULL, NULL, NULL, NULL,
 '1', '1', 107, '导出 Excel 时对手机号与车牌脱敏；关闭后导出原文，仅建议授权场景关闭');

-- §47 支付配置骨架：支付总开关 + 非密钥商户参数入库。
-- 口径：密钥、证书与私钥继续走 gitignored 环境变量，本表只存展示级参数（与 V15 建表注释一致）。
INSERT IGNORE INTO sys_config
(config_key, config_name, config_value, value_type, config_group, sort, options_json, min_value, max_value, max_length, remark)
VALUES
('payment.enabled', '支付总开关', 'true', 'BOOLEAN', 'payment', 20, NULL, NULL, NULL, NULL,
 '关闭后小程序订单支付与钱包充值一并停用；退款不受影响'),
('payment.wechat.appId', '微信支付 AppID', 'wx345da33be78d5fc0', 'STRING', 'payment', 21, NULL, NULL, NULL, 64,
 'JSAPI 支付使用的小程序 AppID，必须与小程序 manifest 一致'),
('payment.wechat.mchId', '微信支付商户号', '', 'STRING', 'payment', 22, NULL, NULL, NULL, 32,
 '真实通道接入后使用；为空表示尚未进件'),
('payment.wechat.serialNo', '微信支付证书序列号', '', 'STRING', 'payment', 23, NULL, NULL, NULL, 64,
 '商户 API 证书序列号；证书与私钥不入库，走环境变量'),
('payment.wechat.notifyUrl', '微信支付回调地址', '', 'STRING', 'payment', 24, NULL, NULL, NULL, 200,
 '公网 HTTPS 地址，真实通道接入后使用');

-- 微信通道仍未接入，选项继续锁定；密钥类参数不通过后台下发。
UPDATE sys_config
SET options_json = '[{"label":"模拟支付","value":"mock"},{"label":"微信支付（待接入）","value":"wechat","disabled":true}]',
    remark = '真实支付通道尚未接入；密钥与证书走环境变量，接入前保持 mock',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'payment.mode';

-- 衡电云控 BALANCLOUD — 演示数据重置脚本
--
-- 仅用于本地/演示环境：清空运行期数据（订单、分时段明细、退款记录、车辆绑定、下行指令、告警、操作与登录审计），
-- 并把设备与端口复位成「离线 / 空闲」，让下一次演示从干净状态开始。
--
-- 不在清理范围内的种子数据：场站、设备、端口定义、费率策略、账号、角色、菜单、权限与字典；
-- 小程序用户档案（app_user）也保留，仅清掉其车辆与订单。
-- 请通过 deploy/scripts/reset-demo-data.sh 执行（该脚本会顺带清理 TDengine 测点与 Redis 缓存）。

SET NAMES utf8mb4;

START TRANSACTION;

DELETE FROM order_rate_detail;
DELETE FROM order_refund;
DELETE FROM charge_order;
DELETE FROM app_vehicle;
DELETE FROM device_command;
DELETE FROM alert_record;
DELETE FROM sys_oper_log;
DELETE FROM sys_logininfor;

UPDATE charger_port
SET status = 'IDLE',
    current_order_no = NULL,
    last_metric_at = NULL;

UPDATE charger
SET online_status = 'OFFLINE',
    last_online_at = NULL,
    last_metric_at = NULL;

COMMIT;

SELECT 'charge_order' AS table_name, COUNT(*) AS remaining FROM charge_order
UNION ALL SELECT 'alert_record', COUNT(*) FROM alert_record
UNION ALL SELECT 'device_command', COUNT(*) FROM device_command
UNION ALL SELECT 'sys_oper_log', COUNT(*) FROM sys_oper_log
UNION ALL SELECT 'charger_port_charging', COUNT(*) FROM charger_port WHERE status <> 'IDLE';

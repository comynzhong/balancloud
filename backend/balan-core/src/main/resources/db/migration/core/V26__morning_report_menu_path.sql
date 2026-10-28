-- 菜单 170 的 path 由 report 改成 morning-report。
-- 前端路由名是按 path 首字母大写生成的，标准版里平台菜单 961（/insight/report）生成的名字同样是 Report，
-- Vue Router 遇到同名路由会整条替换，于是晨报页在标准版被顶成 404；社区版裁掉了平台路由，所以只有标准版暴露。
UPDATE sys_menu SET path = 'morning-report' WHERE menu_id = 170;

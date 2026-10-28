-- 菜单「实时充电」升级为「充电实况」：与首页 5 秒刷新联动，增加实时功率曲线与端口矩阵
UPDATE sys_menu
SET menu_name = '充电实况',
    icon      = 'monitor',
    perms     = 'order:list'
WHERE menu_id = 130;

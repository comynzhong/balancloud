# BALANCLOUD 前端

衡电云控（BALANCLOUD）运营后台的 Vue3 前端，与仓库根目录的 `backend/` 配套使用。

完整的项目介绍、架构、接口与演示脚本见根目录 [README.md](../README.md)。

## 技术栈

- Vue 3.4 + Vite 5 + Pinia
- Element Plus 2.4
- ECharts 5（看板与曲线）
- Axios（统一封装在 `src/utils/request.js`）

本目录基于 [RuoYi-Vue3](https://gitee.com/y_project/RuoYi-Vue) 的 MIT 授权骨架改造而来，保留 `LICENSE`；BALANCLOUD 未复制任何 AGPL/XCPL 项目的代码。详见根目录 [NOTICE](../NOTICE)。

## 本地启动

```bash
pnpm install
pnpm dev
```

- 访问地址：http://localhost:5174
- 接口前缀 `/dev-api` 由 Vite 代理到后端 `http://localhost:8080`，代理目标在 `.env.development` 的 `VITE_APP_BASE_API` 与 `vite.config.js` 中配置。
- WebSocket 路径 `/ws/events` 走同一代理，需要保持 `ws: true`。

## 构建

```bash
pnpm build:prod   # 产物在 dist/
pnpm build:stage
```

## 目录结构

```
src/
├── api/                 # 接口定义（站、设备、费率、订单、告警、看板、系统）
├── views/
│   ├── station/         # 场站管理
│   ├── device/          # 充电桩管理 + 监控抽屉
│   ├── simulator/       # 内置多端口仿真器
│   ├── rate/            # 费率策略
│   ├── order/           # 充电订单
│   ├── alert/           # 告警中心
│   ├── dashboard/       # 首页运营看板
│   └── system/          # 用户 / 角色 / 审计日志
├── store/               # Pinia
├── utils/               # request、鉴权、通用工具
└── layout/              # 布局与导航
```

## 路由与权限

菜单由后端 `/getRouters` 下发，前端不硬编码业务菜单；页面通过 `sys_menu.component` 与 `src/views/` 的相对路径对应。按钮级权限使用 `v-hasPermi` 指令，角色编码为 `admin / operator / maintainer / viewer`。

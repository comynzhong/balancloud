# 衡电云控 BALANCLOUD

![衡电云控界面：运营看板、充电实况与小程序首页，三张真实运行截图拼成](docs/hero.webp)

自己装一套就能用的充电桩运营系统：把桩接进来、按峰/平/谷算钱、每笔订单和退款都查得到、桩出故障有告警和工单；运营人员用网页后台看数据管设备，车主扫码充电、付款、看订单用微信小程序。

一个 jar 加上 MySQL / Redis / EMQX / TDengine 就能跑起来，桩多了就再开一个实例，不用改代码。没有真桩也能先跑通全流程：仓库里带了仿真器，走的是和真实设备一样的链路。

能带多少桩、哪几处还是模拟的（比如支付），都写在后面的「已知边界」里，不藏着。

- 许可：社区版 **AGPL-3.0**（本仓库），标准版闭源部分为商业许可，路径级边界见 [LICENSING.md](LICENSING.md) 与 [NOTICE](NOTICE)
- 文档：[docs/PROTOCOL-SDK.md](docs/PROTOCOL-SDK.md)（设备接入）、[CONTRIBUTING.md](CONTRIBUTING.md)
- 规格与验收记录、小程序设计稿、AI 路线规划是内部文档，不随本仓库分发；功能与接口以代码为准。
- 不是二开：后端 Spring Boot 3 一行行自己写的，前端只拿 RuoYi-Vue3 当骨架，不是拿现成充电平台改的

## 为什么选它

- **一条链跑通，不用拼四五个开源项目**：协议解析 → 设备影子与测点 → 端口级订单状态机 → 分时计费 → 告警 → 工单 → 看板与小程序，全在同一个 jar 里。
- **没有真桩也能验收**：仿真器走的是和真实设备完全一样的链路（同 topic、同适配器、同状态机、同下行指令）；OCPP 有后台内置与独立进程两个模拟桩。
- **扩展点是代码里的接口，不是文档里的承诺**：`DeviceProtocolAdapter`、`OrderFlow`、9 个 SPI 注册成 bean 就生效，`GET /api/v1/device/protocols`、`/api/v1/cluster/spi` 能把注册表直接查出来看。
- **规模到顶会自己说**：测点入库约 500 点/秒、队列 20000 条写满即丢并打 WARN、跨租户大盘单次最多聚合 200 个租户，数字都在「已知边界」里，不写「支持海量设备」。
- **闭源只加在接缝上**：标准版是 core 外面的一层 SPI 实现，社区版缺的那些接口明确返回 `code:404` 并落到「标准版功能」页，不是一个被砍掉一半、跑起来处处报错的残缺版。

## 功能全景

![社区版与标准版功能对比](docs/feature-map.png)

图里三列分别是「本仓库就有」「闭源标准版才有」「两版都还没做」。⚠ 表示代码路径已就位但没和真实外部系统联调过——这种条目一律按「没做完」算。

## 界面

### 社区版（本仓库 clone 下来就能跑到这些页）

下面 15 张后台图 + 8 张小程序图都是社区版页面，起一个 jar 就能拍到一样的。

| 登录页（滑块验证） | 运营看板 | 充电实况 |
| --- | --- | --- |
| ![登录页](docs/screenshots/login-captcha.webp) | ![看板](docs/screenshots/dashboard.png) | ![充电实况](docs/screenshots/active-charging.png) |
| 拼图滑块验证，通过后回一次性 token；口令只在本次会话里用 | 今日经营带 + 指标卡 + 场站经营 + 全站趋势 + 告警分布，5 秒自动刷新 | 全站与分端口实时功率曲线、在充 KPI、事件流、充电名单与端口矩阵 |

| 场站管理 | 充电桩与端口 | 仿真器 |
| --- | --- | --- |
| ![场站管理](docs/screenshots/station.png) | ![设备监控](docs/screenshots/charger.png) | ![仿真器](docs/screenshots/simulator.png) |
| 场站建档、经纬度与营业时间，数据权限按场站圈定 | 端口矩阵、实时测点、启动/停止/解锁/保护阈值与 TDengine 历史曲线 | 2/10/12 路端口一起跑，走的是和真桩同一条链路（同 topic、同状态机、同下行指令） |

| 充电订单 | 费率策略 | 告警与工单 |
| --- | --- | --- |
| ![订单](docs/screenshots/order.png) | ![费率](docs/screenshots/rate.png) | ![告警](docs/screenshots/alert.png) |
| 逐笔可查：电量、分时金额、退款与占位费，手机号只留掩码 | 峰/平/谷 × 按电量/时长/次数三模式，改价不影响已生成的费率快照 | 阈值判定、确认与恢复留痕，可转运维工单并记录处理人 |

| 系统配置 | 节点状态 | 运营晨报 |
| --- | --- | --- |
| ![系统配置](docs/screenshots/system-config.png) | ![节点状态](docs/screenshots/f1-node-status-community.png) | ![运营晨报](docs/screenshots/f1-morning-report-community.png) |
| 分组卡片渲染，内置项只改值、`ext.*` 扩展项可增删改，保存即热生效 | 本实例对四个中间件的独立探测 + JVM 内存线程，凭据不出现在响应里 | 每天 08:00 按租户推昨日订单/电量/营收与异常，站内信可达 |

| 小程序用户（脱敏） | 车辆管理 | 审计日志 |
| --- | --- | --- |
| ![小程序用户](docs/screenshots/c1-app-user-masked.png) | ![车辆管理](docs/screenshots/vehicle.png) | ![审计日志](docs/screenshots/system-log.png) |
| 注册、在充、消费与退款汇总；运营账号只看得到本站场范围内的用户 | 车牌与默认启动方式，配合即插即充 | 操作与登录两类日志分列，密码字段落库前已脱敏 |

### 小程序（uni-app，同一套 `/api/app/**` 接口）

| 首页 | 找桩 | 扫码 | 确认充电 |
| --- | --- | --- | --- |
| ![首页](docs/screenshots/mp-home.png) | ![找桩](docs/screenshots/mp-stations.png) | ![扫码](docs/screenshots/mp-scan.png) | ![确认充电](docs/screenshots/mp-confirm.png) |
| 进行中订单置顶 + 附近场站 | 传坐标就按距离升序，不传保持原顺序 | 解析端口码，识别不了给明确提示 | 费率与上限先看清再启动 |

| 充电中 | 订单列表 | 移动运维工作台（H5） | 运维设备卡 |
| --- | --- | --- | --- |
| ![充电中](docs/screenshots/mp-charging.png) | ![订单](docs/screenshots/mp-orders.png) | ![工作台](docs/screenshots/s50-h5-workbench.png) | ![设备卡](docs/screenshots/s50-h5-device-card.png) |
| 实时功率与已充金额，结束/拔枪都在这一页 | 状态、金额与退款进度逐笔可查 | 运维人员登录后的场站待办 | 端口测点、进行中订单、未闭环告警、远程停充 |

### 标准版（闭源，社区版 jar 里没有这些菜单）

下面每一张都是**标准版**界面：左侧多出的「平台管理 / 深度分析 / 设备 OTA / 预约与停车」等菜单、以及它们背后的 `/api/v1/platform/**` 接口，在社区版里不存在——直接访问返回 `code:404`，前端落到「标准版功能」说明页。

| 跨租户经营大盘 | 租户管理 | 套餐与限额 |
| --- | --- | --- |
| ![标准版·跨租户大盘](docs/screenshots/t1-platform-dashboard.png) | ![标准版·租户管理](docs/screenshots/t1-tenant-create.png) | ![标准版·套餐菜单授权](docs/screenshots/t1-package-menus.png) |
| 标准版：逐租户切片相加，某租户聚合失败只标红该行，不把缺数算成 0 | 标准版：建号即生成租户管理员，租户用「租户编码 + 账号」登录 | 标准版：套餐同时决定数量上限与可见菜单，建租户时按套餐生成 role |

| 商家入驻审核 | 时段热力与自定义报表 | 设备 OTA |
| --- | --- | --- |
| ![标准版·入驻审核](docs/screenshots/t6-platform-registration-list.png) | ![标准版·深度分析](docs/screenshots/t5-analytics-heatmap.png) | ![标准版·OTA 任务](docs/screenshots/t5-ota-tasks.png) |
| 标准版：登录页免登录提交申请，通过后直接建租户并生成初始密码 | 标准版：单元格可下钻到逐笔明细，对不上就是口径有问题 | 标准版：分批推进、进度由仿真回报驱动、回滚另建一条任务 |

| 集群监控 | 端口预约与停车 |
| --- | --- |
| ![标准版·集群监控](docs/screenshots/f1-cluster.png) | ![标准版·端口预约](docs/screenshots/t5-reservation.png) |
| 标准版：读 Redis 实际键值，展示谁真正持有锁（社区版只有节点状态页） | 标准版：只锁端口不锁电量，启动充电时按预约号核销 |

| 定时任务观测 | 租户收款配置 |
| --- | --- |
| ![标准版·定时任务](docs/screenshots/s54-cluster-tasks.png) | ![标准版·收款配置](docs/screenshots/t47-admin-payment-config.png) |
| 标准版：16 个扫描器各自在哪个实例上跑、最近一轮耗时与失败次数 | 标准版：按租户配收款主体，密钥只存引用与掩码；`wechat` 通道未联调前选项是锁住的 |

截图由无头 Edge + CDP 在真实运行环境采集，中间件主机名统一替换为 `middleware.local`，不含真实口令。首屏那张 hero 图是本页 `dashboard.png`、`active-charging.png`、`mp-home.png` 三张真截图加了浏览器与手机外壳拼的，截图内容本身没改。

## 社区版与标准版怎么选

| 能力块 | 社区版（AGPL-3.0，本仓库） | 标准版（闭源，商业许可） |
| --- | --- | --- |
| 设备接入与协议 | 双协议 + 适配器扩展点 + 三类场景 + 仿真器 | 同一份 core，完全一致 |
| 计量计费与订单 | 峰/平/谷 × 三模式、费率快照、占位费、异常单处理 | 同一份 core，完全一致 |
| 告警 / 工单 / 看板 / 实况 | 有 | 有，另加跨租户大盘与深度分析（热力、排名、故障预测、自定义报表） |
| 账号权限与系统配置 | RBAC、场站隔离、滑块、限流、脱敏、字典与配置热生效 | 有，另加租户级配置覆盖与按套餐裁剪授权树 |
| 多运营商 | **只有数据面**：`tenant_id` 贯穿 29 张表，没有控制台 | 租户管理、套餐与限额、入驻审核、租户编码登录 |
| 小程序 | 用户端 + `pkg-staff` 移动运维（H5 已验收，真机未验收） | 有，另加钱包、权益、预约三个页面（按 `features` 能力位隐藏） |
| 集群 | 共享订阅 + Redis 锁 + Lua 选主 + 跨实例广播 + 节点状态页 | 有，另加集群监控页（实例存活、锁归属、EMQX 状态） |
| 资金 | 无，相关接口 `code:404` | 结算单、钱包、账单、日对账、分账 ⚠ 真实微信进件与联调未做 |
| 增长运营 | 无 | 等级、积分、优惠券、推荐有礼、短信/订阅消息/邮件 ⚠ 模拟网关，未接供应商 |
| OTA / 预约 / 停车 / 开放平台 | 无 | 固件库与分批回滚、端口预约与停车计费、appKey + HMAC 签名 ⚠ 无真机升级、无道闸/地锁联动 |

**两版都还没做**（不是「标准版才有」，别按标准版的口径期待）：

- 云快充、特来电这类**原生 TCP** 与 GB/T 27930 的 L0 传输层——适配器只解决语义层，见「设备协议」
- 真实微信支付进件与联调、真实厂商桩联调、真机 OTA 验证
- `HEAVY`（大型车）场景的订单流程实现：枚举里有，`OrderFlow` 没有
- Testcontainers 集成测试；外部 MQ 与事件回放
- 对外的定价、SLA 与厂商支持承诺

**社区版的优势**：clone 就能装、能改、能自己部署对外服务（AGPL-3.0 的义务是：改了就要给通过网络使用它的人提供同样许可下的改动源码）；单运营商跑通业务的全部主流程都在里面；协议、场景、SPI 三类扩展点开放；没有真桩也能把整条链路验收完。

**社区版的劣势**：一个实例服务多家运营商时，多运营商只有数据面没有控制台，只能靠场站级数据隔离凑；没有资金、增长、OTA、预约与开放平台；没有厂商支持，升级靠自己 rebase。

**标准版的优势**：SaaS 化那套（租户 / 套餐 / 入驻 / 跨租户大盘）、资金与增长、深度分析、OTA / 预约 / 开放平台，以及商业许可下的授权与定制。

**标准版的劣势**：闭源，不在本仓库，也不接受 PR 进闭源目录；价格与 SLA 目前都还没对外承诺；它不会让社区版少一块功能，但社区版也不替它做验收。

## 特性

**设备接入**
- 两条内置协议 + 可插拔注册表：自定义 MQTT（`BALAN_MQTT`）与 **OCPP 1.6J over WebSocket**（`OCPP16J`）；厂商自己的报文格式实现一个 `DeviceProtocolAdapter` 即可接入，前提是它走 MQTT 或 WebSocket 传输（原生 TCP 需先补 L0，见「设备协议」）
- 场景化订单流程：两轮插座 `TWOWHEEL`、交流汽车 `AC_CAR`、直流快充 `DC_CAR` 各有独立实现，与端口数型号（`SOCKET_2/10/12`、`AC_SINGLE/AC_DUAL`、`DC_SINGLE/DC_DUAL`）正交
- 启动方式 `SCAN`（扫码）与 `PLUG_AND_CHARGE`（插枪即充）
- 设备影子（Redis：最新测点 + 在线标记）、90 秒无心跳判离线、TDengine 存端口测点历史
- 设备分组（`charger.group_name`）：列表按组筛选，`GET /api/v1/chargers/groups` 返回逐组设备数（空组归「未分组」）
- 内置多端口仿真器：指令驱动、可注入故障、带报文环形日志；OCPP 桩模拟器有后台内置版（`/api/v1/device/ocpp/simulators`）与独立进程版 `tools/ocpp-sim`

**计量与计费**
- 峰/平/谷时段，`TIME / ENERGY / MIXED` 三种模式，服务费独立策略与生效日期区间，启用即互斥切换
- 启动即冻结费率快照，逐段写 `order_rate_detail`；时长/金额上限自动停止、充满自停、空载自停
- 端口级订单状态机 `PENDING_START / CHARGING / STOPPING / COMPLETED / FAILED / ABNORMAL`，后端重启后续算不重复
- 结算后仍插枪进入 `OCCUPIED`，按小时单价累计占位费（宽限期、封顶可配），拔枪或新单释放
- 异常订单人工强制结束与金额修正，全部留审计与人工核实记录

**运营与告警**
- 阈值告警（过温/过流/过压/欠压/过载）与设备上报告警，60 秒去重，连续正常自动恢复，端口故障只人工闭环
- 告警一键生成运维工单，`OPEN → PROCESSING → RESOLVED → CLOSED` 闭环并联动告警状态
- 运营看板（今日营收、指标卡、场站明细、全站趋势）与充电实况（全站与分端口实时功率曲线、在充名单、事件流、端口矩阵）
- 设备分析（利用率 / 故障率 / 平均充电时长）、站内消息中心、每日 08:00 运营晨报
- 小程序用户 / 车辆 / 用户与车牌黑名单管理、退款记录、字典与菜单可视化维护、Excel 导出

**账号、权限与多运营商**
- JWT + Redis 会话、滑块拼图验证码、首次登录强制改密（428 拦截）、登录名唯一含软删、会话精确踢出
- 角色可自建，菜单 + 按钮权限树在页面上勾选保存，即时收回权限
- 场站级数据隔离（`sys_user_station`，越权 403）；29 张业务表进入租户白名单，`tenant_id` 由 MyBatis-Plus 拦截器自动注入
- 标准版另有租户管理与套餐限额、商家入驻审核、租户级配置覆盖、跨租户经营大盘（`balan-platform`）

**小程序（uni-app，H5 与微信小程序双端）**
- 用户端：找桩（带坐标时按服务端计算的直线距离排序）、扫码/手输设备号、确认充电、充电中实时快照、订单与支付、车辆绑定、退款展示
- 移动运维分包 `pkg-staff`：后台账号 + 滑块登录，设备—端口—测点—进行中订单—未闭环告警一次拿全，停充/解锁复用既有接口

**运维与集群**
- 无外部 MQ 也能多实例：EMQX 共享订阅 + Redis 报文幂等窗口 + 定时任务窗口锁（TTL 由周期派生）+ Lua 原子选主续期 + WebSocket 跨实例广播 + 配置跨实例失效广播
- 接口限流（Redis 固定窗口，429，登录独立桶）、手机号/车牌输出层脱敏、MySQL 备份脚本（gzip + sha256 + 保留期）
- 节点状态页（MySQL/Redis/EMQX/TDengine 连通性与 JVM 运行时）、`GET /api/v1/cluster/{node,spi,tasks}` 观测

## 架构

```mermaid
flowchart LR
  DEV[充电桩 / 仿真器 / OCPP 桩] -- MQTT --> G[MqttGateway<br/>共享订阅 + 幂等]
  DEV -- "OCPP 1.6J WS" --> O[OcppWebSocketHandler]
  G --> R[ProtocolAdapterRegistry<br/>RawFrame → PlatformEvent]
  O --> R
  R --> C[ChargingService<br/>OrderFlow 按 scene 分派]
  C --> B[BillingService<br/>费率快照 + 分时段]
  C --> A[AlertService<br/>阈值 + 去重]
  B --> DB[(MySQL 业务表)]
  A --> DB
  R --> TD[(TDengine 测点)]
  R --> RD[(Redis 影子/锁/幂等/采样环)]
  DB --> W[REST /api/v1 · /api/app<br/>WebSocket /ws/events]
  W --> FE[运营后台 Vue3]
  W --> MP[小程序 用户端 / 运维端]
  C -. "SPI（9 个接缝）" .-> P[balan-platform 标准版]
```

一笔订单的生命周期：启动请求 → 端口加 Redis 锁、建单并冻结费率快照 → 下行 `START`（带 `cmdId`）→ 设备 `cmd_ack` 与测点上行 → 按测点时间命中峰/平/谷逐段计费，命中上限或用户停止则下行 `STOP` → `order_event` 收敛并补最后一段电量 → 结算；仍插枪则进入占用累计 → 支付/退款。运营端与小程序共用同一套 `ChargingService`，只在权限校验与订单归属上分流，**平台是计费的唯一权威**（设备只报电压/电流/温度/功率/电表累计值，不参与计价）。

仿真器不直接调用业务方法，而是作为 MQTT 客户端收发报文，因此上层的指令、回执、影子、结算走的是与真实设备完全相同的代码路径。

**版本分层**：闭源标准版只能通过 SPI 扩展，不能改开源核心。

| SPI（`com.balancloud.spi`） | core 默认实现 | 标准版接进来的能力 |
| --- | --- | --- |
| `PaymentChannel` | 模拟支付 | 微信服务商分账 / 租户直连收款 |
| `SettlementService` | 空实现 | 结算单、分账与佣金、支付钩子 |
| `MemberBenefitService` | 空实现 | 会员折扣与优惠券进订单快照 |
| `ReservationGuard` | 全部放行 | 端口预约准入与冲突拒绝 |
| `NotificationChannel` | 站内信 | 短信、订阅消息、邮件（失败降级站内信） |
| `TenantRegistrationService` | 关闭 | 商家入驻申请与审核建租户 |
| `TenantConfigSource` | 全局配置 | 租户级配置覆盖 |
| `TenantMenuScope` | 全量菜单 | 按套餐裁剪可授权菜单 |
| `TenantPaymentProfileProvider` | 全局收款模式 | 按租户路由收款配置 |

默认实现挂在 `SpiDefaultsAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnMissingBean`）上，闭源 jar 注册同类型 bean 即完成替换，core 不为此改代码。当前生效情况自证：`GET /api/v1/cluster/spi` 返回 `edition` 与每个 SPI 的实现类全名。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.3.13 / JDK 17、Spring Security 6 + JWT、MyBatis-Plus 3.5、Flyway、springdoc OpenAPI、Apache POI |
| 存储 | MySQL 8（业务）、TDengine 3.x（端口测点，库级 `KEEP 7d`）、Redis 7（会话/影子/锁/去重/采样环） |
| 接入 | EMQX 5 + Eclipse Paho（MQTT）、Spring WebSocket（OCPP 1.6J 与事件推送） |
| 后台 | Vue 3.4（JS）+ Element Plus + Vite 5 + Pinia + ECharts 5，基于 RuoYi-Vue3 骨架 |
| 小程序 | uni-app 3（Vue3 + Vite + Pinia），产出 H5 与微信小程序 |
| 构建 | Maven（`-Pcommunity` / `-Pstandard`，profile 声明在 `balan-server/pom.xml`）、pnpm（后台）+ npm（小程序）、Docker Compose v2、GitHub Actions 两格矩阵 |

## 快速开始

依赖：JDK **17**（必须，`mvn -v` 的 Java version 也得是 17）、Maven 3.6.3+、Node 18+（后台用 pnpm，见 `frontend/pnpm-lock.yaml`）、Docker Compose v2+。中间件不必与后端同机，主机地址在 `application-local.yml` 覆盖即可。

```bash
# 1. 中间件：MySQL / Redis / EMQX / TDengine
cd deploy && cp .env.example .env      # 改掉 4 个 change_me_*（MySQL root 与应用口令、Redis、EMQX 控制台）
docker compose up -d                   # 期望 4 个 Up，tdengine-init 已 Exited(0)

# 2. 后端（社区版壳，首次启动由 Flyway 建表并灌演示数据）
cd ../backend
cp balan-server/src/main/resources/application-local.yml.example \
   balan-server/src/main/resources/application-local.yml   # 填中间件地址与密码
JAVA_HOME=/path/to/jdk-17 mvn -pl balan-server -am spring-boot:run     # :8080

# 3. 运营后台
cd ../frontend && pnpm install && pnpm dev                             # :5174

# 4. 小程序 H5（可选）
cd ../miniapp && npm install && npm run dev:h5                        # :5175
```

就绪自检（免登录）：

```bash
curl -s http://localhost:8080/api/v1/public/config | head -c 300
# → {"code":200,"data":{"brand":{...},"captchaEnabled":true,"paymentMode":"mock",
#      "edition":"community","features":{"wallet":false,"benefits":false,...}}}
curl -s http://localhost:8080/api/v1/monitor/node | head -c 100
# → {"msg":"认证失败，无法访问系统资源","code":401}
```

第二条是有意保留的行为：**业务错误的 HTTP 状态码是 200，真实码在响应体 `code` 里**（401 未登录、403 越权、404 不存在、428 需改密），只有真正的传输错误才是非 200。写脚本时断言 `body.code`，别断言 HTTP status。

登录后打开「仿真控制」确认仿真器在跑，到「充电桩管理」对一个空闲端口点「启动充电」，「充电实况」的功率曲线就会每 5 秒往前推一格。

| 账号 | 口令 | 角色 | 能做什么 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 超级管理员 | 全部菜单与写操作，含用户/角色/配置管理 |
| `operator` | `operator123` | 场站运营 | 场站、设备、费率、订单、告警、停充 |
| `maintainer` | `maintainer123` | 运维人员 | 查看场站设备、处理告警与工单、节点状态（无 `charger:stop`） |
| `viewer` | `viewer123` | 访客 | 只读，写按钮隐藏且接口返回 `code:403` |

四个账号由 Flyway V2 种子的 BCrypt 哈希创建，**仅用于开发环境**；登录页不预填口令。对外部署前请改口令、删除仿真菜单，并给 EMQX 开启账号与 ACL（默认匿名可发布，任何能连 1883 的人都可冒充设备）。生产必须替换 `JWT_SECRET`。

**配置与密钥**：可调项一律写成 `${ENV:默认}`（`DB_URL` / `DB_PASSWORD` / `REDIS_HOST` / `MQTT_BROKER_URL` / `MQTT_SHARED_GROUP` / `JWT_SECRET` / `WECHAT_APP_ID` / `BALAN_EDITION` …），本机开发也可用 gitignored 的 `application-local.yml` 覆盖。仓库不存放真实口令，`.env` 只保留 `change_me_*` 占位。运行期开关（仿真器、退款、维护模式、验证码、限流与脱敏、支付模式、小程序限额、品牌信息）在「系统管理 → 系统配置」，保存即热生效，不需要重启。

**没有微信 AppID 也能体验小程序**：把 `APP_DEV_LOGIN=true` 打开后可用任意合法手机号 + 固定验证码 `123456` 登录，H5 与微信开发者工具都能跑通全流程；真实微信登录需要 `WECHAT_APP_ID` / `WECHAT_APP_SECRET` 与企业主体资质。

## 项目结构

```
backend/
  balan-common/     共享返回体、异常、工具、framework 基类 + 9 个 SPI 接口与 DTO
  balan-core/       开源核心（AGPL-3.0）：device / mqtt / protocol / rate / order /
                    alert / workorder / dashboard / ws / staff / analytics / system / spi.defaults
  balan-platform/   标准版（闭源）：多运营商、资金与分账、增长、深度分析、OTA、预约停车、开放平台
  balan-server/     启动壳 + application.yml / application-standard.yml / application-local.yml(.example)
frontend/           Vue3 运营后台；src/views/platform/** 仅标准版构建注册路由
miniapp/            uni-app：主包 10 页 + pkg-standard 5 页 + pkg-staff 2 页
docs/               规格与验收、协议 SDK、小程序规划、界面截图
deploy/             docker-compose、.env.example、中间件初始化 SQL、备份/重置/社区版拆分脚本
tools/ocpp-sim/     独立 OCPP 桩模拟器（Python）
.github/workflows/  CI：community / standard 两格矩阵 + 前端双壳 + 小程序双构建
```

分层规则只有一条：**依赖只能向下**（`common ← core ← platform ← server`，`server` 仅在 `standard` profile 引 `platform`）。开源侧不允许出现对 `com.balancloud.platform` 的引用，CI 每格先跑 grep 断言，命中即失败。

Flyway 与菜单 ID 同样分段：core 用 `db/migration/core`（**V1–V30**，菜单 1–899），platform 用 `db/migration/platform`（**V500–V524**，菜单 900+），由 `application-standard.yml` 追加 locations；两段共用一张历史表并开 `out-of-order`。社区版 jar 不加载 platform，平台接口与平台菜单都不存在；若社区库被标准版迁移写过，启动自检 `EditionBoundaryChecker` 只告警不阻断。

前端两壳由 `VITE_EDITION` 裁剪：`pnpm build:prod` 出社区版（`views/platform/**` 连同 glob 一起被判成死代码，0 个 platform chunk），`pnpm build:standard` 出标准版。

## 数据模型

MySQL 业务表按域分组（core 段，Flyway V1–V30）：

| 域 | 表 |
| --- | --- |
| 设备 | `station`、`charger`、`charger_port`、`device_command`、`sim_config`、`ocpp_transaction` |
| 计费与订单 | `rate_strategy`、`rate_period`、`charge_order`、`order_rate_detail`、`order_refund`、`order_manual_log` |
| 告警与工单 | `alert_record`、`work_order`、`work_order_record`、`sys_message`、`sys_message_read` |
| 用户端 | `app_user`、`app_vehicle`、`app_user_blacklist` |
| 系统与权限 | `sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`、`sys_user_station`、`sys_tenant`、`sys_config`、`sys_config_group`、`sys_dict_type`、`sys_dict_data`、`sys_oper_log`、`sys_logininfor` |

主键统一 `BIGINT` 自增（避免前端 JS 精度丢失），业务表带 `del_flag` 逻辑删除。

**租户维度**：29 张业务表进入 `TenantTables` 白名单，查询与写入由 `TenantLineInnerInterceptor`（挂在分页拦截器之前）自动注入 `tenant_id`，实体类不带 `tenantId` 字段以避免双写漂移，业务代码里没有 `if (multiTenant)` 分支。`device_no`、`user_name`、`role_key`、`order_no`、`openid`、`phone`、`dict_type` 这 7 个唯一键是**租户内唯一**。`sys_menu` 有意保持全局（菜单是平台功能目录，按租户复制会让新租户侧边栏空白），`sys_config` 是全局平台级（租户级覆盖走标准版 `sys_tenant_config`）。取不到会话的定时任务与 MQTT 上行按默认租户 1 解析，而不是退化成「不过滤」。

TDengine：超级表 `balan_metrics.device_metric` 按设备建子表，TAGS 为 `station_id / charger_id / port_no / tenant_id`。看板功率曲线不查 TDengine（5 秒一条测点，按分钟 `SUM(power_kw)` 会把同一端口多次采样重复累加），改用 10 秒定时采样写入 Redis 环形列表，每场站保留 720 点。

## 主要接口

运营接口前缀 `/api/v1`，小程序前缀 `/api/app`，返回体 `{ code, msg, data }`，分页参数 `pageNum / pageSize`。登录契约与 RuoYi-Vue3 前端兼容（`/login`、`/getInfo`、`/getRouters`、`/logout`、`/captchaImage`），滑块验证为 `GET /captcha/slider` + `POST /captcha/slider/verify`（通过后回一次性 `captchaToken`）。接口清单以代码为准：`backend/balan-core` 下的 controller 与 `frontend/src/api`。

| 范围 | 端点 | 说明 |
| --- | --- | --- |
| 公开 | `GET /api/v1/public/config` | 品牌、维护公告、验证码开关、支付模式、小程序限额、`edition` 与版本能力位，登录前可读 |
| 场站与设备 | `GET/POST/PUT/DELETE /stations`、`/chargers`；`GET /chargers/{id}`（影子快照）、`/chargers/{id}/ports`、`/chargers/{id}/history?portNo&minutes`、`/chargers/groups`、`/chargers/scenes`、`/device/protocols`、`/device/flows` | 场站/设备/端口 CRUD 与按组筛选，监控与历史曲线，协议与场景注册表 |
| 远程控制 | `POST /chargers/{id}/ports/{no}/start\|stop\|unlock`、`PUT .../threshold`、`GET /commands/{cmdId}` | 指令落库 `device_command`，`cmdId` 幂等回执，超时由定时任务标记 |
| 费率 | `/rate-strategies`（含 `.../enable`、`/periods`） | 峰/平/谷时段、电价与服务费策略、生效区间、一键切换 |
| 订单 | `GET /orders`、`GET /orders/active`、`GET /orders/{id}`、`POST /orders/{id}/pay\|refund`、`/orders/export` | 名单含手机号/车牌/已充时长/实时金额；退款记录复用 `?paymentStatus=REFUNDED` |
| 告警与工单 | `GET /alerts?status=ACTIVE`、`POST /alerts/{id}/ack\|resolve`、`/work-orders`（指派/处理/解决/重开/归档） | 确认幂等，已恢复再确认返回 400 |
| 看板与分析 | `GET /dashboard/summary\|power-trend\|business-trend\|energy-trend\|alert-stats\|events`、`GET /analytics/devices` | 趋势窗口 5–240 分钟；设备分析默认最近 7 天、上限 366 天 |
| 运营对象 | `/app-users`、`/vehicles`、`/app-blacklist`、`/system/dict/**`、`/system/menu/**`、`/system/roles/**`、`/system/config/**` | 用户/车辆/黑名单/字典/菜单/角色授权/系统配置 |
| 审计 | `GET /system/logs/oper`、`/logs/login`、`/logs/oper/export` | `password` 落库前脱敏，重置密码不落请求参数 |
| 仿真与报文 | `GET /simulator/status`、`POST /simulator/start\|stop\|batch\|fault\|unplug`、`GET\|DELETE /mqtt/logs` | 报文环形日志每实例最近 300 条 |
| 移动运维 | `GET /api/v1/staff/profile`、`GET /api/v1/staff/devices/{deviceNumber}` | 两个只读聚合，写操作复用上面的既有接口 |
| 小程序 | `POST /api/app/auth/login\|dev-login`、`GET /api/app/stations?keyword&latitude&longitude`、`/stations/{id}[/rate]`、`POST .../ports/{no}/start`、`/api/app/orders**` | 传坐标时返回 `distanceMeters` 并升序（无坐标排最后），不传坐标行为不变；一人一单由 Redis 锁保证 |
| 实时推送 | `WS /ws/events?token=`（也接受 `Authorization` 头与 `Admin-Token` Cookie） | 推 `power / alert / order / device`，按会话授权场站过滤，事务提交后发送 |
| 观测 | `GET /api/v1/cluster/{node,spi,tasks}`、`GET /api/v1/monitor/node` | 实例身份与幂等计数、SPI 生效、定时任务运行统计、四大中间件健康 |

## 设备协议

**BALAN_MQTT（`balancloud/up|down/{deviceId}/…`）**

| 方向 | Topic | 关键字段 |
| --- | --- | --- |
| 上行 | `up/{id}/online` | `online`、`deviceType`、`portCount`、`ts`（retained，离线用 LWT 发 `online=false`） |
| 上行 | `up/{id}/metric` | 每端口一条：`voltage / current / temperature / powerKw / energyTotal / signal / portStatus` |
| 上行 | `up/{id}/alert` | `level / type / message / value / threshold`，未知 `type` 归入 `PORT_FAULT` 并保留原文 |
| 上行 | `up/{id}/order_event` | `eventType / stopReason ∈ FULL\|NO_LOAD\|FAULT\|DEVICE_STOP`、最终电表读数 |
| 上行 | `up/{id}/cmd_ack` | 按 `cmdId` 幂等，重复回执不重复处理 |
| 下行 | `down/{id}/cmd` | `START / STOP / UNLOCK / SET_THRESHOLD`，`START.params = { orderNo, maxDurationMinutes, chargeLimitAmount }` |
| 下行 | `down/{id}/ota` | 固件升级正文（标准版 OTA 任务使用，扁平字段、无 `cmdId` 信封） |

**OCPP 1.6J**：桩侧连 `ws://<host>:8080/ocpp/{identity}`，WebSocket 子协议 `ocpp1.6`，可选 Basic 认证（`OcppHandshakeInterceptor`，端点可整体关闭）。已实现桩侧 `BootNotification / Heartbeat / StatusNotification / Authorize / MeterValues / StartTransaction / StopTransaction` 与平台侧 `RemoteStartTransaction / RemoteStopTransaction / UnlockConnector`；`transactionId` 与平台订单的映射落在 `ocpp_transaction`，插枪即充由 VIN/idTag 反查车主后补建订单（没有 `RemoteStart` 这一步）。`SET_THRESHOLD` 与固件下发在该协议下明确返回「不支持」而不是静默忽略。

**新增协议/场景不改业务代码**：实现 `DeviceProtocolAdapter`（`protocol()` 报名、`capabilities()` 声明能力、`decode()` 把 `RawFrame` 转成 `PlatformEvent`、`encode()` 编码下行、`dedupKey()` 给去重键）并装配成 bean，由 `UplinkFrameRouter` 按 `charger.protocol` 分派；场景侧实现 `OrderFlow` 注册进 `OrderFlowRegistry`，启动准入、ack 语义、前置半状态、占位判定、自动停规则与停止超时都从场景实现读取。`GET /api/v1/device/protocols` 与 `/api/v1/chargers/scenes` 把注册表下发给前端做显隐。未注册的协议不静默出错：报文只记日志不落影子，远程启动返回 400。照抄模板见 [docs/PROTOCOL-SDK.md](docs/PROTOCOL-SDK.md)。

**但「只写一个 bean」的前提是报文已经到得了平台**。语义层（协议适配器）可插拔，传输层不是：`DownlinkDispatcher.send()` 的 `switch` 里只有 `mqtt` 与 `ocpp` 两条 channel，上行入口也只有 `MqttGateway` 与 `OcppFrameService` 两个调用方。所以另一种 JSON-over-MQTT、或 OCPP 的厂商变体，确实只加一个适配器；而云快充、特来电这类**原生 TCP**（以及 GB/T 27930 的 CAN 链路）必须先补 L0——TCP 服务端与连接/心跳管理、把帧交给 `UplinkFrameRouter.route(deviceNo, event, frame)`、在 `DownlinkDispatcher` 加一条 channel 分支——这一步要改 `balan-core`，不在适配器扩展点范围内。

## 构建与测试

```bash
cd backend && mvn -B test                                              # 577 个单元测试（common 7 + core 370 + platform 200）
cd backend && mvn -B test -pl balan-common,balan-core,balan-server -am # 只跑开源三模块，证明 core 不依赖闭源包
cd backend && mvn -B package -Pcommunity -DskipTests                   # → balancloud-<version>-community.jar
cd backend && mvn -B package -Pstandard  -DskipTests                   # → balancloud-<version>-standard.jar
cd frontend && pnpm build:prod && pnpm build:standard
cd miniapp  && npm run build:h5 && npm run build:mp-weixin
```

单测不依赖中间件，覆盖计费（时段命中、三种模式、增量钳制、电表回退、测点 CAS、最终补结算）、费率校验（覆盖全天、不重叠、服务费策略合并）、告警阈值、SQL 租户注入与白名单、集群锁与选主、报文幂等、事件广播、配置跨实例失效、SPI 装配语义、脱敏与限流、滑块几何、工单状态机、角色授权、场站距离计算。改动计费、费率校验、告警判定这三块逻辑时必须补对应单测。

并发、指令超时与幂等、充电中离线结算、后端重启恢复这些依赖端口锁与真实 MQTT 时序的行为，由端到端脚本验收（不进仓库）；尚未引入 Testcontainers 集成测试。

Windows 上 `mvn package` 前先停掉运行中的实例（jar 被占用会锁文件）；`mvn` 用了系统 JDK 8 会报 `UnsupportedClassVersionError`，显式设 `JAVA_HOME` 指向 JDK 17。

## 多实例部署

同一套 MySQL / Redis / EMQX / TDengine 下并排跑多个后端实例，实例间靠 Redis 协调，不需要注册中心。

```bash
java -jar balancloud-<version>-community.jar --balan.cluster.instance-id=node-a
java -jar balancloud-<version>-community.jar --balan.cluster.instance-id=node-b --server.port=8081
```

- 上行订阅 `$share/balancloud/balancloud/up/#`，一份报文只进一个实例；`metric / order_event / cmd_ack` 进 Redis SETNX 幂等窗口（心跳与下行指令不去重）。下行由 sticky leader 实例（`balan:leader:simulator`）扮演仿真设备应答。
- 定时任务用每租户一把的窗口锁 `balan:window:{task}`，TTL 由任务周期派生并逐个校验「窗口 < 周期 < 租约」；leader 续约是一段原子 Lua（`SET NX` 成功或「键值就是我」才 `PEXPIRE`）。
- 共享订阅不保证同一设备的相邻帧落到同一实例，因此乱帧由行级 CAS 丢弃、下一帧从库里最新状态重算，金额不会重复累计；设备上下线同样按 `online_status` 做 CAS。
- WebSocket 事件先直投本机会话，再广播到 Redis 频道并丢弃自己发过的那份，任意实例都能看到全集群事件；系统配置写入后在事务 `afterCommit` 广播 `balan:config:reload`，避免维护模式/支付模式按实例漂移。
- **Redis 不可用时集群锁与幂等一律 fail-open**（宁可重复处理也不停摆）。窗口锁只保证「不挤在同一 TTL 内」，从来不是 exactly-once，写路径正确性靠行级 CAS 与唯一键。
- 部署期开关：`CLUSTER_WS_BROADCAST`、`MQTT_SHARED_GROUP`（置空则每实例各消费一份）、`MQTT_DEDUPE` / `MQTT_DEDUP_TTL`、`SCHEDULING_POOL_SIZE`（主调度池，默认 4）与晨报专用池。
- 读观测数据的口径：窗口任务的互斥证据是两份计数都在涨（`runs_a + runs_b ≈` 单实例的认领尝试数），不是「一边永远为 0」——两实例 tick 相位会漂移，赢家会翻转；leader 任务才是严格 XOR。

标准版额外提供「平台管理 → 集群监控」：实例存活（`balan:node:<id>` 心跳，TTL 30 秒，只用于显示不参与选主）、Redis 锁归属与「持有实例是否在线」、EMQX 集群状态、逐实例的定时任务统计。

## 演示数据与运维脚本

首次启动后种子数据创建：1 个演示场站（含经纬度）、3 台仿真设备（12 / 10 / 2 路共 24 端口）、1 套启用中的峰/平/谷费率策略、4 个角色账号、完整菜单与按钮权限。

```bash
cd deploy && ./scripts/reset-demo-data.sh --yes   # 复位演示数据（用 docker exec，需在 compose 主机执行）
./scripts/backup-mysql.sh [输出目录]                # mysqldump → .sql.gz + .sha256，按保留期清理
```

`reset-demo-data.sh` 在一个事务里清空订单/明细/退款/车辆/指令/告警/审计与登录日志，端口复位 `IDLE`、设备复位 `OFFLINE`，并重建 TDengine 超级表、清空 Redis 影子与去重和会话键（**所有人要重新登录**）。三点边界：逻辑删除的行有意不物理清除（它们是「编号唯一含已删除」这类校验的依据）、不改账号口令、管理界面不提供任何破坏性入口。执行前先在「仿真控制」停掉仿真器。

## 已知边界

未接通的能力在代码里表现为明确拒绝或空态，不会给出假成功：

- **支付与退款在社区版是平台内的状态流转**（`payment.mode=mock`），不对接微信商户号；`wechat` 选项在配置页保持锁定态。标准版有微信服务商分账（A）与租户直连（B）的完整代码路径，但真实进件与 1 分钱 JSAPI 联调未做，缺收款配置的运营商在真实模式下直接 400，**不会回落到平台商户号**（避免二清）。商户私钥与 APIv3 密钥只存环境变量引用，不入库、不下发前端。
- **短信 / 订阅消息 / 邮件是模拟网关**（标准版），只落 `notification_log`，没有真实供应商；站内信是真实可用的。
- **测试是纯逻辑单测 + 本地 HTTP/浏览器验收脚本**，没有 Testcontainers 集成测试；并发、指令超时、重启恢复靠脚本实测，不靠 CI 保证。
- **未与真实厂商硬件联调**：设备链路走内置 MQTT 与 OCPP 模拟器；`HEAVY` 场景在枚举里预留，尚无订单流程实现。
- **规模上限是可数的，超限的解法只写了触发条件、没有实现**：测点入库每 1 秒最多 drain 500 条（≈500 点/秒，按 5 秒一帧约当 2500 个端口），队列 20000 条写满即丢并打 WARN，不回头压 broker；OCPP 下行是单频道广播，每个实例都收全量再内存过滤；标准版跨租户大盘逐租户循环读、单次最多聚合 200 个租户。上 MQ、改 pattern 订阅、做汇总表预聚合都写在「命中才升级」的清单里，当前都不在代码里。
- **移动运维端 `pkg-staff` 只在 H5 视口验收过**，微信开发者工具与真机未验收。
- OTA、端口预约、停车计费、开放平台签名接口属标准版，接口与状态机已实现；固件签名校验与安全启动链、真机升级、地锁/道闸硬件联动未做。
- 403 越权尝试不落 `sys_oper_log`（`@PreAuthorize` 在 `@Log` 切面之前拒绝，业务规则拒绝的 400 会落）；方法参数上的 `@Valid` 早于方法级鉴权，非法请求体会先返回 400。
- AI 诊断不在本仓库，也还没有对外文档：平台只负责把设备、订单、告警与事件流跑通。

## 许可与贡献

- 社区版 **AGPL-3.0**（[LICENSE](LICENSE)）；标准版闭源部分（`backend/balan-platform/`、`frontend/src/views/platform/`、V500+ 迁移）为商业许可，双仓与逐路径清单见 [LICENSING.md](LICENSING.md)。
- `frontend/` 基于 **RuoYi-Vue3**（MIT，Copyright (c) 2018 RuoYi）骨架改造；后端为自研实现，仅按该前端的接口契约提供兼容的登录与菜单接口。第三方归属与许可证声明集中在 [NOTICE](NOTICE)，再分发时请一并保留。
- 依赖的 MySQL、Redis、EMQX、TDengine、Element Plus、Vue、ECharts、Spring、MyBatis-Plus、Apache POI 各遵循其上游许可证，不随本仓库分发。本仓库未复制任何 AGPL / XCPL 或其他受限许可项目的代码、图片、报文格式或脚本。
- 贡献请读 [CONTRIBUTING.md](CONTRIBUTING.md)：功能性变更先开 Issue 对齐场景；表结构必须走 Flyway 新迁移（`V{n}__*.sql`），不改历史迁移；改到计费/费率校验/告警判定要补单测，`mvn -B test` 必须绿；不要提交密钥（`deploy/.env`、`application-local.yml` 已 gitignore，新增配置请同步提供 `.example`，占位值写 `change_me_*`）；贡献需签署 CLA（DCO 不足以支撑 AGPL + 商业双许可的再授权）。
- **有事就开 Issue**：缺陷、功能请求、接入自己桩遇到的问题，都直接在仓库开 Issue；需要商业许可（私有化部署标准版、以标准版为基础对外提供服务、去品牌二次分发）也先开 Issue 说明场景，授权范围、报价与 SLA 逐单约定——本仓库不挂价目表，也不承诺没实现的能力。

### 公众号

版本更新和实现踩坑写在公众号，商业询价不走这里、仍然走上面的 Issue 渠道（留痕、可追溯，也不会有人私聊我承诺没做的能力）。

<p align="center">
  <a href="http://weixin.qq.com/r/mp/ZSBheVfE-AqDrRMa93V3"><img src="docs/wechat-mp-qrcode.png" width="215" alt="微信公众号二维码：扫码或在微信里点开链接即可关注"></a>
  <br>
  <sub>扫码，或在微信里打开 <a href="http://weixin.qq.com/r/mp/ZSBheVfE-AqDrRMa93V3">weixin.qq.com/r/mp/ZSBheVfE-AqDrRMa93V3</a></sub>
</p>

## FAQ

**没有真实充电桩能体验吗？** 能。仿真器是 3 台设备 24 个端口，走的是和真实设备一样的 MQTT/OCPP 链路，可以启动充电、注入故障、观察报文、看告警与结算。

**能直接给多个运营商用吗？** 数据面已经就绪（`tenant_id` 贯穿 29 张表、唯一键租户内化、查询自动注入、定时任务按租户循环、MQTT 上行按 `device_no` 反查租户、Redis 键带租户段、WebSocket 按租户过滤），**控制面在社区版里不存在**——建租户、套餐限额、入驻审核这些界面在标准版。所以一个社区版 jar 实际服务一个运营商；单运营商内部的场站级隔离由 `sys_user_station` 决定。系统配置里的「多租户模式」开关只影响登录页展示，不改变数据隔离（隔离始终开启）。

**为什么不用 MQ？** 数百台设备规模内，队列语义已由 EMQX 共享订阅、Redis 去重与 pub/sub、`device_command` 表和核心侧 12 个定时任务（标准版另有 4 个）分担，引入 broker 等于多一处必须高可用的状态。需要外部服务订阅业务事件、或需要跨系统回放时才是上 MQ 的时机。

**为什么权限不够的账号有时先拿到 400 而不是 403？** 方法参数上的 `@Valid` 校验发生在方法级鉴权之前，非法请求体先返回校验错误；用合法请求体即可观察到真实的 403。

**怎么临时停掉仿真器 / 退款 / 小程序充电？** 「系统管理 → 系统配置」三个开关各管一路：仿真器开关挡「启动/扩容/故障注入」但保留「停止」，退款开关只挡新的退款请求，维护模式只挡小程序发起新充电并在登录页与小程序展示公告——后台、看单与停止充电始终可用，避免把自己锁在门外。保存即热生效。

**Windows 上能验证优雅停机吗？** 不能直接验证——Windows 无法向 JVM 发 SIGTERM，`taskkill /F` 是硬杀。改验 `POST /simulator/stop`（发布离线并清空内存端口运行时）与 `@PreDestroy` 钩子；Linux/macOS 下 `Ctrl+C` 即可验证完整链路。

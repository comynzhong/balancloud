#!/usr/bin/env bash
#
# 衡电云控 BALANCLOUD — 演示数据重置（仅本地/演示环境）
#
# 清空 MySQL 运行期数据 + TDengine 测点 + Redis 缓存（设备影子、功率采样、告警去重、登录会话）。
# 清空登录会话意味着所有在线用户需要重新登录。
#
# 用法：
#   cd deploy && ./scripts/reset-demo-data.sh --yes
#
# 建议先在管理后台「仿真控制」里停止仿真器，否则重置过程中仿真器会继续上报测点。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

if [[ "${1:-}" != "--yes" ]]; then
  echo "该脚本会删除订单、告警、指令与审计日志，确认无误后执行："
  echo "  $0 --yes"
  exit 1
fi

if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
  echo "缺少 $DEPLOY_DIR/.env，请先复制 .env.example 并填写密码" >&2
  exit 1
fi

# shellcheck disable=SC1091
source "$DEPLOY_DIR/.env"

echo "[1/3] MySQL：清空运行期数据并复位设备/端口"
docker exec -i balan-mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  < "$SCRIPT_DIR/reset-demo-data.sql"

echo "[2/3] TDengine：重建测点超级表（清历史，且让子表按 tenant_id 标签重建）"
# TDengine 改不了已存在子表的标签，只删行会留下 tenant_id 为 NULL 的旧子表，所以这里直接重建超级表。
docker exec balan-tdengine taos -s "DROP STABLE IF EXISTS balan_metrics.device_metric;"
docker exec -i balan-tdengine taos < "$DEPLOY_DIR/tdengine/init.sql"

echo "[3/3] Redis：清理影子、功率采样、告警去重与登录会话"
docker exec balan-redis sh -c '
  for pattern in "balan:*" "alert:dedup:*" "login_tokens:*" "app_tokens:*"; do
    redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "$pattern" \
      | xargs -r -n 200 redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL
  done
'

echo "演示数据已重置，可重新启动仿真器开始演示。"

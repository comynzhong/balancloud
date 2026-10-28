#!/usr/bin/env bash
#
# 衡电云控 BALANCLOUD — MySQL 逻辑备份
#
# 默认通过 compose 中的 balan-mysql 容器执行 mysqldump，容器不存在时回退到本机 mysqldump。
# 连接信息只从 deploy/.env 读取，不写入仓库；备份文件默认落在 deploy/backups/。
#
# 用法：
#   cd deploy && ./scripts/backup-mysql.sh [输出目录]
#
# 可用环境变量：
#   BACKUP_RETAIN_DAYS  保留天数，默认 7；设为 0 表示不自动清理
#   BACKUP_MODE         docker（默认）/ local
#
# 建议 cron（每天 03:10）：
#   10 3 * * * cd /opt/balancloud/deploy && ./scripts/backup-mysql.sh >> backups/backup.log 2>&1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$DEPLOY_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 $ENV_FILE，请先复制 .env.example 并填写密码" >&2
  exit 1
fi

# shellcheck disable=SC1091
source "$ENV_FILE"

: "${MYSQL_DATABASE:?MYSQL_DATABASE 未配置}"
: "${MYSQL_USER:?MYSQL_USER 未配置}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD 未配置}"

BACKUP_DIR="${1:-$DEPLOY_DIR/backups}"
RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-7}"
MODE="${BACKUP_MODE:-docker}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-balan-mysql}"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

STAMP="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/${MYSQL_DATABASE}_${STAMP}.sql.gz"
PARTIAL="${FILE}.part"

cleanup() {
  rm -f "$PARTIAL"
}
trap cleanup EXIT

dump_args=(
  --single-transaction
  --quick
  --routines
  --triggers
  --events
  --set-gtid-purged=OFF
  --default-character-set=utf8mb4
  --no-tablespaces
  --databases "$MYSQL_DATABASE"
)

if [[ "$MODE" == "docker" ]] && command -v docker >/dev/null 2>&1 \
  && docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "备份方式：docker exec $MYSQL_CONTAINER"
  docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
    mysqldump -u"$MYSQL_USER" "${dump_args[@]}" | gzip -c > "$PARTIAL"
else
  if ! command -v mysqldump >/dev/null 2>&1; then
    echo "未找到可用的 balan-mysql 容器或本机 mysqldump" >&2
    exit 1
  fi
  echo "备份方式：本机 mysqldump"
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -h "${MYSQL_HOST:-127.0.0.1}" \
    -P "${MYSQL_PORT:-3306}" -u"$MYSQL_USER" "${dump_args[@]}" | gzip -c > "$PARTIAL"
fi

if [[ ! -s "$PARTIAL" ]] || ! gzip -t "$PARTIAL" 2>/dev/null; then
  echo "备份失败：输出为空或 gzip 校验不通过" >&2
  exit 1
fi

mv "$PARTIAL" "$FILE"
chmod 600 "$FILE"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$FILE" > "${FILE}.sha256"
  chmod 600 "${FILE}.sha256"
fi

SIZE="$(du -h "$FILE" | cut -f1)"
echo "备份完成：$FILE（$SIZE）"
if [[ -f "${FILE}.sha256" ]]; then
  echo "校验文件：${FILE}.sha256"
fi

if [[ "$RETAIN_DAYS" =~ ^[0-9]+$ ]] && [[ "$RETAIN_DAYS" -gt 0 ]]; then
  deleted="$(find "$BACKUP_DIR" -maxdepth 1 -type f \
    \( -name "${MYSQL_DATABASE}_*.sql.gz" -o -name "${MYSQL_DATABASE}_*.sql.gz.sha256" \) \
    -mtime +"$RETAIN_DAYS" -print -delete | wc -l | tr -d ' ')"
  echo "已清理超过 ${RETAIN_DAYS} 天的备份文件：$deleted 个"
fi

echo "恢复示例：gunzip -c '$FILE' | docker exec -i $MYSQL_CONTAINER mysql -u$MYSQL_USER -p"

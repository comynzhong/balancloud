#!/usr/bin/env bash
# 从双仓主仓裁出社区版快照；具体规则见同目录 split_community.py。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PYTHON="${PYTHON:-python3}"
if ! command -v "$PYTHON" >/dev/null 2>&1; then
  PYTHON=python
fi

exec "$PYTHON" "$ROOT/deploy/scripts/split_community.py" "$@"

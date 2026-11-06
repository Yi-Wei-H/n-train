#!/usr/bin/env bash
# 启动服务：./start.sh [business|agent|task|member|admin|gateway]
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

SVC="${1:-}"
if [ -n "$SVC" ]; then
  start_one "$SVC"
  exit $?
fi

failed=0
for s in ${START_ORDER}; do
  start_one "$s" || failed=$((failed + 1))
done
echo
if [ "$failed" -eq 0 ]; then
  echo "全部服务已启动，可用 ./status.sh 查看状态（首次启动 data 会自动创建超管 admin）"
else
  echo "[ERROR] ${failed} 个服务启动失败，见上方输出" >&2
  exit 1
fi

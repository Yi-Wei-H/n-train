#!/usr/bin/env bash
# 重启服务：./restart.sh [business|task|member|admin|gateway]（更新 jar 后使用）
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

SVC="${1:-}"
if [ -n "$SVC" ]; then
  stop_one "$SVC"
  start_one "$SVC"
  exit $?
fi

failed=0
for s in ${START_ORDER}; do
  stop_one "$s"
  start_one "$s" || failed=$((failed + 1))
done
if [ "$failed" -ne 0 ]; then
  echo "[ERROR] ${failed} 个服务重启失败，见上方输出" >&2
  exit 1
fi

#!/usr/bin/env bash
# 停止服务：./stop.sh [business|task|member|admin|gateway]
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

SVC="${1:-}"
if [ -n "$SVC" ]; then
  stop_one "$SVC"
else
  for s in ${SERVICES}; do
    stop_one "$s"
  done
fi

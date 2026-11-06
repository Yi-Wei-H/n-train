#!/usr/bin/env bash
# 查看服务状态：./status.sh（运行中显示 pid/内存/端口）
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

for s in ${SERVICES}; do
  status_one "$s"
done

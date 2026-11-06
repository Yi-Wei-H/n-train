#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${APP_HOME:-/learn/new_train}"

# ==================== 服务定义 ====================
SERVICES="business agent task member admin gateway"
START_ORDER="business agent task member admin gateway"

# ==================== 凭证环境变量（全部服务共用） ====================
ENV_FILE="${NTRAIN_ENV_FILE:-${APP_HOME}/service/service.env}"
if [ ! -f "$ENV_FILE" ] && [ -f "${SCRIPT_DIR}/../../secrets/.env" ]; then
  ENV_FILE="${SCRIPT_DIR}/../../secrets/.env"
fi
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ENV_FILE"
  set +a
else
  echo "[WARN] 未找到凭证文件（${APP_HOME}/service/service.env 或 secrets/.env），服务将缺少凭证无法启动" >&2
fi

jvm_xms() {
  echo "128m"
}
jvm_xmx() {
  case "$1" in
    business) echo "512m" ;;
    *)        echo "384m" ;;
  esac
}
jvm_meta() {
  echo "192m"
}

jvm_opts() {
  local svc="$1"
  echo "-Xms$(jvm_xms "$svc") -Xmx$(jvm_xmx "$svc") -XX:MaxMetaspaceSize=$(jvm_meta "$svc") \
-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${APP_HOME}/log/${svc}/dump.hprof \
-Dntrain.log-dir=${APP_HOME}/log/${svc}"
}

# ==================== 路径/端口辅助 ====================
jar_file() {
  # 每个服务目录取最新修改的 .jar（jar 名任意/带版本号都行，传新版覆盖后 restart 即可）
  ls -t "${APP_HOME}/service/$1"/*.jar 2>/dev/null | head -1
}
pid_file() { echo "${APP_HOME}/run/${1}.pid"; }
log_dir()  { echo "${APP_HOME}/log/${1}"; }
port_of() {
  case "$1" in
    gateway)  echo "8000" ;;
    member)   echo "8001" ;;
    admin)    echo "8002" ;;
    agent)    echo "8004" ;;
    business) echo "8005" ;;
    task)     echo "8006" ;;
    *) echo "-" ;;
  esac
}

# ==================== 公共函数 ====================
valid_service() {
  local svc="$1"
  for s in ${SERVICES}; do
    [ "$s" = "$svc" ] && return 0
  done
  echo "[ERROR] 未知服务: $svc（可选: ${SERVICES}）" >&2
  return 1
}

is_running() {
  local svc="$1" pid=""
  [ -f "$(pid_file "$svc")" ] && pid="$(cat "$(pid_file "$svc")")"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

# ==================== 启动探测 ====================
# 启动成功的判定：进程存活且端口已监听；START_TIMEOUT 秒内未就绪即判失败
START_TIMEOUT="${START_TIMEOUT:-120}"

# 端口是否已监听：0=监听中 1=未监听 2=无法探测（无 ss，如 Windows 本地）
port_in_use() {
  local port="$1"
  command -v ss >/dev/null 2>&1 || return 2
  ss -ltn 2>/dev/null | awk -v p=":${port}" '$4 ~ (p "$") {found=1} END {exit !found}'
}

# 等待进程监听端口：0=就绪 1=进程已退出 2=超时仍未监听
wait_started() {
  local svc="$1" pid="$2" port="$3" waited=0
  while [ "$waited" -lt "$START_TIMEOUT" ]; do
    kill -0 "$pid" 2>/dev/null || return 1
    port_in_use "$port"
    case $? in
      0) return 0 ;;
      2) sleep 3; kill -0 "$pid" 2>/dev/null && return 0 || return 1 ;;
    esac
    sleep 1
    waited=$((waited + 1))
  done
  echo "[WARN] ${svc} 在 ${START_TIMEOUT}s 内仍未监听端口 ${port}" >&2
  return 2
}

start_one() {
  local svc="$1"
  if ! valid_service "$svc"; then return 1; fi

  local jar port running_pid
  port="$(port_of "$svc")"
  if is_running "$svc"; then
    running_pid="$(cat "$(pid_file "$svc")")"
    port_in_use "$port"
    if [ $? -eq 1 ]; then
      echo "[ERROR] ${svc} 进程存在 (pid ${running_pid}) 但端口 ${port} 未监听，请先 restart"
      return 1
    fi
    echo "[SKIP] ${svc} 已在运行 (pid ${running_pid})"
    return 0
  fi

  jar="$(jar_file "$svc")"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then
    echo "[ERROR] ${svc} 的 jar 不存在（${APP_HOME}/service/${svc}/ 下没有 .jar，请先上传）"
    return 1
  fi

  # 端口已被占用时新进程必然绑定失败，提前报错而不是假装启动成功
  port_in_use "$port"
  if [ $? -eq 0 ]; then
    echo "[ERROR] ${svc} 端口 ${port} 已被占用，未启动（请先排查残留进程）"
    return 1
  fi

  mkdir -p "$(log_dir "$svc")" "${APP_HOME}/run"
  cd "${APP_HOME}" || return 1
  nohup java $(jvm_opts "$svc") -jar "$jar" >> "$(log_dir "$svc")/stdout.log" 2>&1 &
  local pid=$!
  echo "$pid" > "$(pid_file "$svc")"

  wait_started "$svc" "$pid" "$port"
  local rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "[OK] ${svc} 已启动 (pid ${pid}, port ${port})，日志: $(log_dir "$svc")"
    return 0
  fi
  if [ "$rc" -eq 1 ]; then
    rm -f "$(pid_file "$svc")"
    echo "[ERROR] ${svc} 启动失败（进程已退出），见 $(log_dir "$svc")/stdout.log" >&2
  else
    echo "[ERROR] ${svc} 启动超时：${START_TIMEOUT}s 内未监听端口 ${port}（进程仍在，可稍后 ./status.sh 复查）" >&2
  fi
  return 1
}

stop_one() {
  local svc="$1"
  if ! valid_service "$svc"; then return 1; fi

  if ! is_running "$svc"; then
    echo "[SKIP] ${svc} 未在运行"
    rm -f "$(pid_file "$svc")"
    return 0
  fi

  local pid
  pid="$(cat "$(pid_file "$svc")")"
  echo "[OK] ${svc} 停止中 (pid ${pid})..."
  kill "$pid" 2>/dev/null
  # 最多等 30 秒优雅退出，超时强杀
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "[WARN] ${svc} 未响应 SIGTERM，强制杀掉"
    kill -9 "$pid" 2>/dev/null
  fi
  rm -f "$(pid_file "$svc")"
  echo "[OK] ${svc} 已停止"
}

status_one() {
  local svc="$1" port pid=""
  port="$(port_of "$svc")"
  if is_running "$svc"; then
    pid="$(cat "$(pid_file "$svc")")"
    local rss=""
    rss="$(ps -o rss= -p "$pid" 2>/dev/null | awk '{printf "%.0fMB", $1/1024}')"
    [ -z "$rss" ] && rss="?"
    echo "$svc : RUNNING  pid=$pid  rss=$rss  port=$port"
  else
    echo "$svc : STOPPED  port=$port"
  fi
}

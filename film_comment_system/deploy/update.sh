#!/usr/bin/env bash
# ====================================================================
# film_comment_system 日常发版更新脚本（root 执行）
#
# 作用：替换后端 jar 与前端 dist、重启服务、健康检查，失败自动回滚。
#
# 【用法】
#   1. 把新构建产物放到 deploy 目录（名称固定）：
#        deploy/app.jar   <- 后端新包
#        deploy/dist/     <- 前端新包（可选，不放则跳过前端更新）
#   2. 执行：chmod +x update.sh && ./update.sh
#      或直接指定路径：./update.sh /tmp/xxx.jar /tmp/dist
#
# 【回滚策略】
#   每次发布先把线上旧版本备份到 /opt/film/backup/<时间戳>/；
#   新版本 60s 内未能健康响应则自动恢复旧 jar/dist 并重启；
#   备份默认保留最近 5 份，更早的自动清理。
#
# 注意：上传到服务器前确保本文件是 LF 换行。
# ====================================================================
set -euo pipefail

PKG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="/opt/film"
UNIT="film-comment"
PORT="${APP_PORT:-8080}"
BACKUP_ROOT="$APP_HOME/backup"
KEEP_BACKUPS="${KEEP_BACKUPS:-5}"

JAR_SRC="${1:-$PKG_DIR/app.jar}"
DIST_SRC="${2:-$PKG_DIR/dist}"

BOLD="\033[1m"; GREEN="\033[32m"; YELLOW="\033[33m"; RED="\033[31m"; RESET="\033[0m"
step() { echo; echo -e "${BOLD}==> $*${RESET}"; }
info() { echo "    $*"; }
warn() { echo -e "    ${YELLOW}[WARN]${RESET} $*" >&2; }
die()  { echo -e "${RED}[ERROR]${RESET} $*" >&2; exit 1; }

wait_http() {
  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$PORT/" 2>/dev/null || echo -n)"
    # 只要应用给出 HTTP 响应（含 404）即认为已就绪
    if [[ -n "$code" && "$code" != "000" ]]; then
      info "应用已响应（HTTP $code）"
      return 0
    fi
    sleep 1
  done
  return 1
}

[[ "$(id -u)" == "0" ]] || die "请用 root 执行：sudo ./update.sh"
[[ -f "$JAR_SRC" ]] || die "找不到后端 jar：$JAR_SRC"
[[ -f "$APP_HOME/app.jar" ]] || die "线上 jar 不存在（$APP_HOME/app.jar），请先运行 install.sh"
command -v curl >/dev/null 2>&1 || die "缺少 curl"

TS="$(date +%Y%m%d-%H%M%S)"
BACKUP="$BACKUP_ROOT/$TS"

step "1/4 备份线上版本 -> $BACKUP"
mkdir -p "$BACKUP"
cp -a "$APP_HOME/app.jar" "$BACKUP/app.jar"
if [[ -d "$APP_HOME/dist" ]]; then
  cp -a "$APP_HOME/dist" "$BACKUP/dist"
  info "已备份 jar 与 dist"
else
  info "已备份 jar（线上无 dist 目录）"
fi

step "2/4 替换产物并重启"
install -o root -g root -m 0644 "$JAR_SRC" "$APP_HOME/app.jar"
if [[ -d "$DIST_SRC" && -f "$DIST_SRC/index.html" ]]; then
  rm -rf "$APP_HOME/dist"
  cp -a "$DIST_SRC" "$APP_HOME/dist"
  chown -R root:root "$APP_HOME/dist"
  info "dist 已替换"
else
  warn "未找到可用 dist（$DIST_SRC），本次仅更新后端，线上前端保持不变"
fi
systemctl restart "$UNIT"

step "3/4 健康检查（$PORT，最长 60s）"
if wait_http; then
  step "4/4 发布成功"
  # 清理旧备份，保留最近 KEEP_BACKUPS 份
  ls -1dt "$BACKUP_ROOT"/*/ 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -rf
  echo
  echo -e "${GREEN}${BOLD}发布完成。${RESET}"
  echo "  新版本: $JAR_SRC"
  echo "  日志:   journalctl -u $UNIT -f"
  exit 0
fi

# ---------------------------------------------------------------- 自动回滚
step "4/4 健康检查未通过，自动回滚到 $TS"
systemctl stop "$UNIT" || true
install -o root -g root -m 0644 "$BACKUP/app.jar" "$APP_HOME/app.jar"
if [[ -d "$BACKUP/dist" ]]; then
  rm -rf "$APP_HOME/dist"
  cp -a "$BACKUP/dist" "$APP_HOME/dist"
  chown -R root:root "$APP_HOME/dist"
fi
systemctl start "$UNIT"
if wait_http; then
  warn "回滚成功：服务已恢复为 $TS 版本"
  echo -e "${RED}本次发布失败，已回滚。${RESET} 排查新版本日志："
  echo "  journalctl -u $UNIT -n 100 --no-pager | grep -iE 'error|exception'"
  exit 1
else
  die "回滚后服务仍未就绪！请立即排查：journalctl -u $UNIT -e"
fi
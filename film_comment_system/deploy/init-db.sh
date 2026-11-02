#!/usr/bin/env bash
# ====================================================================
# film_comment_system 数据库初始化 / 重建脚本（root 执行，仅首次或重建时跑）
#
# 职责：
#   1. 创建数据库 film_comment_system（utf8mb4）
#   2. 导入仓库导出的种子数据 film_comment_system.sql
#   3. 按 env 配置创建最小权限应用账号（仅 SELECT/INSERT/UPDATE/DELETE）
#   4. 把种子图片 URL 从 http://localhost:9090 替换为真实域名（fix-seed-urls.sql）
#   5. 启动后端服务并做健康检查；Nginx 证书就绪时 reload
#
# 【执行】
#   chmod +x init-db.sh && ./init-db.sh [--yes] [--sql 路径]
#     --yes     跳过「将清空现有数据」确认（重建时用）
#     --sql     指定种子 SQL（默认 deploy 同级目录的 film_comment_system.sql）
#
# 【管理员连接方式】（用于建库/导数据，需要 DDL 权限）
#   默认走本机 root socket：mysql -uroot（Debian/Ubuntu 直接可用）
#   使用远程/云 MySQL 时用环境变量指定：
#     MYSQL_ADMIN_HOST / MYSQL_ADMIN_PORT / MYSQL_ADMIN_USER / MYSQL_ADMIN_PASSWORD
#
# 前置：先运行 install.sh，确保 /etc/film-comment/env 已生成。
# 注意：上传到服务器前确保本文件是 LF 换行。
# ====================================================================
set -euo pipefail

PKG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-/etc/film-comment/env}"
UNIT="film-comment"
PORT="${APP_PORT:-8080}"
DB_NAME="${DB_NAME:-film_comment_system}"
SQL_FILE="${SQL_FILE:-$PKG_DIR/../film_comment_system.sql}"
ASSUME_YES=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes) ASSUME_YES=1 ;;
    --sql) SQL_FILE="$2"; shift ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
  shift
done

BOLD="\033[1m"; GREEN="\033[32m"; YELLOW="\033[33m"; RED="\033[31m"; RESET="\033[0m"
step() { echo; echo -e "${BOLD}==> $*${RESET}"; }
info() { echo "    $*"; }
warn() { echo -e "    ${YELLOW}[WARN]${RESET} $*" >&2; }
die()  { echo -e "${RED}[ERROR]${RESET} $*" >&2; exit 1; }

[[ "$(id -u)" == "0" ]] || die "请用 root 执行：sudo ./init-db.sh"
[[ -f "$ENV_FILE" ]] || die "未找到 $ENV_FILE，请先运行 install.sh"
[[ -f "$SQL_FILE" ]] || die "未找到种子 SQL：$SQL_FILE（可用 --sql 指定路径）"

# 读入环境配置（与 systemd EnvironmentFile 同格式）
set -a; . "$ENV_FILE"; set +a
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" && -n "$DB_URL" && -n "$FILE_BASE_URL" ]] \
  || die "$ENV_FILE 内容不完整（需 DB_USERNAME/DB_PASSWORD/DB_URL/FILE_BASE_URL）"

# 管理员 mysql 连接方式
mysql_admin() {
  if [[ -n "${MYSQL_ADMIN_HOST:-}" ]]; then
    local args=(-h"$MYSQL_ADMIN_HOST" -P"${MYSQL_ADMIN_PORT:-3306}" -u"${MYSQL_ADMIN_USER:-root}")
    # 未提供密码时不传 -p，避免 mysql 误进入交互式密码提示
    [[ -n "${MYSQL_ADMIN_PASSWORD:-}" ]] && args+=(-p"$MYSQL_ADMIN_PASSWORD")
    mysql "${args[@]}" "$@"
  else
    mysql -uroot "$@"
  fi
}

# ----------------------------------------------------------------
step "1/4 创建数据库与应用账号（管理员连接）"
if ! mysql_admin -e "SELECT 1" >/dev/null 2>&1; then
  die "无法以管理员身份连接 MySQL（本机 root socket 失败时，请设置 MYSQL_ADMIN_* 环境变量）"
fi

mysql_admin -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
info "数据库 $DB_NAME 就绪"

# 应用账号：最小权限（无 DDL/授权），host 覆盖 localhost 与 127.0.0.1（JDBC 走 TCP）
ESC_PW="${DB_PASSWORD//\'/\'\'}"
mysql_admin <<SQL
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'localhost' IDENTIFIED BY '$ESC_PW';
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$ESC_PW';
ALTER USER '$DB_USERNAME'@'localhost' IDENTIFIED BY '$ESC_PW';
ALTER USER '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$ESC_PW';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
info "最小权限账号 $DB_USERNAME 就绪（SELECT/INSERT/UPDATE/DELETE，无 DDL）"

# ----------------------------------------------------------------
step "2/4 导入种子数据（会重建全部表！）"
if [[ "$ASSUME_YES" != "1" ]]; then
  read -r -p "    导入 $SQL_FILE 将 DROP 并重建 $DB_NAME 全部表，现有数据会丢失，继续？ [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]] || die "已取消"
fi
mysql_admin "$DB_NAME" < "$SQL_FILE"
info "种子数据导入完成"

# ----------------------------------------------------------------
step "3/4 修正种子图片 URL 前缀（localhost:9090 -> $FILE_BASE_URL）"
TMP_SQL="$(mktemp /tmp/fix-seed-urls.XXXXXX.sql)"
trap 'rm -f "$TMP_SQL"' EXIT
{ while IFS= read -r line; do printf '%s\n' "${line//https:\/\/your-domain.com/$FILE_BASE_URL}"; done < "$PKG_DIR/fix-seed-urls.sql"; } > "$TMP_SQL"
mysql_admin "$DB_NAME" < "$TMP_SQL"
fixed="$(mysql_admin "$DB_NAME" -N -e "SELECT COUNT(*) FROM film WHERE cover LIKE 'http://localhost:9090%'")"
info "执行完成，剩余未替换海报数：$fixed（应为 0）"

# ----------------------------------------------------------------
step "4/4 启动后端服务"
systemctl enable "$UNIT" >/dev/null 2>&1 || true
systemctl restart "$UNIT"
info "等待应用就绪（端口 $PORT，最长 60s）..."
ok=false
for _ in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$PORT/" 2>/dev/null || echo -n)"
  if [[ -n "$code" && "$code" != "000" ]]; then
    info "应用已响应（HTTP $code）"
    ok=true; break
  fi
  sleep 1
done
if ! $ok; then
  warn "健康检查超时，请查看日志：journalctl -u $UNIT -e"
else
  if [[ -f /etc/nginx/cert/fullchain.pem ]] && nginx -t 2>/dev/null; then
    systemctl reload nginx 2>/dev/null || true
    info "Nginx 已 reload"
  fi
fi

# ----------------------------------------------------------------
echo
echo -e "${GREEN}${BOLD}数据库初始化完成。${RESET}"
echo "  站点地址: $FILE_BASE_URL"
echo
echo -e "${RED}${BOLD}上线前安全清单（必做）：${RESET}"
echo "  * SQL 种子数据包含 9 个演示/管理账号（admin、John、ccc/bbb/aaa/acc/ddd/user/user2），"
echo "    其口令哈希随 dump 公开传播，等同已知密码。决策为「账号保留、仅重置密码」，"
echo "    参照部署包内 fix-seed-urls.sql 末尾「重置全部账号口令」段执行（生成 bcrypt 哈希）："
echo "        htpasswd -bnBC 10 '' '你的新密码' | tr -d ':\n'"
echo "    把输出中的 \$2y\$ 改成 \$2a\$ 后填入 SQL 中的 UPDATE 语句并执行。"
echo "  * 验证清单建议：登录 Cookie(http-only) / 刷新子路由 / 上传图片 / 海报显示 /"
echo "    登录限流(连续失败 IP 受限) / 改密后被踢下线"
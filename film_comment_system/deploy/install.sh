#!/usr/bin/env bash
# ====================================================================
# film_comment_system 服务器一键安装脚本（首次部署用，需 root 执行）
#
# 【前置准备】（在服务器上完成）
#   1. 把整个 deploy/ 目录上传到服务器（例如 /root/film-deploy/），
#      并放入构建产物与种子资源：
#         deploy/app.jar   <- springboot/target/springboot-0.0.1-SNAPSHOT.jar
#         deploy/dist/     <- vue/dist/
#         deploy/files/    <- 仓库 film_comment_system/files/（种子电影海报/头像）
#   2. 域名已解析到本机；SSL 证书放到：
#         /etc/nginx/cert/fullchain.pem
#         /etc/nginx/cert/privkey.pem
#      （证书暂未就绪也可以先装，脚本会跳过 Nginx 生效，补齐后 reload）
#
# 【执行】
#   chmod +x install.sh && ./install.sh
#   脚本会交互询问：域名 / 数据库账号密码 / Redis 密码；
#   也可用环境变量免交互：DOMAIN、DB_USERNAME、DB_PASSWORD、REDIS_PASSWORD
#
# 【可选开关】
#   SKIP_DEPS=1            跳过依赖安装（java17 / nginx / curl）
#   INSTALL_DB_SERVICES=1  同时安装本机 mysql-server 与 redis-server
#                          （用云数据库/云 Redis 则保持默认 0）
#   ASSUME_YES=1           所有确认自动通过
#   DEPLOY_MODE=test       内网/本地 HTTP 测试部署（无域名无证书也能跑，
#                          默认 prod=公网 HTTPS）；交互执行时也会询问
#   ENV_FILE=              环境文件目标路径（默认 /etc/film-comment/env）
#
# 本脚本可重复执行（幂等）：已存在的 env / 服务配置默认不覆盖。
# 注意：上传到服务器前确保本文件是 LF 换行（勿被 Windows 转成 CRLF）。
# ====================================================================
set -euo pipefail

PKG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_USER="john123456"
APP_HOME="/opt/film"
ENV_DIR="/etc/film-comment"
ENV_FILE="${ENV_FILE:-$ENV_DIR/env}"
UNIT="film-comment"

BOLD="\033[1m"; GREEN="\033[32m"; YELLOW="\033[33m"; RED="\033[31m"; RESET="\033[0m"
step() { echo; echo -e "${BOLD}==> $*${RESET}"; }
info() { echo "    $*"; }
warn() { echo -e "    ${YELLOW}[WARN]${RESET} $*" >&2; }
die()  { echo -e "${RED}[ERROR]${RESET} $*" >&2; exit 1; }
confirm() {
  local msg="$1"
  [[ "${ASSUME_YES:-0}" == "1" ]] && return 0
  read -r -p "    $msg [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]]
}

# ----------------------------------------------------------------
step "1/8 环境检查"
[[ "$(id -u)" == "0" ]] || die "请用 root 执行：sudo ./install.sh"
[[ -f "$PKG_DIR/env.example" ]] || die "缺少 env.example，deploy 目录不完整"

# 收集配置（环境变量优先，无则交互填写）
if [[ -z "${DOMAIN:-}" ]]; then
  [[ -t 0 ]] || die "缺少 DOMAIN 环境变量（非交互模式必须显式提供）"
  read -r -p "    站点域名或内网 IP（如 film.example.com / 192.168.1.100，不含协议/端口）: " DOMAIN
fi
[[ "$DOMAIN" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || die "域名格式不合法：$DOMAIN"

# 部署模式：prod=公网 HTTPS（默认）；test=内网/本地 HTTP 演练
DEPLOY_MODE="${DEPLOY_MODE:-}"
if [[ -z "$DEPLOY_MODE" ]]; then
  if [[ -t 0 ]]; then
    read -r -p "    部署类型 [1=公网 HTTPS（默认）/ 2=内网 HTTP 测试]: " mode_ans
    [[ "$mode_ans" == "2" ]] && DEPLOY_MODE="test"
  fi
  DEPLOY_MODE="${DEPLOY_MODE:-prod}"
fi
if [[ "$DEPLOY_MODE" == "test" ]]; then
  SITE_BASE="http://$DOMAIN"
  info "内网 HTTP 测试模式：访问地址将为 http://$DOMAIN（无证书）"
else
  SITE_BASE="https://$DOMAIN"
fi

DB_USERNAME="${DB_USERNAME:-john123456}"
if [[ -z "${DB_PASSWORD:-}" ]]; then
  [[ -t 0 ]] || die "缺少 DB_PASSWORD 环境变量"
  read -r -s -p "    数据库账号 $DB_USERNAME 的密码: " DB_PASSWORD; echo
  [[ -n "$DB_PASSWORD" ]] || die "数据库密码不能为空"
fi
if [[ -z "${REDIS_PASSWORD:-}" ]]; then
  [[ -t 0 ]] || die "缺少 REDIS_PASSWORD 环境变量"
  read -r -s -p "    Redis 密码: " REDIS_PASSWORD; echo
  [[ -n "$REDIS_PASSWORD" ]] || die "Redis 密码不能为空"
fi

# 检查部署产物
[[ -f "$PKG_DIR/app.jar" ]] || die "缺少 $PKG_DIR/app.jar（放入后端构建产物后重试）"
[[ -d "$PKG_DIR/dist" && -f "$PKG_DIR/dist/index.html" ]] || die "缺少 $PKG_DIR/dist/（放入前端构建产物后重试）"
[[ -d "$PKG_DIR/files" ]] || die "缺少 $PKG_DIR/files/（放入种子图片目录后重试）"

# ----------------------------------------------------------------
step "2/8 安装依赖（Java 17 / Nginx / curl）"
if [[ "${SKIP_DEPS:-0}" != "1" ]]; then
  if command -v apt-get >/dev/null 2>&1; then
    PKG_MGR="apt"
    apt-get update -qq
    install_deps() { DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@"; }
  elif command -v dnf >/dev/null 2>&1; then
    PKG_MGR="dnf"
    install_deps() { dnf install -y -q "$@"; }
  elif command -v yum >/dev/null 2>&1; then
    PKG_MGR="yum"
    install_deps() { yum install -y -q "$@"; }
  else
    die "未识别到 apt/dnf/yum，无法自动安装依赖（可 SKIP_DEPS=1 跳过）"
  fi

  java_ok=false
  if command -v java >/dev/null 2>&1; then
    v="$(java -version 2>&1 | head -1 | grep -oE '"[^"]+"' | tr -d '"')"
    major="${v%%.*}"
    [[ "$major" =~ ^[0-9]+$ && "$major" -ge 17 ]] && java_ok=true
  fi
  if ! $java_ok; then
    info "安装 OpenJDK 17..."
    case "$PKG_MGR" in
      apt) install_deps openjdk-17-jre-headless ;;
      dnf|yum) install_deps java-17-openjdk-headless ;;
    esac
  else
    info "检测到 Java $v，跳过安装"
  fi

  command -v nginx >/dev/null 2>&1 || { info "安装 nginx..."; install_deps nginx; }
  command -v curl  >/dev/null 2>&1 || { info "安装 curl...";  install_deps curl;  }

  if [[ "${INSTALL_DB_SERVICES:-0}" == "1" ]]; then
    info "安装本机 mysql-server 与 redis-server..."
    case "$PKG_MGR" in
      apt) install_deps mysql-server redis-server ;;
      dnf|yum) install_deps mysql-server redis ;;
    esac
    systemctl enable --now mysql redis 2>/dev/null || \
      systemctl enable --now mysql redis-server 2>/dev/null || \
      systemctl enable --now mysqld redis-server 2>/dev/null || \
      systemctl enable --now mysqld redis 2>/dev/null || true
  fi
else
  info "SKIP_DEPS=1，跳过依赖安装（请自行确保 java17/nginx/curl 可用）"
fi

# ----------------------------------------------------------------
step "3/8 创建运行用户与目录"
id -u "$APP_USER" >/dev/null 2>&1 || useradd -r -m -d "$APP_HOME" -s /usr/sbin/nologin "$APP_USER"
mkdir -p "$APP_HOME/film_comment_system/files" "$APP_HOME/backup" "$ENV_DIR"

step "4/8 部署应用产物"
install -o root -g root -m 0644 "$PKG_DIR/app.jar" "$APP_HOME/app.jar"
if [[ -d "$APP_HOME/dist" ]]; then rm -rf "$APP_HOME/dist"; fi
cp -a "$PKG_DIR/dist" "$APP_HOME/dist"
chown -R root:root "$APP_HOME/dist"
# 种子图片放入上传目录；注意文件量少时与现有文件并存（增量拷贝）
cp -an "$PKG_DIR/files/." "$APP_HOME/film_comment_system/files/"
# 上传目录归 john123456 用户所有（后端以 john123456 身份运行，需要写权限）
chown -R "$APP_USER":"$APP_USER" "$APP_HOME/film_comment_system"
info "app.jar -> $APP_HOME/app.jar"
info "dist    -> $APP_HOME/dist"
info "files   -> $APP_HOME/film_comment_system/files"

# ----------------------------------------------------------------
step "5/8 生成环境文件 $ENV_FILE"
if [[ -f "$ENV_FILE" ]]; then
  warn "$ENV_FILE 已存在，跳过（如需重建请先删除或改 ENV_FILE 变量）"
else
  # 逐行做字面量替换（比 sed 安全：密码含特殊字符也不会出错）
  while IFS= read -r line; do
    line=${line//<DB密码>/$DB_PASSWORD}
    line=${line//<Redis密码>/$REDIS_PASSWORD}
    line=${line//https:\/\/your-domain.com/$SITE_BASE}
    line=${line//your-domain.com/$DOMAIN}
    [[ "$line" == "DB_USERNAME="* ]] && line="DB_USERNAME=$DB_USERNAME"
    printf '%s\n' "$line"
  done < "$PKG_DIR/env.example" > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  info "环境文件已写入（chmod 600，包含凭据，勿提交/外泄）"
fi
# 内网 HTTP 模式：追加关闭 Cookie secure 标记。
# prod 配置默认 secure=true，浏览器在 HTTP 下会拒绝保存该 Cookie，
# 表现为「一登录就掉线」；公网 HTTPS 模式则必须保持 secure。
if [[ "$DEPLOY_MODE" == "test" ]] && ! grep -q '^SA_TOKEN_COOKIE_SECURE=' "$ENV_FILE"; then
  echo "SA_TOKEN_COOKIE_SECURE=false" >> "$ENV_FILE"
  info "测试模式：已追加 SA_TOKEN_COOKIE_SECURE=false（否则 HTTP 下登录无法保持）"
fi
# 校验占位符已全部替换
if grep -qE '<DB密码>|<Redis密码>|your-domain\.com' "$ENV_FILE"; then
  die "$ENV_FILE 中存在未替换的占位符，请检查"
fi

# ----------------------------------------------------------------
step "6/8 本机 Redis 密码兜底（使用本机 Redis 时）"
if command -v redis-cli >/dev/null 2>&1 \
   && redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -q PONG; then
  current="$(redis-cli -h 127.0.0.1 -p 6379 config get requirepass 2>/dev/null | tail -1)"
  if [[ -z "$current" ]]; then
    info "检测到本机 Redis 未配置密码，正在写入 requirepass..."
    if redis-cli -h 127.0.0.1 -p 6379 config set requirepass "$REDIS_PASSWORD" \
       && redis-cli -h 127.0.0.1 -p 6379 -a "$REDIS_PASSWORD" config rewrite 2>/dev/null; then
      info "Redis requirepass 已设置并持久化"
    else
      warn "自动设置 Redis 密码失败，请手工在 redis.conf 配置 requirepass 后重启"
    fi
  else
    info "本机 Redis 已配置密码，跳过"
  fi
else
  info "本机未运行 Redis，跳过（使用云 Redis 时请确保其已启用密码）"
fi

# ----------------------------------------------------------------
step "7/8 安装 systemd 服务"
cp -f "$PKG_DIR/film-comment.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "$UNIT" 2>/dev/null || warn "enable $UNIT 失败"
info "服务已安装：systemctl enable 已完成，初始化数据库后再 start"

# ----------------------------------------------------------------
step "8/8 配置 Nginx 反向代理（$DEPLOY_MODE 模式）"
NGINX_CONF_DIR="/etc/nginx/conf.d"
mkdir -p "$NGINX_CONF_DIR"
# api_proxy.conf 原样复制
# 共用反代参数片段必须以非 .conf 扩展名落盘：conf.d/*.conf 会被 Nginx 顶层自动加载，
# proxy_pass 在 http 上下文非法，会直接导致 nginx -t 失败（经典坑）
rm -f "$NGINX_CONF_DIR/api_proxy.conf"
cp -f "$PKG_DIR/api_proxy.conf" "$NGINX_CONF_DIR/api_proxy.inc"
# 按部署模式选站点模板：prod=HTTPS 公网 / test=纯 HTTP 内网，替换域名后落盘
if [[ "$DEPLOY_MODE" == "test" ]]; then
  NGINX_TPL="$PKG_DIR/nginx-http.conf"
else
  NGINX_TPL="$PKG_DIR/nginx.conf"
fi
{ while IFS= read -r line; do printf '%s\n' "${line//your-domain.com/$DOMAIN}"; done < "$NGINX_TPL"; } \
  > "$NGINX_CONF_DIR/film-comment.conf"
# Debian 系默认站点会占用 80 端口默认 host，禁用以免冲突
if [[ -f /etc/nginx/sites-enabled/default ]]; then
  rm -f /etc/nginx/sites-enabled/default
  info "已禁用 Nginx 默认站点（/etc/nginx/sites-enabled/default）"
fi

if [[ "$DEPLOY_MODE" == "test" ]]; then
  # 内网 HTTP：无证书要求，直接校验并生效
  if nginx -t 2>/tmp/nginx-test.err; then
    systemctl enable nginx >/dev/null 2>&1 || true
    systemctl reload nginx 2>/dev/null || systemctl start nginx
    info "Nginx 已生效（纯 HTTP，浏览器访问 http://$DOMAIN）"
  else
    warn "nginx -t 校验失败（见 /tmp/nginx-test.err），请检查配置"
  fi
else
  CERT_FULL="/etc/nginx/cert/fullchain.pem"
  CERT_KEY="/etc/nginx/cert/privkey.pem"
  if [[ -f "$CERT_FULL" && -f "$CERT_KEY" ]]; then
    if nginx -t 2>/tmp/nginx-test.err; then
      systemctl enable nginx >/dev/null 2>&1 || true
      systemctl reload nginx 2>/dev/null || systemctl start nginx
      info "Nginx 配置已生效"
    else
      warn "nginx -t 校验失败（见 /tmp/nginx-test.err），请检查配置与证书"
    fi
  else
    warn "未检测到 SSL 证书 $CERT_FULL / $CERT_KEY"
    warn "已写入 Nginx 配置但未 reload；补证书后执行：nginx -t && systemctl reload nginx"
  fi
fi

# ----------------------------------------------------------------
echo
echo -e "${GREEN}${BOLD}安装完成。${RESET}"
echo "  访问:    $SITE_BASE"
echo "  产物:    $APP_HOME/app.jar + $APP_HOME/dist + $APP_HOME/film_comment_system/files"
echo "  环境:    $ENV_FILE"
echo "  服务:    systemctl [start|stop|status] $UNIT；日志 journalctl -u $UNIT -f"
echo
if [[ "$DEPLOY_MODE" == "test" ]]; then
  echo "下一步（内网测试模式）："
  echo "  1) 初始化/导入数据库：运行同一目录下的 init-db.sh"
  echo "  2) 如需从其他机器访问，放行 80 端口：ufw allow 80"
  echo "  3) 重置演示账号口令（见 init-db.sh 结束时的安全清单）"
else
  echo "下一步（公网生产模式）："
  echo "  1) 初始化/导入数据库：运行同一目录下的 init-db.sh"
  echo "     （云 MySQL/Redis 请先按 env 里的连接参数建好库和账号）"
  echo "  2) 放行防火墙 80/443 端口："
  echo "       Ubuntu: ufw allow 'Nginx Full'"
  echo "       CentOS: firewall-cmd --permanent --add-service=http --add-service=https && firewall-cmd --reload"
  echo "  3) 上线后重置演示账号密码（见 init-db.sh 结束时的安全清单）"
fi
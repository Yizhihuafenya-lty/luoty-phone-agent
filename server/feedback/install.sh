#!/usr/bin/env bash
# 在远程 Linux 上安装反馈服务；参数 1 为 Cloudflare Tunnel token（可选）
set -euo pipefail

BASE=/opt/luoty-feedback
CF_TOKEN="${1:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y python3 curl
fi
if ! command -v curl >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y curl
fi

if ! id -u luoty-feedback >/dev/null 2>&1; then
  useradd --system --home "$BASE" --shell /usr/sbin/nologin luoty-feedback
fi

mkdir -p "$BASE"
install -m 750 -o root -g root "$HERE/app.py" "$BASE/app.py"
install -m 644 -o root -g root "$HERE/luoty-feedback.service" /etc/systemd/system/luoty-feedback.service

if [ ! -s "$BASE/admin_token" ]; then
  python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$BASE/admin_token"
fi
chown -R luoty-feedback:luoty-feedback "$BASE"
chmod 600 "$BASE/admin_token"

systemctl daemon-reload
systemctl enable --now luoty-feedback
sleep 1
python3 - <<'PY'
import json, urllib.request
body = json.loads(urllib.request.urlopen("http://127.0.0.1:18080/health", timeout=5).read())
print("HEALTH", json.dumps(body, ensure_ascii=False))
PY

if [ -n "$CF_TOKEN" ]; then
  if ! command -v cloudflared >/dev/null 2>&1; then
    arch="$(uname -m)"
    case "$arch" in
      x86_64) cf_arch=amd64 ;;
      aarch64|arm64) cf_arch=arm64 ;;
      *) cf_arch=amd64 ;;
    esac
    curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${cf_arch}" \
      -o /usr/local/bin/cloudflared
    chmod 755 /usr/local/bin/cloudflared
  fi
  cloudflared service install "$CF_TOKEN"
  systemctl enable --now cloudflared
  sleep 4
  systemctl is-active cloudflared || true
  systemctl --no-pager --full status cloudflared | head -20 || true
fi

echo "ADMIN_TOKEN_FILE=$BASE/admin_token"
systemctl --no-pager --full status luoty-feedback | head -12 || true

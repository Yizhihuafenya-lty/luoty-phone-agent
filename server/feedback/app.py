#!/usr/bin/env python3
"""LuoTy Agent 反馈收集服务。

- 仅监听 127.0.0.1，由 Cloudflare Tunnel 转发
- SQLite 存储，保留 365 天
- 每 IP 限速（默认 8 次/分钟、80 次/天）
- 数据库总量上限 1 GiB，超限自动淘汰最旧数据
- 管理接口用 Bearer Token 保护
"""
import hmac
import json
import os
import sqlite3
import sys
import threading
import time
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE = os.environ.get("LUOTY_BASE", "/opt/luoty-feedback")
DB_PATH = os.environ.get("LUOTY_DB", os.path.join(BASE, "feedback.db"))
TOKEN_FILE = os.environ.get("LUOTY_TOKEN_FILE", os.path.join(BASE, "admin_token"))
BIND = os.environ.get("LUOTY_BIND", "127.0.0.1")
PORT = int(os.environ.get("LUOTY_PORT", "18080"))
RETENTION_DAYS = int(os.environ.get("LUOTY_RETENTION_DAYS", "365"))
MAX_DB_BYTES = int(float(os.environ.get("LUOTY_MAX_DB_GB", "1")) * 1024 ** 3)
RATE_PER_MIN = int(os.environ.get("LUOTY_RATE_PER_MIN", "8"))
RATE_PER_DAY = int(os.environ.get("LUOTY_RATE_PER_DAY", "80"))
MAX_BODY = 64 * 1024

db_lock = threading.Lock()
minute_hits = defaultdict(deque)
day_hits = defaultdict(lambda: [0, 0])
rate_lock = threading.Lock()


def read_token():
    try:
        with open(TOKEN_FILE, "r", encoding="utf-8") as handle:
            return handle.read().strip()
    except OSError:
        return ""


ADMIN_TOKEN = read_token()


def db():
    connection = sqlite3.connect(DB_PATH, timeout=10)
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("PRAGMA synchronous=NORMAL")
    return connection


def init_db():
    os.makedirs(BASE, exist_ok=True)
    with db_lock, db() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                ip TEXT NOT NULL,
                message TEXT NOT NULL,
                contact TEXT,
                app_version TEXT,
                device TEXT,
                diagnostics TEXT,
                ua TEXT
            )
            """
        )
        connection.execute("CREATE INDEX IF NOT EXISTS idx_feedback_created ON feedback(created_at)")
        connection.commit()


def purge(days=None):
    days = RETENTION_DAYS if days is None else days
    cutoff = int(time.time()) - days * 86400
    with db_lock, db() as connection:
        deleted = connection.execute("DELETE FROM feedback WHERE created_at < ?", (cutoff,)).rowcount
        connection.commit()
        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    return deleted


def db_size():
    total = 0
    for suffix in ("", "-wal", "-shm"):
        try:
            total += os.path.getsize(DB_PATH + suffix)
        except OSError:
            pass
    return total


def prune_for_space():
    """超过 1 GiB 时从最旧的开始删，直到回落到 90% 以下。"""
    if db_size() <= MAX_DB_BYTES:
        return True
    target = int(MAX_DB_BYTES * 0.9)
    with db_lock, db() as connection:
        while db_size() > target:
            dropped = connection.execute(
                "DELETE FROM feedback WHERE id IN (SELECT id FROM feedback ORDER BY id ASC LIMIT 200)"
            ).rowcount
            connection.commit()
            connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            if dropped == 0:
                break
    return db_size() <= MAX_DB_BYTES


def cleanup_loop():
    while True:
        try:
            purge()
            prune_for_space()
        except Exception as error:  # noqa: BLE001
            print(f"cleanup error: {error}", flush=True)
        time.sleep(6 * 3600)


def rate_limited(ip):
    now = time.time()
    today = int(now // 86400)
    with rate_lock:
        per_minute = minute_hits[ip]
        while per_minute and now - per_minute[0] > 60:
            per_minute.popleft()
        day = day_hits[ip]
        if day[1] != today:
            day[0] = 0
            day[1] = today
        if len(per_minute) >= RATE_PER_MIN or day[0] >= RATE_PER_DAY:
            return True
        per_minute.append(now)
        day[0] += 1
        return False


class Handler(BaseHTTPRequestHandler):
    server_version = "luoty-feedback"
    sys_version = ""

    def log_message(self, fmt, *args):  # noqa: A003
        print(f"{self.address_string()} - {fmt % args}", flush=True)

    def _json(self, code, payload, extra_headers=None):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def _client_ip(self):
        forwarded = self.headers.get("CF-Connecting-IP")
        if forwarded:
            return forwarded.strip()
        xff = self.headers.get("X-Forwarded-For", "")
        if xff:
            return xff.split(",")[0].strip()
        return self.client_address[0]

    def _authorized(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return False
        return hmac.compare_digest(header[7:].strip(), ADMIN_TOKEN)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_BODY:
            return None
        return self.rfile.read(length)

    def do_GET(self):  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            with db_lock, db() as connection:
                count, oldest = connection.execute(
                    "SELECT COUNT(*), MIN(created_at) FROM feedback"
                ).fetchone()
            self._json(
                200,
                {
                    "ok": True,
                    "count": count,
                    "oldest": oldest,
                    "db_bytes": db_size(),
                    "limit_bytes": MAX_DB_BYTES,
                    "retention_days": RETENTION_DAYS,
                },
            )
            return
        if parsed.path == "/admin/feedback":
            if not self._authorized():
                self._json(401, {"error": "unauthorized"})
                return
            query = parse_qs(parsed.query)
            limit = min(int(query.get("limit", ["50"])[0]), 200)
            before = int(query.get("before", ["0"])[0] or 0)
            with db_lock, db() as connection:
                if before > 0:
                    rows = connection.execute(
                        "SELECT id, created_at, ip, message, contact, app_version, device, diagnostics "
                        "FROM feedback WHERE id < ? ORDER BY id DESC LIMIT ?",
                        (before, limit),
                    ).fetchall()
                else:
                    rows = connection.execute(
                        "SELECT id, created_at, ip, message, contact, app_version, device, diagnostics "
                        "FROM feedback ORDER BY id DESC LIMIT ?",
                        (limit,),
                    ).fetchall()
            items = [
                {
                    "id": row[0],
                    "created_at": row[1],
                    "ip": row[2],
                    "message": row[3],
                    "contact": row[4],
                    "app_version": row[5],
                    "device": row[6],
                    "diagnostics": row[7],
                }
                for row in rows
            ]
            self._json(200, {"items": items})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path != "/feedback":
            self._json(404, {"error": "not found"})
            return
        ip = self._client_ip()
        if rate_limited(ip):
            self._json(429, {"error": "too many requests"}, {"Retry-After": "60"})
            return
        raw = self._read_body()
        if raw is None:
            self._json(413, {"error": "body too large or empty"})
            return
        try:
            payload = json.loads(raw.decode("utf-8"))
        except Exception:  # noqa: BLE001
            self._json(400, {"error": "invalid json"})
            return
        message = str(payload.get("message", "")).strip()
        if not message:
            self._json(400, {"error": "message required"})
            return
        message = message[:4000]
        contact = str(payload.get("contact", ""))[:200]
        app_version = str(payload.get("appVersion", ""))[:80]
        device = str(payload.get("device", ""))[:200]
        diagnostics = str(payload.get("diagnostics", ""))[:20000]
        ua = self.headers.get("User-Agent", "")[:300]
        if not prune_for_space():
            self._json(507, {"error": "storage full"})
            return
        with db_lock, db() as connection:
            cursor = connection.execute(
                "INSERT INTO feedback(created_at, ip, message, contact, app_version, device, diagnostics, ua) "
                "VALUES(?,?,?,?,?,?,?,?)",
                (int(time.time()), ip, message, contact, app_version, device, diagnostics, ua),
            )
            connection.commit()
            feedback_id = cursor.lastrowid
        self._json(200, {"ok": True, "id": feedback_id})

    def do_DELETE(self):  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path != "/admin/feedback":
            self._json(404, {"error": "not found"})
            return
        if not self._authorized():
            self._json(401, {"error": "unauthorized"})
            return
        feedback_id = int(parse_qs(parsed.query).get("id", ["0"])[0] or 0)
        with db_lock, db() as connection:
            deleted = connection.execute("DELETE FROM feedback WHERE id = ?", (feedback_id,)).rowcount
            connection.commit()
        self._json(200, {"deleted": deleted})


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "purge":
        init_db()
        print(f"purged {purge()} rows")
        return
    init_db()
    purge()
    prune_for_space()
    threading.Thread(target=cleanup_loop, daemon=True).start()
    server = ThreadingHTTPServer((BIND, PORT), Handler)
    print(f"luoty-feedback listening on {BIND}:{PORT}, retention={RETENTION_DAYS}d, cap={MAX_DB_BYTES}B", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

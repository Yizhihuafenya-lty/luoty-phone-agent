# LuoTY 反馈服务（服务器端）

一个零依赖的反馈收集服务：Python 3 标准库 + SQLite，部署在 Linux 上，
通过 Cloudflare Tunnel 暴露，不直接监听公网。

## 能力

- `POST /feedback`：`{message, contact?, appVersion?, device?, diagnostics?}`，body ≤ 64 KB
- `GET /health`：健康检查（不含敏感数据）
- `GET /admin/feedback?limit=50&before=<id>`：Bearer Token 读取（最多 200 条/页）
- `DELETE /admin/feedback?id=<id>`：删除单条
- 保留期 365 天，每 6 小时清理一次
- 每 IP 限速：8 次/分钟、80 次/天（返回 429 + Retry-After）
- 数据库上限 1 GiB，超限按最旧数据淘汰到 90% 以下，仍超限返回 507
- 绑定 127.0.0.1，仅允许 Cloudflare Tunnel 转发进来

## 安装

```bash
# 在服务器上（root）
bash install.sh '<cloudflared-token>'
```

安装脚本会：

1. 创建系统用户 `luoty-feedback`，目录 `/opt/luoty-feedback`
2. 安装并启动 `luoty-feedback.service`（systemd 加固：只读系统盘、私有 /tmp、内存上限）
3. 生成管理 Token 到 `/opt/luoty-feedback/admin_token`（600 权限）
4. 下载 cloudflared 并 `cloudflared service install <token>`，启动隧道

## Cloudflare 侧

在 Zero Trust → Networks → Tunnels 里给这个隧道添加 Public Hostname，指向
`http://localhost:18080`。绑定后 `https://<你的域名>/health` 应返回 `{"ok": true, ...}`。

## 运维

```bash
systemctl status luoty-feedback cloudflared
journalctl -u luoty-feedback -n 100
cat /opt/luoty-feedback/admin_token
curl -H "Authorization: Bearer <token>" https://<域名>/admin/feedback?limit=20
```

## 安全

- 密码/私钥只用于 SSH，不写入服务端
- Token 用 `hmac.compare_digest` 比较，避免时序攻击
- 所有 SQL 参数化；不记录请求正文到日志
- 1 GiB 上限避免磁盘被写满；429/507 都有明确响应

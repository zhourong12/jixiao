# Linux 部署说明（jixiao2）

本文说明在 Linux 服务器上以 **生产模式** 部署本仓库（NestJS 后端 + Vite 构建的前端静态资源，由同一进程提供）。

开发环境本地启动：

```bash
npm run dev
```

- 前端开发服务器：<http://127.0.0.1:8080>（通过 Vite 代理 `/api`、`/auth` 到后端）
- 后端 API：<http://127.0.0.1:3000/api>

---

## 命令集（可复制）

以下默认：**项目目录** `APP=/opt/jixiao2`，**MySQL** 主机 `127.0.0.1`、库名 `jixiao2`、用户 `jixiao`；请按环境替换。

### 开发机（本仓库根目录）

```bash
npm install          # 或 npm ci（有 package-lock 时推荐）
npm run dev          # 前端 :8080 + 后端 :3000
```

### 生产：首次部署（源码目录已存在）

```bash
export APP=/opt/jixiao2
cd "$APP"

# 依赖
npm ci

# 数据库（空库或接受 initial.sql 的 DROP，见脚本头注释）
mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE IF NOT EXISTS jixiao2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 127.0.0.1 -u jixiao -p jixiao2 < server/database/sql/initial.sql

# 环境变量：在项目根准备 .env（chmod 600 .env）
# 至少：MYSQL_*、SESSION_JWT_SECRET、SERVER_HOST=0.0.0.0、SERVER_PORT=3000

# 构建 + 启动（前台试跑）
npm run build:prod
NODE_ENV=production node dist/server/main.js
```

### 生产：更新发版（同目录）

```bash
export APP=/opt/jixiao2
cd "$APP"
git pull
npm ci
npm run build:prod
# 若使用 systemd：
sudo systemctl restart jixiao2
```

### 生产：systemd 一次到位

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now jixiao2
sudo systemctl status jixiao2 --no-pager
sudo journalctl -u jixiao2 -f
```

### 可选：检查服务是否起来

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:3000/
# 或按你配置的端口访问首页 / API
```

### 可选：`.env` 权限

```bash
chmod 600 /opt/jixiao2/.env
```

### Docker（推荐：宿主机 glibc 过旧无法直接跑 Node 22 时）

项目根目录提供 `Dockerfile`、`.dockerignore`。

```bash
cd /path/to/jixiao2
docker build -t jixiao2:latest .

# 与本地 .env 一致（勿把 .env 打进镜像；用挂载或 --env-file）
docker run -d --name jixiao2 -p 3000:3000 --env-file .env --restart unless-stopped jixiao2:latest
```

- 容器内已默认 `SERVER_HOST=0.0.0.0`、`SERVER_PORT=3000`。
- MySQL 在**宿主机**时：`MYSQL_HOST` 可填 `host.docker.internal`（Docker 20.10+ Linux 需加 `--add-host=host.docker.internal:host-gateway`），或填宿主机内网 IP。
- MySQL 在**另一容器**时：用同一 Docker 网络，把 `MYSQL_HOST` 写成数据库服务名。

若运行时阶段 `npm ci` 因原生模块编译失败，可把 `Dockerfile` 最后一阶段的 `node:22-bookworm-slim` 改成 `node:22-bookworm` 再构建。

---

## 1. 环境要求

| 依赖 | 版本建议 |
|------|----------|
| Node.js | **≥ 22**（见 `package.json` 的 `engines`） |
| npm | **≥ 10** |
| MySQL | 5.7+，InnoDB，`utf8mb4` |

可选：Nginx 作为反向代理与 TLS 终结；systemd 托管 Node 进程。

---

## 2. 数据库

1. 创建空库，例如：

   ```bash
   mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE jixiao2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   ```

2. 在**空库或接受全量重建**的环境执行初始化脚本（会 `DROP` 部分业务表，详见脚本头注释）：

   ```bash
   mysql -h 127.0.0.1 -u YOUR_USER -p jixiao2 < server/database/sql/initial.sql
   ```

3. 若仓库中另有 `server/database/sql/*.sql` 增量脚本，按项目发布说明或迁移顺序在初始化之后按需执行。

---

## 3. 环境变量

在**项目根目录**放置 `.env`（与开发一致，生产请勿提交密钥）。常用项：

| 变量 | 说明 |
|------|------|
| `MYSQL_HOST` | MySQL 主机 |
| `MYSQL_PORT` | 端口，默认 `3306` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 账号密码 |
| `MYSQL_DATABASE` | 库名 |
| `SESSION_JWT_SECRET` | **生产必填**，会话 JWT 签名密钥（勿用默认值） |
| `SERVER_HOST` | 监听地址；对外服务建议 `0.0.0.0` |
| `SERVER_PORT` | 监听端口，默认 `3000` |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET` | 飞书应用（若使用飞书能力） |
| `NODE_ENV` | 生产设为 `production`（`npm run start` 已内置） |

`npm run start` 会加载编译后的 `dist/server/main.js`，视图与静态页目录为 `dist/client`（见 `server/main.ts`）。

---

## 4. 构建与启动

```bash
cd /path/to/jixiao2
npm ci          # 或 npm install
npm run build:prod   # 等同 build:server + build:client
```

生产启动：

```bash
export NODE_ENV=production
npm run start
```

或直接：

```bash
NODE_ENV=production node dist/server/main.js
```

确认日志中出现服务监听信息；浏览器访问 `http://SERVER_HOST:SERVER_PORT/`（以实际配置为准）。

---

## 5. systemd 示例（可选）

`/etc/systemd/system/jixiao2.service`：

```ini
[Unit]
Description=jixiao2 fullstack
After=network.target mysql.service

[Service]
Type=simple
WorkingDirectory=/opt/jixiao2
Environment=NODE_ENV=production
# 或使用 EnvironmentFile=/opt/jixiao2/.env
ExecStart=/usr/bin/node dist/server/main.js
Restart=on-failure
RestartSec=5
User=www-data
Group=www-data

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now jixiao2
sudo systemctl status jixiao2
```

部署新版本前在 `WorkingDirectory` 下执行 `git pull`、`npm ci`、`npm run build:prod`，再 `sudo systemctl restart jixiao2`。

---

## 6. Nginx 反向代理（可选）

将对外 443 转发到本机 `127.0.0.1:3000`，并配置 `proxy_set_header Host / X-Forwarded-For / X-Forwarded-Proto` 等，以便 HTTPS 与真实客户端 IP。具体 `server` 块按贵司证书与域名调整。

---

## 7. 防火墙与安全

- 仅对 Nginx 或负载均衡开放 80/443；Node 监听 `127.0.0.1:3000` 或内网段。
- 定期更新系统与本依赖；`.env` 权限建议 `chmod 600`。

---

## 8. 常见问题

- **启动报数据库连接失败**：检查 MySQL 地址、账号、库名及远程访问授权。
- **生产 Cookie / 登录异常**：确认 `SESSION_JWT_SECRET` 已设置，且 HTTPS 场景下 Cookie `secure` 等与域名一致（见 `local-auth.controller` 等）。
- **静态页 404**：确认已执行 `npm run build:client`，且存在 `dist/client/index.html`。

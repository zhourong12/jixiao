# Ubuntu Docker 部署教程（Vue 前端 + Java 后端）

本仓库分为两个可独立构建的目录：

| 目录 | 技术栈 | 产物 |
| --- | --- | --- |
| `client-vue/` | Vue 3 + Vite | Nginx 静态站点，监听 80 |
| `server-java/` | Java 8 + Spring Boot 2.7 | 可执行 JAR，监听 8081 |

根目录 `docker-compose.yml` 用于在 Ubuntu 上一键拉起 MySQL、Java API、Vue 前端。

## 1. 服务器准备

建议 Ubuntu 22.04 / 24.04，至少 2 核 CPU、2 GB 内存。首次部署前安装 Docker 与 Compose 插件：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
newgrp docker
docker --version
docker compose version
```

## 2. 获取代码

```bash
cd /opt
git clone <你的仓库地址> jixiao2
cd jixiao2
```

若通过压缩包上传，解压后进入包含 `client-vue/`、`server-java/`、`docker-compose.yml` 的目录即可。

## 3. 一键部署（推荐）

### 3.1 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，至少修改：

- `MYSQL_ROOT_PASSWORD`：MySQL root 密码
- `SESSION_JWT_SECRET`：登录会话密钥，使用足够长的随机字符串

首次启动时，MySQL 会执行 `server-java/database/sql/initial.sql` 初始化库表。已有库且 `menu` / `role_menu` 不完整时，在 MySQL 中执行 `server-java/database/sql/sync-menu-table.sql`。

### 3.2 构建并启动

```bash
docker compose up -d --build
docker compose ps
```

访问：

- 前端：`http://<服务器IP>/`
- Java API：`http://<服务器IP>:8081/actuator/health`

前端容器内 Nginx 将 `/api/`、`/auth/` 反代到 Compose 服务 `jixiao2-api:8081`，浏览器与 API 同源，Cookie 会话可正常工作。构建时 `VITE_API_BASE_URL` 留空即可。

### 3.3 常用运维命令

```bash
docker compose logs -f jixiao2-api
docker compose logs -f jixiao2-web
docker compose restart jixiao2-api
docker compose down
docker compose down -v
```

`docker compose down -v` 会删除 MySQL 数据卷，仅在确认可清空数据时使用。

## 4. 打成 JAR + dist 再上传（推荐发版）

先在开发机或 CI 打包，只把产物传到服务器，不在服务器编译源码。

### 4.1 本地打包

在项目根目录执行：

```bash
npm install
npm install --prefix client-vue
npm run package
```

生成目录：

- 前端静态资源：`dist/client/`
- 后端可执行包：`dist/server/server-0.0.1-SNAPSHOT.jar`

也可分步执行 `npm run build:server-java` 与 `npm run build:client`；JAR 默认仍在 `server-java/target/`，`npm run package` 会再复制到 `dist/server/`。

### 4.2 上传到服务器

将 `dist/client/` 与 `dist/server/server-0.0.1-SNAPSHOT.jar` 传到服务器，例如 `/opt/jixiao2/`。可用 `scp`、`rsync` 或 SFTP。

### 4.3 服务器运行

后端（需 Java 8+，并配置数据库与会话密钥）：

```bash
export JIXIAO2_JDBC_URL='jdbc:mysql://127.0.0.1:3306/jixiao2?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
export JIXIAO2_JDBC_USER=root
export JIXIAO2_JDBC_PASSWORD='<password>'
export SESSION_JWT_SECRET='<secret>'
java -jar /opt/jixiao2/dist/server/server-0.0.1-SNAPSHOT.jar
```

前端用 Nginx 托管 `dist/client`，并把 `/api/`、`/auth/` 反代到本机 `8081`（可参考 `client-vue/nginx.conf`）。

### 4.4 用已打包产物起 Docker

先完成 4.1，再在项目根目录：

```bash
docker compose -f docker-compose.release.yml up -d --build
```

该编排只复制 `dist/` 进镜像，不在容器内跑 Maven / Node 构建。数据库需自行准备，并在 `.env` 中配置 `JIXIAO2_JDBC_*` 与 `SESSION_JWT_SECRET`。

## 5. 分别构建镜像

### 5.1 Vue 前端（`client-vue/Dockerfile`）

多阶段构建：`node:20-alpine` 编译，`nginx:alpine` 运行。

```bash
cd client-vue
docker build -t jixiao2-web .
docker run -d --name jixiao2-web -p 80:80 jixiao2-web
```

与 Java 同机 Docker 网络、且需由 Nginx 转发 API 时：

```bash
docker network create jixiao2-net
docker run -d --name jixiao2-api --network jixiao2-net -p 8081:8081 \
  -e JIXIAO2_JDBC_URL='jdbc:mysql://<mysql-host>:3306/jixiao2?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true' \
  -e JIXIAO2_JDBC_USER=root \
  -e JIXIAO2_JDBC_PASSWORD='<password>' \
  -e SESSION_JWT_SECRET='<secret>' \
  jixiao2-api
docker run -d --name jixiao2-web --network jixiao2-net -p 80:80 jixiao2-web
```

`nginx.conf` 中上游主机名为 `jixiao2-api`，容器名需一致，或修改 `client-vue/nginx.conf` 后重新构建。

API 在另一域名或端口、不走 Nginx 反代时，构建时传入 `VITE_API_BASE_URL`，并配置后端 CORS（`JIXIAO2_CORS_ALLOWED_ORIGIN_PATTERNS`）：

```bash
docker build --build-arg VITE_API_BASE_URL=http://<api-host>:8081 -t jixiao2-web .
```

### 5.2 Java 后端（`server-java/Dockerfile`）

多阶段构建：`maven:3.9-eclipse-temurin-8` 编译，`eclipse-temurin:8-jre-jammy` 运行。

```bash
cd server-java
docker build -t jixiao2-api .
docker run -d --name jixiao2-api -p 8081:8081 \
  -e JIXIAO2_JDBC_URL='jdbc:mysql://<mysql-host>:3306/jixiao2?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true' \
  -e JIXIAO2_JDBC_USER=root \
  -e JIXIAO2_JDBC_PASSWORD='<password>' \
  -e SESSION_JWT_SECRET='<secret>' \
  jixiao2-api
```

主要环境变量见 `server-java/.env.example` 与 `server-java/src/main/resources/application.yml`。

## 6. 轻量说明

- 前端运行镜像为 `nginx:alpine`，不含 Node 运行时。
- 后端运行镜像为 JRE，不含 Maven 与源码；JVM 默认 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`。
- 构建上下文通过 `.dockerignore` 排除 `node_modules`、`target` 等，缩短上传与构建时间。
- 小内存机器可将 Compose 中 MySQL 改为外部托管实例，只保留 `jixiao2-api` 与 `jixiao2-web`，并相应修改 `JIXIAO2_JDBC_URL`。

## 7. 常见问题

**前端能打开，接口 502**  
确认 `jixiao2-api` 已启动，且与 `jixiao2-web` 在同一 Docker 网络；单独 `docker run` 前端时容器名须为 `jixiao2-api`，或改 `nginx.conf` 后重建镜像。

**后端启动失败**  
查看 `docker compose logs jixiao2-api`，核对 JDBC 地址、账号密码、`SESSION_JWT_SECRET` 是否已设置。

**跨域或登录 Cookie 异常**  
优先使用 Compose 同源反代；若前后端不同源，设置 `JIXIAO2_CORS_ALLOWED_ORIGIN_PATTERNS` 与 Cookie 相关变量（`JIXIAO2_SESSION_COOKIE_*`）。

**端口冲突**  
修改 `docker-compose.yml` 中 `ports` 映射，例如 `"8080:80"`、`"18081:8081"`。

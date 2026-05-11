# jixiao2：多阶段构建，运行时基于 glibc 较新的 Debian，避免老 CentOS 上 Node 22 无法运行的问题
# 构建：DOCKER_BUILDKIT=1 docker build -t jixiao2:latest .
# 运行：docker run -d -p 3000:3000 --env-file .env --name jixiao2 jixiao2:latest
# MySQL 需可达：同一 compose 网络内写服务名，或宿主机写 host.docker.internal / 宿主机 IP
#
# 基础镜像：仍失败时用 build-arg 换成你可 pull 的完整镜像名。
#   docker build --build-arg NODE_BUILDER=... --build-arg NODE_RUNNER=... -t jixiao2:latest .
#
# 加速说明：不在 runner 再 npm ci（避免依赖装两遍）；BuildKit 挂载 npm 缓存；海外构建可：
#   docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org -t jixiao2:latest .

ARG NODE_BUILDER=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/node:22-bookworm
ARG NODE_RUNNER=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/node:22-bookworm-slim

FROM ${NODE_BUILDER} AS builder
# FROM 会清空其前的 ARG；npm 源必须在当前 stage 内再声明一次，否则 RUN 里变量为空易出错
ARG NPM_REGISTRY=https://registry.npmmirror.com
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --ignore-scripts --registry="${NPM_REGISTRY}"

COPY . .
# 不用 npm run build:prod：避免依赖 cross-env 在部分镜像/ shell 下解析失败（exit 1）；Linux 下直接设 NODE_ENV 即可
ENV NODE_OPTIONS=--max-old-space-size=8192
RUN NODE_ENV=production npx nest build \
  && NODE_ENV=production npx vite build --config vite.config.ts \
  && npm prune --omit=dev

FROM ${NODE_RUNNER} AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV SERVER_HOST=0.0.0.0
ENV SERVER_PORT=3000
ENV FORCE_AUTHN_INNERAPI_DOMAIN=http://127.0.0.1:3000

COPY --from=builder --chown=node:node /app/package.json /app/package-lock.json ./
COPY --from=builder --chown=node:node /app/node_modules ./node_modules
COPY --from=builder --chown=node:node /app/dist ./dist

USER node

EXPOSE 3000

CMD ["node", "dist/server/main.js"]

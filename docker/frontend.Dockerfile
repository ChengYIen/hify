FROM node:20-alpine AS build

WORKDIR /workspace

# 先复制依赖清单，源码变更时可以复用 npm ci 这一层。
COPY hify-web/package.json hify-web/package-lock.json ./hify-web/
RUN cd hify-web && npm ci --no-audit --no-fund

COPY hify-web ./hify-web
RUN cd hify-web && npm run build

FROM nginx:1.27-alpine
COPY docker/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/hify-web/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O - http://127.0.0.1/health >/dev/null || exit 1

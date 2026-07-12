# syntax=docker.m.daocloud.io/docker/dockerfile:1.7

ARG NODE_IMAGE=node:20.18.0-alpine3.20
ARG NGINX_IMAGE=nginx:1.27.2-alpine
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS build

RUN corepack enable \
    && corepack prepare pnpm@9.12.0 --activate

WORKDIR /workspace

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . ./
RUN pnpm build

FROM ${NGINX_IMAGE}

RUN rm -f /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 18080

HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=5 \
    CMD wget -qO- http://127.0.0.1:18080/healthz || exit 1

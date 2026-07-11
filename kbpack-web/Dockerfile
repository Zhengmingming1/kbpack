FROM node:20.18.0-alpine3.20 AS build

RUN corepack enable \
    && corepack prepare pnpm@9.12.0 --activate

WORKDIR /workspace

COPY package.json ./
RUN pnpm install --no-frozen-lockfile

COPY . ./
RUN pnpm build

FROM nginx:1.27.2-alpine

RUN rm -f /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 18080

HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=5 \
    CMD wget -qO- http://localhost:18080/healthz || exit 1

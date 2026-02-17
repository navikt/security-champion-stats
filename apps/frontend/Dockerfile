FROM node:24 AS builder

WORKDIR /app

ENV PNPM_HOME="/pnpm"
ENV PATH="${PNPM_HOME}:${PATH}"

RUN corepack enable pnpm

RUN --mount=type=secret,id=NODE_AUTH_TOKEN sh -c \
    'pnpm config set //npm.pkg.github.com/:_authToken=$(cat /run/secrets/NODE_AUTH_TOKEN)'
RUN pnpm config set @navikt:registry=https://npm.pkg.github.com

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY next.config.ts tsconfig.json ./
COPY src/ ./src/

RUN pnpm run build

FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:24-slim
WORKDIR /app

ENV NODE_ENV=production \
    PORT=3000 \
    NEXT_TELEMETRY_DISABLED=1

COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000

CMD ["server.js"]

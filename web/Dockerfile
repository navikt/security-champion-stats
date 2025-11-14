FROM cgr.dev/chainguard/node:latest-dev AS build
USER root
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY .. .
RUN npm run dev

# Production stage
FROM cgr.dev/chainguard/nginx AS production
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
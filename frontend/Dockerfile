# Build the SPA
# Node 22.5+ is required: pnpm 11 uses the built-in `node:sqlite` module.
FROM node:24-alpine AS build
RUN npm install -g pnpm@11.28.5
WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# Serve static assets + reverse-proxy to backend via nginx
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

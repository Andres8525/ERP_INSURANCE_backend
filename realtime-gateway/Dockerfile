FROM node:22.18.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci
COPY tsconfig.json tsconfig.build.json ./
COPY src/ src/
RUN npm run build && npm prune --omit=dev

FROM node:22.18.0-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY package.json ./
USER node
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:${PORT:-8090}/health || exit 1
CMD ["node", "--enable-source-maps", "dist/index.js"]

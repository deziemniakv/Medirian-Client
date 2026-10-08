FROM node:24-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
# behind the HTTPS reverse proxy (backend/deploy): listen on the container network, trust the proxy
ENV NODE_ENV=production MEDIRIAN_SERVICES_PORT=8080 MEDIRIAN_SERVICES_HOST=0.0.0.0 MEDIRIAN_SERVICES_DATA_DIR=/data MEDIRIAN_SERVICES_TRUST_PROXY=1
# the data volume belongs to the unprivileged user the service runs as
RUN mkdir -p /data && chown node:node /data
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "src/index.mjs"]

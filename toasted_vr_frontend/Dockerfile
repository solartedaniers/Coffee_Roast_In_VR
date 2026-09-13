FROM node:20-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY public public
COPY src src

# Placeholder baked into the build output in place of the real API URL.
# entrypoint.sh replaces it at container startup, so the same image works
# for any REACT_APP_API_BASE_URL without rebuilding.
ENV REACT_APP_API_BASE_URL=__RUNTIME_API_BASE_URL__
RUN npm run build

FROM nginx:alpine

COPY --from=build /workspace/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/entrypoint.sh"]

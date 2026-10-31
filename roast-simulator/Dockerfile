# Se construye desde la raíz del repo: la imagen necesita el dominio del tueste de la web, que el
# simulador importa con la misma ruta relativa que fuera de Docker (../../toasted_vr_frontend/...).
# Dockerfile.dockerignore deja entrar solo esas dos carpetas.
FROM node:24-alpine

WORKDIR /app

COPY toasted_vr_frontend/src/domain/roasting toasted_vr_frontend/src/domain/roasting
COPY roast-simulator/package.json roast-simulator/package.json
COPY roast-simulator/src roast-simulator/src

WORKDIR /app/roast-simulator
USER node

EXPOSE 8083

CMD ["node", "--import", "./src/domainHooks.js", "src/main.js"]

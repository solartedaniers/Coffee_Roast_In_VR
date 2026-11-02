# Simulador Inmersivo VR del Proceso del Café (Toasted VR)

## Descripción general

En este repositorio tengo todo el ecosistema del simulador de tueste de café:

| Carpeta | Qué es |
|---|---|
| `Toasted_VR/` | Backend en Java 17 con Spring Boot: API REST, seguridad, persistencia y puente hacia la IA y el simulador. |
| `toasted_vr_frontend/` | Aplicación web en React: registro, simulador de tueste en el navegador, historial y ranking. |
| `roast-simulator/` | Servicio en Node 24 que corre la física del tueste para Unity, reutilizando el dominio de la web. |
| `IA/` | Servicio en Python con FastAPI: retroalimentación de cada tueste, asistente de voz, alertas, calificación y RAG. |
| `postgres-init/` | Script que crea la extensión `vector` la primera vez que se crea el volumen de PostgreSQL. |

La base de datos es PostgreSQL 16 con pgvector (imagen `pgvector/pgvector:pg16`) y corre en Docker. El backend se conecta con Spring Data JPA + Hibernate, y el servicio IA usa la misma base para las tablas del RAG.

---

## Arquitectura

Uso una arquitectura cliente-servidor orientada a APIs REST. El backend centraliza la lógica de negocio, la seguridad y la persistencia; el frontend y Unity solo hablan con el backend, y el backend le delega el trabajo al servicio IA y al simulador por HTTP.

### Backend (Spring Boot)
- **Controllers**: exponen los endpoints REST.
- **Services**: lógica de negocio, validaciones, códigos de verificación y autenticación.
- **Repositories**: persistencia con Spring Data JPA sobre PostgreSQL.
- **Entities**: mapean las clases Java a tablas con Hibernate.
- **DTOs**: desacoplan la API de las entidades internas.
- **Security**: protege rutas y flujos sensibles con JWT.

### Frontend (React)
- Componentes funcionales y hooks.
- Textos centralizados en los archivos de traducción.
- CSS propio, sin Bootstrap ni Tailwind.

---

## Servicios y puertos

| Servicio | Dónde corre | Puerto |
|---|---|---|
| PostgreSQL + pgvector | Docker | `5433` en el host (`5432` dentro de la red de Compose) |
| Backend | Docker o host | `8081` |
| Frontend | Docker (nginx) o host (`npm start`) | `3000` |
| Simulador de tueste | Docker o host | `127.0.0.1:8083`, solo local |
| Servicio IA | Host, fuera de Docker | `127.0.0.1:8082` |
| Ollama | Host, fuera de Docker | `11434` |

---

## Cómo levanto el proyecto con Docker

Docker solo incluye la base de datos, el backend, el frontend y el simulador. El servicio IA y Ollama corren en el host.

1. Abro Docker Desktop y espero a que esté listo.
2. Copio `Toasted_VR/.env.example` a `Toasted_VR/.env` y pongo valores privados en `POSTGRES_PASSWORD`, `APP_JWT_SECRET`, `UNITY_CODE_HMAC_PEPPER` y `UNITY_CODE_ENCRYPTION_KEY`. Ese archivo nunca se sube.
3. Desde la raíz del repositorio, en PowerShell, levanto todo con los 4 compose:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml up --build -d
```

Si no cambié código desde la última vez, quito `--build` y arranca más rápido.

Para ver que todo quedó arriba (todos en `Up`, PostgreSQL además en `healthy`, ninguno en `Restarting`):

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml ps
```

Para seguir los logs del backend:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml logs -f backend
```

Para detener todo sin borrar los datos de la base:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml down
```

Nunca le agrego `-v` al `down`, porque esa opción borra el volumen de PostgreSQL.

Después abro `http://localhost:3000` en el navegador.

`postgres-init/` se monta en `/docker-entrypoint-initdb.d` y solo corre cuando el volumen se crea por primera vez. Si el volumen ya existía de antes, ejecuto a mano una vez el `CREATE EXTENSION` de `postgres-init/001-create-vector-extension.sql`.

---

## Cómo corro cada parte en el host

### Backend (`Toasted_VR`)

Necesito JDK 17 o superior y la base levantada en Docker (`docker compose up -d postgres` desde la raíz). En `Toasted_VR/.env` apunto `DB_URL` a `jdbc:postgresql://localhost:5433/toasted_vr_db`, con `DB_USERNAME` y `DB_PASSWORD` iguales a los de PostgreSQL. Con `JPA_DDL_AUTO=update`, Hibernate crea o actualiza las tablas al arrancar.

```bash
cd Toasted_VR
./mvnw spring-boot:run      # en Windows: mvnw.cmd spring-boot:run
```

Queda en `http://localhost:8081`. Los detalles de Docker, red local y variables están en `Toasted_VR/README.md`.

### Frontend (`toasted_vr_frontend`)

Uso Node 20 o más nuevo (el CI usa Node 20).

```bash
cd toasted_vr_frontend
npm install
npm start
```

Queda en `http://localhost:3000`. Más detalles en `toasted_vr_frontend/README.md`.

### Servicio IA (`IA/`)

Genera la retroalimentación al terminar un tueste, responde el asistente de voz y hace el RAG sobre los PDFs de `IA/rag-docs/`. La retroalimentación la redacta Groq (`openai/gpt-oss-120b`) y, si Groq falla o se queda sin cuota, Ollama local (`phi4-mini`).

1. Instalo Ollama desde [https://ollama.com](https://ollama.com) y descargo los modelos:
   ```bash
   ollama pull nomic-embed-text
   ollama pull phi4-mini
   ```
2. Creo una llave en [https://console.groq.com](https://console.groq.com) y la pongo como `GROQ_API_KEY` en `IA/.env` (ese archivo nunca se sube).
3. Levanto el servicio siguiendo `IA/README.md` (Python 3.12, entorno virtual en `IA/.venv` y `python -m app` desde `IA/`).

El backend lo encuentra con `AI_SERVICE_BASE_URL` (backend en el host) o `DOCKER_AI_SERVICE_BASE_URL` (backend en Docker) en `Toasted_VR/.env`.

Es opcional: si el servicio IA no está corriendo, o ni Groq ni Ollama responden, el resto de la aplicación funciona igual y en lugar de la retroalimentación aparece el mensaje "Retroalimentación no disponible por ahora".

### Simulador de tueste para Unity (`roast-simulator/`)

Unity usa la misma física del simulador web sin copiarla: `roast-simulator/` es un servicio en Node 24, sin dependencias, que importa directamente `toasted_vr_frontend/src/domain/roasting`. Unity nunca lo llama directo: juega a través de `/api/v1/roasting/simulations` del backend, que valida el token y guarda la sesión al descargar. El simulador nunca queda abierto hacia la red.

En el host, cuando el backend también corre en el host:

```bash
cd roast-simulator
npm start
```

Queda en `127.0.0.1:8083`. En Docker ya se levanta con los 4 compose de arriba y el backend lo encuentra por el nombre del servicio.

El backend lo encuentra con `ROAST_SIMULATOR_BASE_URL` (backend en el host) o `DOCKER_ROAST_SIMULATOR_BASE_URL` (backend en Docker). El contrato para Unity está en `IA/docs/unity-roast-simulation-contract.md` y los detalles del servicio en `roast-simulator/README.md`. La web no lo necesita: corre la física en el navegador.

---

## Variables de entorno

Cada parte tiene su `.env.example` con todos los nombres; los `.env` reales nunca se suben.

| Archivo | Para qué |
|---|---|
| `Toasted_VR/.env` | Base de datos (`POSTGRES_*`, `DB_*`), JWT, códigos de Unity, correo, reglas del tueste, URLs del servicio IA y del simulador, y administrador inicial. También lo usa Docker Compose con `--env-file`. |
| `toasted_vr_frontend/.env` | `REACT_APP_API_BASE_URL`, `REACT_APP_EVALUATION_POLL_INTERVAL_MS`, `REACT_APP_EVALUATION_POLL_MAX_ATTEMPTS`. |
| `IA/.env` | Base de datos, Groq, Ollama, RAG, voz y evaluación. |
| `roast-simulator/.env` | `SIMULATOR_HOST`, `SIMULATOR_PORT`, `SIMULATION_IDLE_TIMEOUT_SECONDS`, `SIMULATION_MAX_STORED_TICKS`, `SIMULATOR_MAX_BODY_BYTES`. |

---

## Flujo de Pull Requests y CI

Trabajo cada cambio en su propia rama y lo integro a `main` solo por Pull Request. Cada PR hacia `main` (y cada push a `main`) dispara el workflow `.github/workflows/ci.yml` en GitHub Actions, que corre cuatro checks:

- `backend-tests`: los tests de `Toasted_VR` con Java 17 y Maven (`./mvnw -B test`). Usan el perfil `test` (H2 en memoria, correo deshabilitado y servicio IA simulado), así que no necesitan PostgreSQL, el servicio IA ni SMTP.
- `frontend-tests`: en `toasted_vr_frontend` con Node 20 corre `npm ci`, los tests con `CI=true` y `npm run build`.
- `simulator-tests`: los tests de `roast-simulator` con Node 24 (`npm test`). No tienen dependencias: importan el dominio de la web directamente.
- `ai-tests`: los tests del servicio `IA/` con Python 3.12 y pytest. No necesitan PostgreSQL, Ollama ni Groq.

Solo fusiono un PR cuando los checks están en verde. Antes de abrirlo corro lo mismo localmente:

```bash
cd Toasted_VR && ./mvnw -B test
cd IA && .venv/Scripts/python -m pytest -q
cd roast-simulator && npm test
cd toasted_vr_frontend && npm ci && CI=true npm test -- --watchAll=false && CI=true npm run build
```

---

## Contexto importante

1. **Esquema automático:** con `JPA_DDL_AUTO=update`, Hibernate crea o actualiza las tablas según las entidades Java.
2. **Verificación por correo:** el registro envía un código de verificación con expiración para confirmar el email.
3. **SMTP real:** con Gmail uso una contraseña de aplicación, no la contraseña normal de la cuenta.
4. **Archivos sensibles:** los `.env` no se suben al repositorio; solo se versionan los `.env.example`.

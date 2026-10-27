# Servicio IA (Toasted VR)

Aquí tengo separada toda la parte de IA del proyecto: la retroalimentación de cada sesión de tueste y el RAG sobre los PDFs de `rag-docs/`. Es un servicio en Python con FastAPI que corre en el host, fuera de Docker, igual que Ollama.

La retroalimentación la redacta un LLM con cadena de respaldo: primero Groq (`openai/gpt-oss-120b`) y, si falla o se queda sin cuota, Ollama local (`phi4-mini`). Si ninguno responde, la retroalimentación queda vacía y la sesión se ve igual que siempre.

El frontend y Unity no lo llaman directamente: siguen usando los mismos endpoints del backend (`GET /api/v1/roasting/sessions/{id}/feedback` y `POST /api/v1/admin/rag/ingest`), y el backend le delega el trabajo a este servicio por HTTP.

## Requisitos

- Python 3.12 o superior.
- La base de datos del proyecto levantada (`docker compose up -d postgres` desde la raíz). Uso la misma base que el backend y solo toco las tablas del RAG: `rag_ingested_documents` y `rag_document_chunks` (índice HNSW de 768 dimensiones, distancia coseno). Al arrancar no se borra ni se recrea nada: solo se crean si no existen.
- Ollama instalado y corriendo en el host. `nomic-embed-text` es obligatorio porque con él está indexado el RAG; `phi4-mini` es el respaldo de Groq:

```bash
ollama pull nomic-embed-text
ollama pull phi4-mini
```

- Una llave de Groq (https://console.groq.com, capa gratuita). Es opcional: sin llave se usa solo Ollama.

## Cómo lo levanto

1. Creo `IA/.env` a partir de `IA/.env.example` y pongo la contraseña de la base (la misma `POSTGRES_PASSWORD` de `Toasted_VR/.env`) y `GROQ_API_KEY`. Ese archivo nunca se sube a git.
2. La primera vez creo el entorno virtual desde la carpeta `IA/`:

```bash
py -3.12 -m venv .venv
.venv/Scripts/python -m pip install -e ".[dev]"
```

En Linux o macOS es `python3.12 -m venv .venv` y `.venv/bin/python`.

3. Lo arranco:

```bash
.venv/Scripts/python -m app
```

El servicio queda en `http://127.0.0.1:8082`.

4. En `Toasted_VR/.env` el backend apunta al servicio con `AI_SERVICE_BASE_URL=http://localhost:8082` (backend corriendo en el host) o `DOCKER_AI_SERVICE_BASE_URL=http://host.docker.internal:8082` (backend en Docker).

## Endpoints

Son internos, solo para el backend, y no tienen autenticación propia: la autenticación y el rol ADMIN los valida el backend antes de llamarlos. Por eso el servicio escucha por defecto solo en `127.0.0.1`.

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/v1/feedback` | Recibe los datos de la sesión y responde `{"feedback": "..."}` (`null` si ningún proveedor respondió) |
| `POST` | `/api/v1/rag/ingest` | Ingesta los PDFs de `rag-docs/` que sean nuevos o hayan cambiado |

El contrato es exactamente el mismo que tenía la versión anterior en Java, así que el backend no cambió. Un cuerpo inválido responde 400.

La ingesta no corre sola al arrancar. Cuando agrego o cambio un PDF en `rag-docs/`, la lanzo desde el backend con un usuario ADMIN (`POST /api/v1/admin/rag/ingest`). Los PDFs que no cambiaron se saltan por su hash, y cada archivo se guarda en una sola transacción: si algo falla a mitad, sus fragmentos anteriores siguen intactos.

## Caché de retroalimentación

El backend pide la retroalimentación cada vez que se abre una sesión. Para no gastar cuota del proveedor en cada apertura, guardo en memoria las respuestas por 24 horas (máximo 500). La llave incluye los datos de la sesión, el nivel y los modelos configurados, así que cambiar de modelo no reutiliza respuestas viejas. La caché se vacía si una ingesta agrega material nuevo y se pierde al reiniciar el servicio. Con `FEEDBACK_CACHE_TTL_SECONDS=0` queda desactivada.

## Configuración

Todo sale de variables de entorno o de `IA/.env` (ver `IA/.env.example`): dirección y puerto, base de datos, orden de proveedores (`FEEDBACK_PROVIDERS`), modelo y tiempos de Groq, modelo y opciones de Ollama, caché, cantidad de fragmentos por consulta (`RAG_TOP_K`) y tamaño de los fragmentos.

`FEEDBACK_TOTAL_TIMEOUT_MILLIS` (125 s por defecto) es el tiempo total que tiene la cadena para responder, repartido entre los proveedores. Siempre lo dejo menor que `AI_SERVICE_FEEDBACK_READ_TIMEOUT_MILLIS` del backend (130 s), para que el backend no se canse de esperar justo cuando Ollama está respondiendo.

Para usar el modelo más rápido de Groq pongo `GROQ_MODEL=openai/gpt-oss-20b`; para no usar Groq, `FEEDBACK_PROVIDERS=ollama`.

## Servidor Ubuntu

`AI_SERVER_ADDRESS` define en qué interfaz escucha el servicio (por defecto `127.0.0.1`). En Docker Desktop (Windows) eso basta, porque `host.docker.internal` llega al localhost del host. En un servidor Linux no: el contenedor del backend llega al host por la IP del gateway de la red de Docker, y en Linux `host.docker.internal` no existe salvo que se declare en el compose. Ahí hago esto:

1. Busco la IP del gateway de la red del compose (`docker network inspect <red> --format '{{(index .IPAM.Config 0).Gateway}}'`, normalmente algo como `172.18.0.1`).
2. Levanto el servicio con `AI_SERVER_ADDRESS` en esa IP (o `0.0.0.0` si el firewall bloquea el puerto 8082 desde fuera).
3. En `Toasted_VR/.env` pongo `DOCKER_AI_SERVICE_BASE_URL=http://<esa IP>:8082`.

## Tests

```bash
.venv/Scripts/python -m pytest -q
```

Corren sin base de datos, sin Ollama y sin Groq (las llamadas HTTP son simuladas). En el CI los corre el job `ai-tests`. Los tests del prompt y de la consulta al RAG comparan byte a byte contra salidas grabadas en `tests/snapshots/` con el código original en Java, para asegurar que la migración no cambió lo que se le envía al modelo.

Los tests de `tests/integration/` usan un PostgreSQL real y solo corren si defino `AI_TEST_DB_URL` con una base desechable (el nombre debe terminar en `_test` o `_e2e`; se niegan a correr contra otra):

```bash
AI_TEST_DB_URL=jdbc:postgresql://localhost:5433/toasted_vr_ai_test .venv/Scripts/python -m pytest -q tests/integration
```

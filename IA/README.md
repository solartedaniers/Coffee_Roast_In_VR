# Servicio IA (Toasted VR)

Aquí tengo separada toda la parte de IA del proyecto: la retroalimentación de cada sesión de tueste con Ollama (phi4-mini) y el RAG sobre los PDFs de `src/main/resources/rag-docs/`. Es un servicio Spring Boot propio (Java 17, Maven) que corre en el host, fuera de Docker, igual que Ollama.

El frontend y Unity no lo llaman directamente: siguen usando los mismos endpoints del backend (`GET /api/v1/roasting/sessions/{id}/feedback` y `POST /api/v1/admin/rag/ingest`), y el backend le delega el trabajo a este servicio por HTTP. Si el servicio no responde, el backend devuelve la retroalimentación vacía, igual que antes cuando Ollama no estaba disponible.

## Requisitos

- JDK 17.
- La base de datos del proyecto levantada (`docker compose up -d postgres` desde la raíz). Uso la misma base que el backend y solo toco las tablas del RAG: `rag_ingested_documents` y `rag_document_chunks` (índice HNSW de 768 dimensiones, distancia coseno). Al arrancar no se borra ni se recrea nada: solo se crean si no existen.
- Ollama instalado y corriendo en el host con los dos modelos:

```bash
ollama pull phi4-mini
ollama pull nomic-embed-text
```

## Cómo lo levanto

1. Creo `IA/.env` a partir de `IA/.env.example` y pongo la contraseña de la base (la misma `POSTGRES_PASSWORD` de `Toasted_VR/.env`).
2. Desde la carpeta `IA/`:

```bash
./mvnw spring-boot:run
```

En Windows uso `mvnw.cmd spring-boot:run`. El servicio queda en `http://127.0.0.1:8082`.

3. En `Toasted_VR/.env` el backend apunta al servicio con `AI_SERVICE_BASE_URL=http://localhost:8082` (backend corriendo en el host) o `DOCKER_AI_SERVICE_BASE_URL=http://host.docker.internal:8082` (backend en Docker).

## Endpoints

Son internos, solo para el backend, y no tienen autenticación propia: la autenticación y el rol ADMIN los valida el backend antes de llamarlos. Por eso el servicio escucha por defecto solo en `127.0.0.1`.

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/v1/feedback` | Recibe los datos de la sesión y responde `{"feedback": "..."}` (`null` si Ollama falló) |
| `POST` | `/api/v1/rag/ingest` | Ingesta los PDFs de `rag-docs/` que sean nuevos o hayan cambiado |

La ingesta no corre sola al arrancar. Cuando agrego o cambio un PDF en `rag-docs/`, la lanzo desde el backend con un usuario ADMIN (`POST /api/v1/admin/rag/ingest`).

## Configuración

Todo sale de `application.yml` con variables de entorno (ver `IA/.env.example`): dirección y puerto, base de datos, URL de Ollama, modelo, tiempos de espera, `keep_alive`, `temperature`, `num_predict`, cantidad de fragmentos por consulta (`RAG_TOP_K`) y tamaño de los fragmentos.

Si el equipo es lento y la retroalimentación llega vacía, subo `OLLAMA_READ_TIMEOUT_MILLIS` aquí y `AI_SERVICE_FEEDBACK_READ_TIMEOUT_MILLIS` en el backend (este último siempre un poco mayor).

## Servidor Ubuntu

`AI_SERVER_ADDRESS` define en qué interfaz escucha el servicio (por defecto `127.0.0.1`). En Docker Desktop (Windows) eso basta, porque `host.docker.internal` llega al localhost del host. En un servidor Linux no: el contenedor del backend llega al host por la IP del gateway de la red de Docker, y en Linux `host.docker.internal` no existe salvo que se declare en el compose. Ahí hago esto:

1. Busco la IP del gateway de la red del compose (`docker network inspect <red> --format '{{(index .IPAM.Config 0).Gateway}}'`, normalmente algo como `172.18.0.1`).
2. Levanto el servicio con `AI_SERVER_ADDRESS` en esa IP (o `0.0.0.0` si el firewall bloquea el puerto 8082 desde fuera).
3. En `Toasted_VR/.env` pongo `DOCKER_AI_SERVICE_BASE_URL=http://<esa IP>:8082`.

## Tests

```bash
./mvnw -B test
```

Corren con H2 en memoria y sin Ollama. En el CI los corre el job `ai-tests`. Los tests del prompt y de la consulta al RAG comparan contra salidas grabadas en `src/test/resources/snapshots/` con el código original del backend, para asegurar que mover la IA no cambió lo que se le envía al modelo.

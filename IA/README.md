# Servicio IA (Toasted VR)

Aquí tengo separada toda la parte de IA del proyecto: la retroalimentación de cada sesión de tueste, el asistente de voz que responde preguntas durante el tueste y el RAG sobre los PDFs de `rag-docs/`. Es un servicio en Python con FastAPI que corre en el host, fuera de Docker, igual que Ollama.

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
| `GET` | `/api/v1/alerts/package` | Paquete de alertas proactivas de un nivel, con `ETag` (ver [Alertas proactivas](#alertas-proactivas)) |

El contrato es exactamente el mismo que tenía la versión anterior en Java, así que el backend no cambió. Un cuerpo inválido responde 400.

La ingesta no corre sola al arrancar. Cuando agrego o cambio un PDF en `rag-docs/`, la lanzo desde el backend con un usuario ADMIN (`POST /api/v1/admin/rag/ingest`). Los PDFs que no cambiaron se saltan por su hash, y cada archivo se guarda en una sola transacción: si algo falla a mitad, sus fragmentos anteriores siguen intactos.

## Caché de retroalimentación

El backend pide la retroalimentación cada vez que se abre una sesión. Para no gastar cuota del proveedor en cada apertura, guardo en memoria las respuestas por 24 horas (máximo 500). La llave incluye los datos de la sesión, el nivel y los modelos configurados, así que cambiar de modelo no reutiliza respuestas viejas. La caché se vacía si una ingesta agrega material nuevo y se pierde al reiniciar el servicio. Con `FEEDBACK_CACHE_TTL_SECONDS=0` queda desactivada.

## Configuración

Todo sale de variables de entorno o de `IA/.env` (ver `IA/.env.example`): dirección y puerto, base de datos, orden de proveedores (`FEEDBACK_PROVIDERS`), modelo y tiempos de Groq, modelo y opciones de Ollama, caché, cantidad de fragmentos por consulta (`RAG_TOP_K`) y tamaño de los fragmentos.

`FEEDBACK_TOTAL_TIMEOUT_MILLIS` (125 s por defecto) es el tiempo total que tiene la cadena para responder, repartido entre los proveedores. Siempre lo dejo menor que `AI_SERVICE_FEEDBACK_READ_TIMEOUT_MILLIS` del backend (130 s), para que el backend no se canse de esperar justo cuando Ollama está respondiendo.

Para usar el modelo más rápido de Groq pongo `GROQ_MODEL=openai/gpt-oss-20b`; para no usar Groq, `FEEDBACK_PROVIDERS=ollama`.

Del RAG también configuro los filtros sobre lo recuperado (`RAG_DEDUPLICATE_CHUNKS`, `RAG_MAX_CHUNKS_PER_PAGE`, `RAG_CANDIDATE_POOL`) y la partición del texto al indexar (`RAG_TEXT_SPLITTER`, `RAG_MIN_CHUNK_CHARS`, `RAG_REPEATED_LINE_PAGE_RATIO`). Los títulos legibles de cada PDF, que uso en las citas, están en `rag-docs/document_titles.json`: cuando agrego un PDF le agrego su título ahí (un test revisa que no falte ninguno).

## Evaluación del RAG

Para saber si los fragmentos que llegan al prompt son relevantes, mido la recuperación con un set de preguntas en `eval/rag_questions.json`:

- 25 preguntas de jugador (`player`), cada una con el PDF y la página donde está la respuesta. La página es la del PDF, no la impresa.
- 5 preguntas de café que no tienen respuesta en los PDFs (`negative`).
- Las 4 búsquedas reales de la retroalimentación (`session`), sin etiquetas, que reporto aparte.

Para ampliarlo con PDFs nuevos solo agrego casos al JSON; si una página esperada no existe en `rag-docs/`, la carga falla y lo avisa.

```bash
.venv/Scripts/python -m app.rag.diagnostics                       # fragmentos por archivo, archivos fallidos, PDFs sin texto
.venv/Scripts/python -m app.rag.evaluation --label mi-medicion    # hit@k y MRR@10, en eval/results/mi-medicion.json
.venv/Scripts/python -m app.rag.evaluation.compare                # tabla comparativa de todas las mediciones
.venv/Scripts/python -m app.rag.index_builder_cli --table <tabla>  # reindexa en una tabla aparte (solo en bases *_test o *_e2e)
```

La evaluación y el diagnóstico abren la base en solo lectura. Todas las mediciones las hice sobre una copia desechable de las tablas del RAG, nunca sobre la base real. Una mejora solo se quedaba si subía el MRR@10 sin bajar el hit@4; con 25 preguntas, una pregunta mueve 0,04, así que una diferencia de una sola pregunta la tomo como empate.

| Variante | hit@1 | hit@3 | hit@4 | hit@5 | MRR@10 | Δ MRR | neg. con contexto | repetidos | p50 ms |
|---|---|---|---|---|---|---|---|---|---|
| 01-linea-base | 0.32 | 0.44 | 0.52 | 0.60 | 0.409 | +0.000 | 5/5 | 2 | 891 |
| 02-hibrida-rrf | 0.12 | 0.52 | 0.52 | 0.52 | 0.307 | -0.102 | 5/5 | 2 | 1075 |
| 03-hibrida-rrf-filtro-frecuencia | 0.24 | 0.56 | 0.60 | 0.60 | 0.402 | -0.007 | 5/5 | 2 | 533 |
| 04-umbral-0.70 | 0.32 | 0.40 | 0.44 | 0.52 | 0.386 | -0.023 | 5/5 | 2 | 476 |
| 05-sin-repetidos | 0.32 | 0.44 | 0.52 | 0.60 | 0.409 | +0.000 | 5/5 | 0 | 453 |
| 06-sin-repetidos-1-por-pagina | 0.32 | 0.48 | 0.56 | 0.60 | 0.437 | +0.028 | 5/5 | 0 | 465 |
| 07-prefijo-consulta | 0.28 | 0.52 | 0.56 | 0.56 | 0.397 | -0.013 | 5/5 | 3 | 482 |
| 08-prefijo-con-filtros | 0.28 | 0.56 | 0.56 | 0.56 | 0.422 | +0.013 | 5/5 | 0 | 464 |
| 09-traduccion-con-filtros | 0.28 | 0.44 | 0.52 | 0.68 | 0.421 | +0.012 | 5/5 | 0 | 1996 |
| 10-reingesta-python | 0.20 | 0.44 | 0.48 | 0.52 | 0.335 | -0.074 | 5/5 | 0 | 459 |
| 11-reingesta-python-prefijos | 0.24 | 0.36 | 0.40 | 0.56 | 0.359 | -0.050 | 5/5 | 0 | 468 |
| 12-caracteres-250 | 0.12 | 0.40 | 0.52 | 0.56 | 0.293 | -0.116 | 5/5 | 0 | 550 |
| 13-frases-300 | 0.20 | 0.44 | 0.48 | 0.56 | 0.359 | -0.050 | 5/5 | 0 | 483 |
| 14-frases-500 | 0.40 | 0.52 | 0.52 | 0.56 | 0.482 | +0.073 | 5/5 | 0 | 475 |
| 15-frases-700 | 0.16 | 0.48 | 0.56 | 0.60 | 0.336 | -0.073 | 5/5 | 0 | 404 |
| 16-bge-m3-frases-500 | 0.16 | 0.36 | 0.56 | 0.64 | 0.342 | -0.067 | 5/5 | 0 | 464 |
| 17-frases-500-prefijos | 0.28 | 0.52 | 0.52 | 0.64 | 0.422 | +0.013 | 5/5 | 0 | 424 |
| 18-configuracion-final | 0.32 | 0.48 | 0.56 | 0.60 | 0.437 | +0.028 | 5/5 | 0 | 416 |

La latencia de la línea base (891 ms) incluye cargar el modelo en frío; con el modelo ya cargado la misma búsqueda tarda unos 450 ms, así que los filtros no agregan tiempo.

Lo que se quedó:

- **Quitar fragmentos repetidos y tomar uno por página** (05 y 06, activos por defecto): el prompt deja de recibir textos repetidos y el MRR sube de 0,409 a 0,437. No exige reindexar.
- **Citas validadas** (archivo, página y título): `RagContextRetriever.find_relevant_passages` devuelve cada fragmento con su cita solo si el PDF existe y la página está dentro de él. El prompt de la retroalimentación sigue recibiendo exactamente los mismos textos.
- **Partición por frases de 500 caracteres** (14), lista pero sin aplicar al índice real: limpia el PDF (une palabras cortadas, quita encabezados repetidos y la bibliografía) y junta frases completas. Es la mejor variante medida (MRR 0,482, hit@1 0,40), pero exige reingestar.

Lo que revertí, con sus mediciones guardadas como evidencia:

- **Búsqueda híbrida con RRF** (02 y 03): la búsqueda de texto en español la dominan palabras como "café" o "tueste", que aparecen en más del 19 % de los fragmentos. Quitándolas mejora, pero aun así no supera a la línea base.
- **Umbral de relevancia** (04): con nomic-embed-text la similitud de los fragmentos correctos (mediana 0,71) y la del mejor fragmento de las preguntas sin respuesta (0,71–0,76) se solapan, así que cualquier umbral quita aciertos antes que ruido. Por eso las 5 negativas siempre reciben contexto.
- **Prefijos `search_query:` / `search_document:` de nomic** (07, 08, 11 y 17): no mejoraron en ningún índice.
- **Traducir la consulta con Groq** (09 y `09b-traduccion-union-por-posicion.txt`): sube mucho las preguntas cuya respuesta está en los PDFs en inglés (MRR 0,20 → 0,65), pero desplaza a los españoles; ninguna forma de unir los resultados supera a 06 y cada consulta tarda ~1,5 s más.
- **Modelo multilingüe bge-m3** (16): con la misma partición queda por debajo de nomic (0,342 contra 0,482). Revisándolo, varias de sus respuestas son páginas relevantes que no están entre las esperadas: es una limitación de etiquetar por página.

Un hallazgo importante: el índice actual (1243 fragmentos) lo armó la versión en Java, cuyo lector de PDF dejaba el texto con muchos espacios. Reingestar los mismos PDFs con el lector de Python, sin ningún otro cambio, deja 390 fragmentos y baja el MRR a 0,335 (10). Por eso, cuando reingeste, lo hago con la partición por frases de 500 y no con la de caracteres.

## Asistente de voz

Durante el tueste el jugador puede preguntar por voz y oír la respuesta en pocos segundos. Unity llama al backend (`POST /api/v1/roasting/assistant/questions`) y el backend llama aquí a `POST /api/v1/voice/questions` con el audio, el usuario, su nivel y el estado del tueste. El contrato completo para Unity está en [docs/unity-voice-contract.md](docs/unity-voice-contract.md).

Cada pregunta pasa por cuatro pasos, cada uno con respaldo:

1. **Voz a texto:** Groq Whisper (`whisper-large-v3-turbo`); si falla, Azure Speech. Las frases que Whisper inventa con el silencio ("Gracias por ver el video"…) se filtran con `config/whisper_silence_phrases.json` y responden 422.
2. **Fragmentos del RAG:** los 3 más cercanos a la pregunta, con su cita validada.
3. **Respuesta:** Groq `openai/gpt-oss-20b` con salida JSON estricta, máximo 3 frases, adaptada al nivel del jugador y con las 2 últimas respuestas del mismo intento como contexto. El modelo tiene que decir qué fuentes usó (PDFs o el estado del tueste); si no usa ninguna o cita una que no se le dio, el asistente se abstiene en vez de inventar. Además, cada cifra de la respuesta (temperatura, porcentaje de gas, tiempo) tiene que aparecer en los fragmentos que citó, en el estado del tueste (si lo citó) o en la pregunta, con un margen de redondeo de `VOICE_FIGURE_TOLERANCE`. Las frases con cifras sin respaldo se quitan y, si no queda ninguna, se abstiene: es un simulador para enseñar y una cifra inventada enseña algo falso. El prompt le pide al modelo dar solo la dirección ("baja un poco el gas") cuando no tiene una cifra con respaldo. Solo reviso cifras escritas con dígitos, que es como el prompt le pide escribirlas. No hay respaldo en Ollama a propósito: phi4-mini tarda demasiado para una respuesta hablada.
4. **Texto a voz:** Azure (`es-CO-SalomeNeural`); si falla o se llega al límite mensual, Piper local con `es_MX-claude-high`.

Si Groq no responde, el jugador oye una frase fija ("No puedo responder en este momento…"). Esa frase y la de abstención están en `config/voice_messages.json` y su audio se genera al arrancar y queda guardado en `voice-cache/`, así que se oyen al instante y sin gastar cuota.

Límites (todos en `IA/.env`): una pregunta a la vez y 6 por minuto por jugador, audio de hasta 15 s y 600 000 bytes, y 25 s en total por pregunta (`VOICE_TOTAL_TIMEOUT_MILLIS`, menor que los 30 s que espera el backend en `AI_SERVICE_VOICE_READ_TIMEOUT_MILLIS`).

### Cuotas gratuitas

- **Azure Speech F0:** 500 000 caracteres de voz al mes. Llevo mi propio contador en la tabla `tts_monthly_usage` y al llegar a `AZURE_TTS_MONTHLY_CHAR_LIMIT` (450 000) paso directo a Piper. Una respuesta típica gasta unos 150–300 caracteres.
- **Groq:** Whisper 20 pedidos por minuto y 2 000 al día; `gpt-oss-20b` 30 por minuto y 1 000 al día. La documentación de Groq da los límites por modelo, pero no dice explícitamente si se comparten entre modelos de la misma organización; por eso uso `gpt-oss-20b` para la voz y dejo `gpt-oss-120b` para la retroalimentación. Cada pregunta gasta una transcripción y una respuesta.

### Tablas nuevas

Al arrancar se crean, si no existen, `tts_monthly_usage` (caracteres de Azure por mes) y `voice_assistant_exchanges` (historial de preguntas y respuestas por usuario e intento, con proveedores y latencia). No tienen endpoint de consulta todavía.

### Instalar Piper

Piper va en `IA/voice-runtime/`, que git ignora. Lo descargo solo de las fuentes oficiales:

- Ejecutable: release `2023.11.14-2` de https://github.com/rhasspy/piper/releases (`piper_windows_amd64.zip` en Windows, `piper_linux_x86_64.tar.gz` en Ubuntu). Lo descomprimo en `voice-runtime/piper/`.
- Voz: `es_MX-claude-high.onnx` y `es_MX-claude-high.onnx.json` de https://huggingface.co/rhasspy/piper-voices (carpeta `es/es_MX/claude/high/`). Los dejo en `voice-runtime/voices/`.

En Windows las rutas por defecto ya apuntan ahí. En Ubuntu el ejecutable no tiene `.exe`, así que en `IA/.env` pongo:

```bash
PIPER_EXECUTABLE=voice-runtime/piper/piper
PIPER_MODEL=voice-runtime/voices/es_MX-claude-high.onnx
```

y le doy permiso de ejecución con `chmod +x voice-runtime/piper/piper`. Para probar solo Piper uso `VOICE_SPEECH_PROVIDERS=piper`.

### Latencia medida

Medí con el servicio real (Groq, Azure y Piper de verdad) desde mi equipo con Windows, contra una copia desechable de la base, con preguntas de 2–3 s:

| Pregunta | Resultado | Voz a texto | RAG | Respuesta | Voz | Total servidor | Total cliente |
|---|---|---|---|---|---|---|---|
| ¿Qué pasa con el grano en el primer crack? | ANSWERED | — | — | — | Azure | 6,3 s | 6,9 s |
| ¿Debería bajar el gas ahora? (calor 80 %, +14 °C/min) | ANSWERED | 1,0 s | 0,7 s | 1,2 s | Azure 3,0 s | 5,9 s | 6,7 s |
| ¿Quién ganó el mundial de 2014? | ABSTAINED | 1,3 s | 0,7 s | 0,8 s | caché 0 s | 2,7 s | 3,6 s |
| ¿Y cuánto tiempo falta para eso? | ABSTAINED | 1,1 s | 0,8 s | 1,1 s | caché 0 s | 3,0 s | 3,7 s |
| Audio sin voz | 422 SILENT_AUDIO | — | — | — | — | — | 1,2 s |
| ¿Qué pasa con el grano en el primer crack? (solo Piper) | ANSWERED | 1,3 s | 0,8 s | 1,7 s | Piper 4,5 s | 8,3 s | 9,1 s |

Después lo medí por la ruta completa, como lo hará Unity: backend local con un usuario de prueba de nivel principiante (creado solo en la copia de la base), servicio IA de la rama y el mismo script con `--backend` y su JWT:

| Pregunta | Resultado | Voz a texto | RAG | Respuesta | Voz | Total servidor IA | Total cliente |
|---|---|---|---|---|---|---|---|
| Sin token | 401 | — | — | — | — | — | 0,02 s |
| Audio de 1,28 MB | 413 AUDIO_TOO_LARGE | — | — | — | — | — | 0,9 s |
| ¿Debería bajar el gas ahora? (primera, modelo de embeddings en frío) | ANSWERED | 1,2 s | 2,2 s | 2,2 s | Azure 3,9 s | 9,4 s | 10,4 s |
| ¿Qué pasa con el grano en el primer crack? | ANSWERED | 1,1 s | 0,7 s | 1,9 s | Azure 2,2 s | 5,9 s | 6,7 s |
| ¿Y cuánto tiempo falta para eso? | ABSTAINED | 1,2 s | 0,9 s | 1,6 s | caché 0 s | 3,8 s | 4,6 s |

El backend agrega menos de 0,2 s frente a llamar directo al servicio IA; el resto de la diferencia entre el cliente y el servidor es mandar el audio y recibir el WAV en base64. En la segunda y la tercera el filtro de cifras quitó frases: una temperatura que el modelo no sacó del PDF que citó y un tiempo que calculó por su cuenta.

El paso más lento es generar la voz, porque espera el audio completo antes de responder.

## Alertas proactivas

Durante el tueste el asistente también habla por su cuenta, como un profesor al lado: carga fuera de rango, subida muy rápida o muy lenta, estancamiento, primer crack, desarrollo y riesgo de quemado. Las reglas no corren aquí: armo un paquete por nivel con las reglas como datos, la frase del nivel y su audio ya sintetizado, y Unity lo descarga al empezar el tueste y lo evalúa localmente cada segundo. Así no hay latencia de red, funciona sin WiFi y Azure se gasta una sola vez por frase. El contrato y la especificación del evaluador para Unity están en [docs/unity-proactive-alerts-contract.md](docs/unity-proactive-alerts-contract.md).

- **Catálogo:** `config/proactive_alerts.json`. Cada regla dice de dónde sale cada umbral: *Fuente* (PDFs o tesis), *Parámetro de diseño* (reglas del simulador sin respaldo en los PDFs) o *Propuesto* (valores míos, afinados con el simulador). Se valida al arrancar: señales, operadores, niveles y una frase por nivel.
- **Reglas de quemado:** aviso a 215 °C, segundo crack inminente a 218 °C y techo superado por encima de 220 °C. El estancamiento de Maillard solo cuenta antes del primer crack y el desarrollo avisa con el DTR en 18 % y 22 %.
- **Niveles:** el backend entrega solo el paquete del nivel del jugador (INTERMEDIATE si no tiene). BEGINNER e INTERMEDIATE reciben las 17 reglas con frases distintas; ADVANCED recibe 12, sin la guía de subida del secado, el aviso de primer crack cercano ni los refuerzos LOW.
- **Canal de voz:** una sola voz con prioridades CRITICAL > HIGH > NORMAL > LOW, cola de 5 s, LOW nunca espera y mínimo 8 s entre alertas que no son CRITICAL. Mientras el jugador graba una pregunta solo se muestra el subtítulo.
- **Audio:** se genera al arrancar, en segundo plano, con la misma voz y el mismo `voice-cache/` del asistente. Las 46 frases de los tres niveles gastan 2 775 caracteres de Azure una sola vez, contados en `tts_monthly_usage` (0,6 % del límite mensual). Si una frase no se pudo sintetizar va con audio `null` y se reintenta en el siguiente pedido.
- **ETag:** el backend reenvía `If-None-Match` y responde 304 si Unity ya tiene esa versión. Tiempo para sintetizar lo que falte en un pedido: `ALERTS_PACKAGE_TIMEOUT_MILLIS` (20 s), menor que los 30 s de `AI_SERVICE_ALERTS_READ_TIMEOUT_MILLIS` en el backend.

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/v1/alerts/package?level=BEGINNER` | Paquete del nivel (sin `level`, el nivel por defecto del catálogo), con `ETag` y 304 |

### Simulador de alertas

Con `scripts/alert_simulator.py` veo qué alerta suena en cada curva, en qué segundo y cuántas por nivel, sin Unity:

```bash
.venv/Scripts/python scripts/alert_simulator.py --phrases          # curvas de prueba del repositorio
.venv/Scripts/python scripts/alert_simulator.py --csv curva.csv    # curvas propias, una fila por segundo
.venv/Scripts/python scripts/alert_simulator.py --catalog copia.json --summary   # probar umbrales
```

Las curvas de prueba (`eval/alerts/curves.json`) salen del simulador del frontend, sin modificar su física. Con ellas genero los vectores de conformidad (`eval/alerts/conformance.json`, con `python -m app.alerts.conformance`) que Unity usa para comprobar su evaluador en C#; un test falla si el catálogo cambia y no los regenero.

Alertas por tueste en la revisión 2, con las curvas de prueba del repositorio:

| Curva | BEGINNER | INTERMEDIATE | ADVANCED |
|---|---|---|---|
| Buena | 5 | 5 | 2 |
| Quemada | 10 | 10 | 7 |
| Horneada | 8 | 8 | 5 |
| Cruda | 1 | 1 | 0 |
| Con caída | 6 | 6 | 3 |
| Carga baja | 6 | 6 | 3 |
| Sobredesarrollada | 7 | 7 | 4 |

Afiné los umbrales además con 10 semillas por escenario del simulador real; en BEGINNER la buena queda en 5, la quemada entre 8 y 10 y la horneada entre 7 y 8.

Dos ajustes salieron de esas curvas: en el simulador la pantalla salta del aire al grano al cargar, así que la subida de las alertas empieza de nuevo en la carga (antes marcaba un estancamiento falso a los 13 s en todos los tuestes); y la subida rápida del secado avisa desde 25 °C/min, porque un tueste normal llega como máximo a 21.

### Observación pendiente

Después del primer crack el resultado es muy sensible al gas: con el 25 % el tueste sale bien, con el 30 % el grano llega a 216–220 °C y con el 15 % se enfría hasta quedar crudo. Lo anoto para revisarlo con la física del simulador; en esta fase no la cambié.

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

En Windows uso `127.0.0.1` en vez de `localhost` en `OLLAMA_BASE_URL` y en las URLs de la base cuando mido: con `localhost` cada embedding tardaba ~3 s en lugar de ~0,7 s (probablemente porque prueba IPv6 primero).

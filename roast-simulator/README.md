# Simulador de tueste (Node)

Este servicio corre la física del tueste para Unity. No tiene una física propia: importa directamente los archivos de `toasted_vr_frontend/src/domain/roasting`, los mismos que usa el simulador web, así que la calibración con el tostador experto, las constantes y la nota son idénticas en los dos lados y los tests de la web cubren a los dos.

Unity no habla con este servicio. Unity llama al backend (`/api/v1/roasting/simulations`), el backend valida el token del jugador y me reenvía el pedido con su id en `X-User-Id`. Por eso el simulador escucha solo en `127.0.0.1` y corre fuera de Docker, igual que el servicio IA.

## Cómo funciona

- Cada simulación avanza sola, con el mismo ritmo de la web (`RoastPacingProfile`): un tick cada 225 ms en el precalentado y uno por segundo desde la carga. El jugador solo cambia el gas, carga y descarga.
- Guardo los últimos `SIMULATION_MAX_STORED_TICKS` ticks de cada simulación y descarto los más viejos. El cliente pide `?afterTick=N` y recibe todos los ticks guardados posteriores a `N`, así la curva queda completa aunque el WiFi se corte un momento. Si `N` es más viejo que lo que guardo, recibe desde el tick más antiguo que queda: el cliente lo nota porque el primer `tick` de la lista es mayor que `N + 1`.
- Cada jugador tiene una sola simulación: crear otra reemplaza la anterior. Si nadie consulta una simulación durante `SIMULATION_IDLE_TIMEOUT_SECONDS`, la borro.
- Al descargar calculo la nota con `RoastQualityEvaluator` y armo el pedido de guardado con la traza (`RoastTraceRecorder`) y las alertas que sonaron en Unity. El backend es quien lo guarda.
- Las simulaciones viven en memoria: si reinicio el servicio, las que estaban en curso responden 404.

## Requisitos

Node 24 o más nuevo. No uso dependencias: solo `node:http` y `node:test`.

## Arrancarlo

```bash
cd roast-simulator
cp .env.example .env   # opcional
npm start
```

Queda en `http://127.0.0.1:8083`.

| Variable | Por defecto | Qué es |
|---|---|---|
| `SIMULATOR_HOST` | `127.0.0.1` | Dirección donde escucha |
| `SIMULATOR_PORT` | `8083` | Puerto |
| `SIMULATION_IDLE_TIMEOUT_SECONDS` | `120` | Segundos sin consultas tras los que borro una simulación |
| `SIMULATION_MAX_STORED_TICKS` | `3600` | Ticks que guardo por simulación; 3600 es una hora simulada, cerca de 1,5 MB |
| `SIMULATOR_MAX_BODY_BYTES` | `16384` | Tamaño máximo del cuerpo de un pedido |

Si el backend corre en Docker, el contenedor no llega a `127.0.0.1` del host: arranco el simulador con `SIMULATOR_HOST=0.0.0.0` y el backend usa `DOCKER_ROAST_SIMULATOR_BASE_URL`. En ese caso el puerto 8083 queda abierto en la red y cualquiera que llegue a él puede actuar como cualquier jugador con `X-User-Id`, así que bloqueo el 8083 para la red en el firewall de Windows y solo dejo pasar al contenedor.

## API interna

Todas las rutas piden `X-User-Id`. Los errores responden `{"code": "..."}` y el backend pone el mensaje en español.

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /api/v1/simulations` | `{"chargeTemperature": 190, "targetTemperature": 210}` (opcionales) | `201 {"simulationId", "state"}`, ya precalentando |
| `GET /api/v1/simulations/{id}?afterTick=N` | — | `200 {"state", "ticks"}` |
| `PUT /api/v1/simulations/{id}/gas` | `{"gasPercent": 60}` | `200 {"state"}` |
| `POST /api/v1/simulations/{id}/charge` | — | `200 {"state"}` |
| `POST /api/v1/simulations/{id}/discharge` | `{"knowledgeLevel": "BEGINNER", "alerts": [{"id": "FIRST_CRACK", "t": 625}]}` | `200 {"state", "sessionRequest"}` |
| `DELETE /api/v1/simulations/{id}` | — | `204` |

Un `state` (y cada elemento de `ticks`) se ve así:

```json
{
  "tick": 1040, "phase": "ROASTING", "roastSeconds": 510,
  "temperature": 198.42, "airTemperature": 231.1, "moisturePct": 4.1,
  "gasPercent": 60, "risePerMinute": 14,
  "chargeTemperature": 190.3, "targetTemperature": 210, "grainState": "MAILLARD",
  "firstCrack": {"reached": false, "roastSeconds": null},
  "secondCrack": {"reached": false, "roastSeconds": null},
  "alarms": {"burned": false, "baked": false, "burnWarning": false, "alarmLimitExceeded": false, "chargeOvershoot": false}
}
```

- `tick` es el segundo simulado desde el encendido (el `elapsedSeconds` de la web) y `roastSeconds` el segundo del tueste, que empieza en cero al cargar.
- `temperature` es lo que marca el termómetro: el aire antes de cargar y la sonda del grano después.
- No expongo la temperatura interna del grano ni los umbrales sorteados de los cracks, para no adelantarle el resultado al jugador.
- `sessionRequest` es exactamente el cuerpo de `POST /api/v1/roasting/sessions`, con la traza incluida.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `SIMULATION_INVALID_REQUEST` | Falta `X-User-Id`, el JSON no es válido o un valor está fuera de rango |
| 400 | `GAS_OUT_OF_RANGE` | El gas no está entre 0 y 100 |
| 404 | `SIMULATION_NOT_FOUND` | No existe, expiró o es de otro jugador |
| 404 | `ROUTE_NOT_FOUND` | La ruta no existe |
| 409 | `SIMULATION_INVALID_PHASE` | Cargar fuera del precalentado, descargar antes del punto de retorno o en su mismo tick (el pico todavía no cuenta; se reintenta al segundo siguiente) o cambiar el gas ya descargado |
| 413 | `SIMULATION_BODY_TOO_LARGE` | El cuerpo pasa `SIMULATOR_MAX_BODY_BYTES` |

## Probarlo con curl

```bash
curl -s -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" -d "{}" http://127.0.0.1:8083/api/v1/simulations
curl -s -H "X-User-Id: 1" "http://127.0.0.1:8083/api/v1/simulations/<id>?afterTick=0"
curl -s -X PUT -H "X-User-Id: 1" -H "Content-Type: application/json" -d "{\"gasPercent\": 70}" http://127.0.0.1:8083/api/v1/simulations/<id>/gas
curl -s -X POST -H "X-User-Id: 1" http://127.0.0.1:8083/api/v1/simulations/<id>/charge
curl -s -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" -d "{\"knowledgeLevel\": \"BEGINNER\", \"alerts\": []}" http://127.0.0.1:8083/api/v1/simulations/<id>/discharge
```

## Tests

```bash
npm test
```

Uso relojes simulados (`mock.timers`) para recorrer un tueste completo sin esperar, y un servidor real en un puerto libre para la API.

## Cómo reutilizo el código de la web

El dominio se escribió para el bundler de React: sus imports no llevan extensión y su `package.json` no declara módulos ES. `src/domainHooks.js` registra un hook de Node (`module.registerHooks`) que agrega la extensión `.js` y marca esos archivos como módulos ES, y `src/RoastDomain.js` es el único lugar que apunta a la carpeta del dominio. No toco nada de la web para esto.

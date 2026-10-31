# Contrato del simulador de tueste (Unity → backend)

Unity no tiene física propia: el tueste corre en el simulador de Node (`roast-simulator/`), que usa los mismos archivos que el simulador web (`toasted_vr_frontend/src/domain/roasting`). Unity solo habla con el backend, que valida el token del jugador y le reenvía cada pedido al simulador. Unity mueve las perillas (encendido, gas, carga y descarga), consulta el estado y lo muestra.

Todas las rutas piden `Authorization: Bearer <jwt del jugador>`. La base es `/api/v1/roasting/simulations`.

## El reloj

El reloj está en el servidor: cada simulación avanza sola al ritmo de la web, un tick cada 225 ms en el precalentado y uno por segundo desde la carga. Unity no manda nada para que avance. Consulta cada segundo con `?afterTick=<último tick que ya tiene>` y recibe todos los ticks posteriores, en orden. Con ellos dibuja la curva y alimenta el evaluador de alertas tick por tick, como pide [unity-proactive-alerts-contract.md](unity-proactive-alerts-contract.md).

Si el WiFi se corta un momento, el tambor sigue tostando. Al volver, la siguiente consulta trae los ticks perdidos y la curva queda completa. El servidor guarda la última hora simulada de ticks; si Unity pide algo más viejo, recibe desde el tick más antiguo que queda, y lo nota porque el primer `tick` de la lista es mayor que `afterTick + 1`.

Si nadie consulta una simulación durante 120 s, se borra y las siguientes consultas responden 404.

## Rutas

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /` | `{"chargeTemperature": 190, "targetTemperature": 210}`, opcional | `201 {"simulationId", "state"}`, ya precalentando. Reemplaza la simulación anterior del jugador |
| `GET /{simulationId}?afterTick=N` | — | `200 {"state", "ticks"}` |
| `PUT /{simulationId}/gas` | `{"gasPercent": 60}` | `200 {"state"}` |
| `POST /{simulationId}/charge` | — | `200 {"state"}` |
| `POST /{simulationId}/discharge` | `{"alerts": [{"id": "FIRST_CRACK", "t": 625}]}` | `200 {"state", "session"}` |
| `DELETE /{simulationId}` | — | `204`, para abortar |

- Se puede cargar en `PREHEAT` o `READY`, igual que en la web, y descargar en `ROASTING`.
- El `simulationId` sirve también como `roastAttemptId` de las preguntas por voz ([unity-voice-contract.md](unity-voice-contract.md)).
- `alerts` son las alertas proactivas que sonaron, con el segundo del tueste (`roastSeconds`). Viajan en la traza para el criterio `ALERT_REACTION`. Si no sonó ninguna, va la lista vacía o se omite el cuerpo.

## El estado

`state` y cada elemento de `ticks` tienen esta forma:

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

| Campo | Qué es |
|---|---|
| `tick` | Segundo simulado desde el encendido. Es el eje X de la curva |
| `phase` | `PREHEAT`, `READY`, `CHARGE_DIP`, `ROASTING` o `FINISHED` |
| `roastSeconds` | Segundo del tueste, cero en la carga. Es el reloj de la nota, del DTR, de la traza y de las alertas |
| `temperature` | Lo que marca el termómetro: el aire antes de cargar y la sonda del grano después (`°C`). Es el eje Y de la curva y la `temperature` de las alertas |
| `airTemperature` | Temperatura del aire del tambor |
| `moisturePct` | Humedad del grano; con ella funciona la alerta `SCORCH_RISK` |
| `gasPercent` | Gas con el que corre el quemador (0 a 100) |
| `risePerMinute` | Subida en °C por minuto, la misma de la web |
| `chargeTemperature` | La que se planeó al empezar y, desde la carga, la del aire en el momento real de cargar |
| `grainState` | `DRYING`, `MAILLARD`, `FIRST_CRACK`, `DARK`, `SECOND_CRACK` o `BURNED`, para el color del grano |
| `firstCrack`, `secondCrack` | Si ya pasaron y en qué `roastSeconds` |
| `alarms.burned` | El café se quemó |
| `alarms.baked` | El café se horneó (Maillard estancado) |
| `alarms.burnWarning` | Se acerca al límite de quemado |
| `alarms.alarmLimitExceeded` | El termómetro pasó la alarma de seguridad del equipo |
| `alarms.chargeOvershoot` | Antes de cargar, el aire pasó la temperatura de carga planeada |

La temperatura interna del grano y los umbrales sorteados de los cracks no se muestran, para no adelantarle el resultado al jugador.

## La descarga y la nota

Al descargar, el backend saca el nivel del jugador de la base y el simulador calcula la nota con las mismas reglas de la web. El backend arma la traza con las alertas que mandó Unity y guarda la sesión por el mismo camino que la web: valida que sea coherente (RF015) y la deja lista para la calificación de la IA. Unity no hace `POST /api/v1/roasting/sessions` ni arma la traza.

`session` es la misma respuesta de `POST /api/v1/roasting/sessions`, explicada en [unity-roast-trace-contract.md](unity-roast-trace-contract.md). Casi siempre llega con `evaluation.status: PENDING`; para ver la nota final, Unity pide `GET /api/v1/roasting/sessions/{session.id}` cada 5 s hasta que cambie, durante 2 minutos como máximo.

En el mismo tick en que la sonda llega al punto de retorno todavía no cuenta el pico. Una descarga en ese tick responde 409 y se repite al segundo siguiente.

## Errores

Todos responden `{"code": "...", "message": "..."}`, con `message` en español y listo para mostrar al jugador.

| HTTP | `code` | Cuándo | Qué hace Unity |
|---|---|---|---|
| 400 | `SIMULATION_INVALID_REQUEST` | El id no es un UUID, el JSON no es válido o falta el gas | Revisa el pedido |
| 400 | `GAS_OUT_OF_RANGE` | El gas no está entre 0 y 100 | Limita la perilla |
| 401 | — | Sin token o token inválido | Vuelve al inicio de sesión |
| 404 | `SIMULATION_NOT_FOUND` | No existe, expiró, es de otro jugador o el simulador se reinició | Ofrece empezar un tueste nuevo |
| 409 | `SIMULATION_INVALID_PHASE` | La acción no corresponde a la fase (por ejemplo, cargar dos veces) | Muestra el mensaje |
| 503 | `SIMULATOR_UNAVAILABLE` | El simulador no responde | Muestra "sin conexión" y sigue consultando |

## Probarlo sin Unity

Con el simulador corriendo (`roast-simulator/README.md`) y el backend en `127.0.0.1:8081`, desde Git Bash:

```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"<correo>","password":"<contraseña>"}' \
  http://127.0.0.1:8081/api/v1/auth/login | node -p 'JSON.parse(require("fs").readFileSync(0,"utf8")).accessToken')
API=http://127.0.0.1:8081/api/v1/roasting/simulations
ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" $API | node -p 'JSON.parse(require("fs").readFileSync(0,"utf8")).simulationId')
curl -s -H "Authorization: Bearer $TOKEN" "$API/$ID?afterTick=0"
```

El token dura 10 minutos.

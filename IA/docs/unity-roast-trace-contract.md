# Contrato de la traza del tueste (Unity → backend)

Desde la fase 5 cada sesión tiene una **nota final** que combina dos partes: el 70 % es la nota de las reglas (`qualityScore`, la misma de siempre) y el 30 % es la nota de las decisiones del jugador que da la IA con una rúbrica. Para calificar las decisiones necesito saber qué hizo el jugador durante el tueste, y eso viaja en la **traza**: un campo opcional `roastTrace` dentro del mismo `POST /api/v1/roasting/sessions` que Unity ya envía.

La traza es opcional. Si Unity no la manda, la sesión se guarda igual que antes y su nota final es la de las reglas. La web ya la envía (`toasted_vr_frontend/src/domain/roasting/RoastTraceRecorder.js`), y ese archivo sirve de referencia para la implementación en C#.

## Qué se envía

```json
{
  "chargeTemperature": 190.0,
  "...": "los campos de siempre",
  "roastTrace": {
    "version": 1,
    "operationMode": "MANUAL",
    "sampleIntervalSeconds": 5,
    "samples": [
      { "t": 1, "bean": 108.59, "gas": 30 },
      { "t": 5, "bean": 109.4, "gas": 30 },
      { "t": 756, "bean": 204.81, "gas": 25 }
    ],
    "gasChanges": [ { "t": 604, "gas": 25 } ],
    "events": [
      { "type": "CHARGE", "t": 0 },
      { "type": "FIRST_CRACK", "t": 604 },
      { "type": "DISCHARGE", "t": 756 }
    ],
    "alerts": [ { "id": "BURN_WARNING", "t": 700 } ]
  }
}
```

**El reloj `t`** es el segundo del tueste: cero en la carga del café, el mismo reloj de `totalDurationSeconds` y del primer crack. El precalentado no entra en la traza.

| Campo | Qué es |
|---|---|
| `version` | Siempre `1`. |
| `operationMode` | `MANUAL` o `AUTO`. Si el control automático manejó el gas en **cualquier** momento después de la carga, va `AUTO`. |
| `sampleIntervalSeconds` | Cada cuántos segundos hay una muestra. Uso 5. |
| `samples` | Una muestra cada `sampleIntervalSeconds`, más la primera después de la carga y la última antes de descargar. `bean` es la temperatura del grano (°C, dos decimales) y `gas` la potencia del quemador (0 a 100) con la que corrió ese segundo. Mínimo 2, en orden y sin segundos repetidos. |
| `gasChanges` | Cada vez que **el jugador** cambió el gas: el segundo y el gas nuevo. En `AUTO` va vacío. En orden cronológico. |
| `events` | `CHARGE` (siempre `t: 0`), `FIRST_CRACK`, `SECOND_CRACK` si pasó, y `DISCHARGE` (igual a `totalDurationSeconds`). Cada tipo una sola vez. |
| `alerts` | Las alertas proactivas que sonaron (ver [unity-proactive-alerts-contract.md](unity-proactive-alerts-contract.md)): su `id` del catálogo y el segundo en que sonaron. La web todavía no emite alertas, así que manda la lista vacía. |

La muestra 0 lleva el gas con el que se cargó: con ella sé desde qué gas partió el jugador.

## Límites

Son los de `app.roast-trace` en `Toasted_VR/src/main/resources/application.yml`:

| Límite | Valor |
|---|---|
| Muestras | 400 (con una cada 5 s alcanza para 33 min de tueste) |
| Cambios de gas | 120 |
| Eventos | 10 |
| Alertas | 60 |
| Largo del id de una alerta | 40 caracteres |
| Tolerancia de tiempo | 5 s |
| Tolerancia de temperatura | 1 °C |

## Qué revisa el backend

La traza tiene que cuadrar con la sesión que la acompaña: ningún segundo pasa de `totalDurationSeconds` + 5 s, la muestra más caliente no pasa de `peakTemperature` + 1 °C, hay `FIRST_CRACK` si y solo si `firstCrackReached` es `true`, el desarrollo (`totalDurationSeconds` − primer crack) coincide con `developmentTimeSeconds` y la descarga coincide con `totalDurationSeconds`, todo con la tolerancia de 5 s.

Si algo no cuadra, **la sesión se guarda igual**: el backend descarta la traza, deja anotado el motivo en la auditoría y la nota final queda con las reglas (`skipReason: INVALID_TRACE`). Una traza mala nunca hace fallar el guardado.

## Qué devuelve

La respuesta del `POST` y de `GET /api/v1/roasting/sessions/{id}` trae tres campos nuevos:

```json
{
  "qualityScore": 74,
  "finalScore": 80,
  "grade": "GOOD",
  "evaluation": {
    "status": "COMPLETED",
    "skipReason": null,
    "finalScore": 80,
    "rulesScore": 74,
    "rubricScore": 93.3,
    "rulesWeight": 0.7,
    "aiWeight": 0.3,
    "defectCapApplied": false,
    "rubricVersion": "draft-2026-10-30",
    "model": "openai/gpt-oss-120b",
    "criteria": [
      { "id": "DRYING_GAS", "level": 3, "maxLevel": 3, "justification": "..." }
    ],
    "notApplicable": ["ALERT_REACTION"]
  }
}
```

- `finalScore` es la nota que vale. Mientras la IA no calificó, es la de las reglas.
- `grade` sale de la nota final: `EXCELLENT` (85 o más), `GOOD` (70 a 84), `FAIR` (50 a 69) y `POOR` (menos de 50).
- `evaluation.status`:
  - `PENDING`: la IA califica en segundo plano. Al guardar casi siempre llega así; Unity vuelve a pedir `GET /api/v1/roasting/sessions/{id}` cada 5 s hasta que cambie (la web se rinde a los 2 min).
  - `COMPLETED`: ya tiene la nota final combinada y la justificación de cada criterio.
  - `NOT_APPLICABLE`: la IA no califica, y `skipReason` dice por qué (`NO_TRACE`, `INVALID_TRACE`, `AUTO_MODE`, `NO_CRITERIA`). Vale la nota de las reglas.
  - `FAILED`: Groq no respondió después de todos los reintentos. Vale la nota de las reglas.
- Si el resultado tiene un defecto (`RAW`, `BURNED` o `BAKED`), la nota final no pasa de 50 (`defectCapApplied: true`).

## Los criterios

La rúbrica está en `IA/config/roast_rubric.json`. El modelo solo elige un nivel de 0 a 3 por criterio y una justificación; los hechos los mide el código sobre la traza, así que Unity no tiene que calcular nada.

| Criterio | Qué mira | Cuándo no aplica |
|---|---|---|
| `DRYING_GAS` | Si la subida del secado estuvo en la franja y si el jugador corrigió el gas a tiempo y en la dirección correcta | Sin punto de retorno |
| `RISE_TO_FIRST_CRACK` | Estancamientos y minuto del primer crack (8 a 12 min) | El grano no llegó a 160 °C |
| `GAS_AFTER_FIRST_CRACK` | Si bajó el gas después del primer crack, cuánto y qué tan rápido | Sin primer crack, o con menos de 30 s de desarrollo si el jugador todavía no había bajado el gas 5 puntos |
| `ALERT_REACTION` | Si reaccionó a las alertas en los 20 s siguientes | Sin alertas de las de la tabla de abajo |

Las alertas que cuentan para `ALERT_REACTION` y la reacción que se espera:

| Alerta | Reacción esperada |
|---|---|
| `DRYING_RISE_FAST`, `BURN_WARNING`, `SCORCH_RISK` | Bajar el gas |
| `DRYING_RISE_SLOW`, `TEMP_STALL_DRYING`, `TEMP_STALL_MAILLARD` | Subir el gas |
| `SECOND_CRACK_NEAR`, `BURN_CEILING_EXCEEDED` | Bajar el gas o descargar |
| `DISCHARGE_WINDOW_CLOSING` | Descargar |

Las demás alertas del catálogo se pueden mandar, pero no entran en la nota.

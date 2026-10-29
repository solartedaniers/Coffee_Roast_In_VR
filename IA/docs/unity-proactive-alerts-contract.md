# Contrato de las alertas proactivas (Unity → backend)

Durante el tueste el asistente habla por su cuenta en los momentos clave: carga fuera de rango, subida muy rápida o muy lenta, estancamiento, primer crack, desarrollo y riesgo de quemado. Las reglas no corren en el servidor: Unity descarga al empezar el tueste un **paquete** con las reglas, la frase de su nivel y el audio ya sintetizado, y lo evalúa localmente una vez por segundo. Así no hay latencia de red, funciona sin WiFi y no se gasta la cuota de Azure en cada alerta.

El catálogo completo, con la fuente de cada umbral, está en `IA/config/proactive_alerts.json`. Este documento es la especificación que debe seguir el evaluador en C#.

## Pedido

`GET /api/v1/roasting/assistant/alerts` con `Authorization: Bearer <jwt del jugador>`.

- El backend saca el nivel del jugador del token y de la base. Unity **no** elige el nivel; un parámetro `level` se ignora.
- Si el jugador no tiene nivel, recibe el paquete INTERMEDIATE.
- Unity manda `If-None-Match: <ETag guardado>` si ya tiene un paquete descargado.

| HTTP | Cuándo | Qué hace Unity |
|---|---|---|
| 200 | Paquete nuevo o distinto al guardado | Lo guarda junto con su `ETag` y lo usa |
| 304 | El paquete guardado sigue vigente (sin cuerpo) | Usa el guardado |
| 401 | Sin token o token inválido | Usa el guardado o el del build |
| 503 | `{"code": "ALERTS_UNAVAILABLE", "message": "..."}`: el servicio IA no responde | Usa el guardado o el del build |

Sugerencia: esperar hasta 30 s. El ETag es distinto por nivel: si el jugador cambia de nivel, el guardado no coincide y llega el paquete nuevo.

Tamaño medido (revisión 2, todo con voz de Azure): BEGINNER 7,5 MB, INTERMEDIATE 4,2 MB, ADVANCED 2,0 MB. Con el ETag, la descarga se hace una sola vez por versión.

## Paquete de respaldo y regla de versión

El build de Unity trae un paquete por nivel (descargado con este mismo endpoint). Al empezar el tueste Unity elige así:

1. Si hay paquete descargado, su `schemaVersion` es la que Unity entiende (hoy `1`) y su `catalogRevision` es **mayor o igual** al del build, usa el descargado.
2. Si no, usa el del build.

Un `schemaVersion` desconocido significa que cambió la forma de las reglas: ese paquete se descarta.

## Formato del paquete

```json
{
  "schemaVersion": 1,
  "catalogRevision": 2,
  "level": "BEGINNER",
  "signals": {"riseWindowSeconds": 30, "maillardStartTemperature": 160, "maillardEndTemperature": 200,
              "burnCeilingTemperature": 220, "turningPointMinRise": 0.5},
  "channel": {"queueMaxWaitSeconds": 5, "minGapSeconds": 8,
              "urgentPriorities": ["CRITICAL"], "unqueuedPriorities": ["LOW"]},
  "rules": [
    {
      "id": "BURN_WARNING",
      "priority": "HIGH",
      "when": [{"signal": "phase", "op": "eq", "value": "ROASTING"},
               {"signal": "peakTemperature", "op": "le", "value": 220},
               {"signal": "temperature", "op": "ge", "value": 215}],
      "sustainSeconds": 1,
      "rearmWhen": [{"signal": "temperature", "op": "lt", "value": 213}],
      "cooldownSeconds": 20,
      "maxRepeats": 2,
      "requiresQuietSeconds": 0,
      "text": "Cuidado, el grano se acerca al límite de quemado de 220 grados. ...",
      "audio": {"format": "wav", "sampleRateHz": 22050, "provider": "azure", "base64": "UklGR..."}
    }
  ]
}
```

- `rules` viene en el orden del catálogo, que es también el orden de desempate (ver el canal).
- `audio` es WAV PCM16 mono. Puede venir `null` si no se pudo sintetizar: Unity muestra solo `text` y el siguiente pedido trae un ETag distinto cuando el audio exista.
- Las frases no llevan cifras en vivo, solo constantes fijas, porque el audio se genera antes del tueste.

## Entradas por segundo

Unity llama al evaluador una vez por **tick del simulador** (un segundo simulado), en orden, desde que el tambor llega a READY hasta la descarga. Una muestra trae:

| Campo | Qué es |
|---|---|
| `phase` | `PREHEAT`, `READY`, `CHARGE_DIP` o `ROASTING` |
| `temperature` | La que muestra el termómetro: el aire antes de cargar y el grano después (el `displayTemp` del simulador) |
| `roastSeconds` | `roastingElapsedSeconds`: el mismo reloj de la nota y del DTR. Es 0 antes de cargar |
| `firstCrackReached` | Si ya hubo primer crack |
| `moisturePct` | Humedad del grano. Opcional: **`SCORCH_RISK` solo funciona si Unity la envía** |
| `recording` | El jugador está grabando una pregunta |
| `assistantBusy` | El asistente está esperando o reproduciendo una respuesta |

En PREHEAT y READY el simulador comprime el reloj real (225 ms por tick). Lo que cuenta es el tick, no el reloj real. Desde la carga cada tick dura 1 s real.

## Señales

Se calculan en este orden en cada tick. `charged` significa fase `CHARGE_DIP` o `ROASTING`.

| Señal | Cálculo |
|---|---|
| `temperature`, `roastSeconds`, `phase`, `firstCrackReached`, `moisturePct` | La muestra tal cual |
| `secondsInPhase` | 1 en el primer tick de una fase, +1 en cada tick siguiente de la misma fase |
| `risePerMinute` | Ventana de las últimas `riseWindowSeconds + 1` temperaturas. Al entrar a una fase `charged` desde una que no lo es, la ventana se **vacía** antes de agregar la muestra. Con `n` muestras: `n < 2` da 0; si no, `round((última − primera) / (n − 1) × 60)` con el redondeo de `Math.round` de JavaScript: `floor(x + 0.5)` (−2,5 da −2, no −3) |
| `peakTemperature` | Máxima temperatura desde la carga; `null` antes de cargar |
| `secondsAboveCeiling` | Solo `charged`: +1 si `temperature > burnCeilingTemperature`, si no 0. Antes de cargar vale 0 |
| `turningPointReached` | Solo `charged`: se guarda la mínima temperatura desde la carga; pasa a verdadero (y no vuelve a falso) cuando `temperature − mínima > 0` y `≥ turningPointMinRise` |
| `stagnationSeconds` | Igual que `MaillardStagnationMonitor`. Solo cambia en `ROASTING`, si el tick **anterior** no tenía primer crack y `maillardStart ≤ temperature ≤ maillardEnd`: +1 si `temperature ≤` la temperatura de **dos ticks atrás** (o la más vieja que haya), si no 0. En cualquier otro caso conserva su valor |
| `developmentRatio` | 0 sin primer crack. Si no, `(roastSeconds − roastSeconds del primer tick con primer crack) / roastSeconds` |

## Reglas

Una condición se cumple si la señal tiene valor y `señal op valor` es verdadero (`eq`, `ne`, `lt`, `le`, `gt`, `ge`). Una señal sin valor (la humedad si no se envía, el pico antes de cargar) **nunca** cumple. `when` se cumple si se cumplen todas sus condiciones.

Cada regla lleva este estado: `held` (segundos seguidos con `when` activo), `armed` (empieza en verdadero), `timesFired` y `lastFiredSecond`. En cada tick (`second` cuenta los ticks desde 0):

1. `held = when ? held + 1 : 0`.
2. Si no está armada: con `rearmWhen` en `null` se re-arma cuando `when` deja de cumplirse; con una lista, cuando se cumplen todas sus condiciones (una lista vacía re-arma de inmediato).
3. Se dispara si está armada, `held ≥ sustainSeconds`, `timesFired < maxRepeats`, pasaron `cooldownSeconds` desde `lastFiredSecond` (o nunca se disparó) y pasaron `requiresQuietSeconds` desde la última alerta entregada. Al dispararse: `armed = false`, `timesFired += 1`, `lastFiredSecond = second`.

Todas las reglas se evalúan en cada tick, aunque el canal esté ocupado. `requiresQuietSeconds` usa la última entrega **antes** de este tick.

## Canal de voz

Hay un solo canal. En cada tick:

1. Se descartan de la cola las alertas que llevan más de `queueMaxWaitSeconds` esperando o cuyo `when` ya no se cumple.
2. Candidatas = la cola + las reglas disparadas en este tick. Se ordenan por prioridad (`CRITICAL`, `HIGH`, `NORMAL`, `LOW`) y luego por el segundo en que entraron (las disparadas en el mismo tick, en el orden del paquete).
3. La primera se entrega si su prioridad está en `urgentPriorities`, o si el asistente no está ocupado y pasaron `minGapSeconds` desde la última entrega.
4. Las demás quedan en la cola, salvo las de `unqueuedPriorities`, que se descartan. Como máximo se entrega una alerta por tick.

**Cómo se entrega**: con `recording` verdadero solo se muestra el subtítulo (para que Whisper no transcriba la alerta). En otro caso suena el audio y se muestra el subtítulo. Una alerta `CRITICAL` corta la respuesta que esté sonando.

## Catálogo (revisión 2)

| id | Prioridad | Niveles | Cuándo |
|---|---|---|---|
| CHARGE_TEMP_LOW / CHARGE_TEMP_HIGH | NORMAL | B I A | READY, < 180 °C o > 200 °C durante 3 s |
| DRYING_RISE_FAST | NORMAL | B I | Después del punto de retorno, < 160 °C, subida ≥ 25 °C/min durante 10 s |
| DRYING_RISE_SLOW | NORMAL | B I | Después del punto de retorno, 100–160 °C, subida entre 0 y 5 durante 20 s |
| TEMP_STALL_DRYING | HIGH | B I A | Después del punto de retorno, < 160 °C, subida ≤ 0 durante 10 s |
| TEMP_STALL_MAILLARD | HIGH | B I A | Sin primer crack, 160–200 °C, 15 s estancado (se hornea a los 30) |
| FIRST_CRACK_NEAR | NORMAL | B I | Sin primer crack, ≥ 190 °C |
| FIRST_CRACK | HIGH | B I A | Primer crack |
| ROAST_RUNNING_LONG | NORMAL | B I A | 13 min de tueste sin primer crack |
| BURN_WARNING | HIGH | B I A | ≥ 215 °C sin haber pasado 220 |
| SECOND_CRACK_NEAR | CRITICAL | B I A | ≥ 218 °C sin haber pasado 220 |
| BURN_CEILING_EXCEEDED | CRITICAL | B I A | Pasó de 220 °C |
| SCORCH_RISK | CRITICAL | B I A | Subida ≥ 30 °C/min con humedad ≤ 3 % (requiere la humedad) |
| DEVELOPMENT_READY | HIGH | B I A | DTR ≥ 18 % |
| DISCHARGE_WINDOW_CLOSING | HIGH | B I A | DTR ≥ 22 %, se repite una vez a los 20 s |
| ON_TRACK_DRYING / ON_TRACK_MAILLARD | LOW | B I | Buen ritmo durante 60 s sin otra alerta |

## Vectores de conformidad

`IA/eval/alerts/conformance.json` trae 7 curvas del simulador (buena, quemada, horneada, cruda, con caída, con carga baja y sobredesarrollada). Cada una tiene:

- `samples`: una muestra por tick, con los campos de `sampleFields` (`phase`, `temperature`, `roastSeconds`, `firstCrackReached`, `moisturePct`, `recording`, `assistantBusy`).
- `expected`: por nivel, las alertas entregadas con `second`, `roastSeconds`, `alertId` y `mode` (`VOICE` o `SUBTITLE`).

El evaluador en C# pasa la prueba si, con el catálogo de la misma `catalogRevision`, entrega **exactamente** la misma lista en las 7 curvas y los 3 niveles. La curva buena incluye una pregunta grabada justo en el primer crack, para probar el modo de solo subtítulo.

Conteo esperado por tueste (BEGINNER / INTERMEDIATE / ADVANCED): buena 5/5/2, quemada 10/10/7, horneada 8/8/5, cruda 1/1/0, con caída 6/6/3, carga baja 6/6/3, sobredesarrollada 7/7/4.

## Probarlo sin Unity

Desde `IA/`:

```bash
# Qué suena en cada curva de prueba, en qué segundo y con qué frase
.venv/Scripts/python scripts/alert_simulator.py --phrases
# Curvas propias exportadas a CSV (una fila por tick)
.venv/Scripts/python scripts/alert_simulator.py --csv curva.csv
# Regenerar los vectores después de cambiar el catálogo
.venv/Scripts/python -m app.alerts.conformance
```

## Observación pendiente (sin cambiar la física)

En las curvas del simulador, después del primer crack el resultado es muy sensible al gas: con el 25 % el tueste sale bien (PERFECT 84–90), con el 30 % el grano llega a 216–220 °C y con el 15 % se enfría hasta quedar crudo. Las alertas avisan en los dos extremos (aviso a 215 °C y descarga por DTR), pero el margen de control es estrecho. Queda anotado para revisarlo con la física del simulador; no se cambió nada en esta fase.

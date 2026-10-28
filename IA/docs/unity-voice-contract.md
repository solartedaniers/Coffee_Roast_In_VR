# Contrato de la pregunta por voz (Unity → backend)

Así es como Unity le hace una pregunta por voz al asistente durante el tueste. Unity solo habla con el backend; el backend agrega el usuario y su nivel (los saca del token y de la base) y le pasa la pregunta al servicio IA.

## Pedido

`POST /api/v1/roasting/assistant/questions` con `Authorization: Bearer <jwt del jugador>` y cuerpo `multipart/form-data` con dos partes:

| Parte | Contenido |
|---|---|
| `audio` | La pregunta grabada: WAV PCM 16 bits, mono, 16 kHz. Entre 0,3 s y 15 s, máximo 600 000 bytes. |
| `context` | Texto JSON con el intento y el estado del tueste (abajo). |

```json
{
  "roastAttemptId": "3f6c1b52-9a1e-4c55-9d7e-2b8f0c4a7e11",
  "roastState": {
    "elapsedSeconds": 510,
    "beanTemperature": 198.0,
    "firstCrackReached": false,
    "targetTemperature": 205.0,
    "chargeTemperature": 190.0,
    "firstCrackElapsedSeconds": null,
    "heatLevelPercent": 80,
    "temperatureRisePerMinute": 14
  }
}
```

- `roastAttemptId` es un UUID que genera Unity al empezar cada tueste y repite en todas las preguntas de ese tueste. Con él el asistente recuerda las últimas respuestas y entiende preguntas de seguimiento ("¿y cuánto falta para eso?").
- Obligatorios: `elapsedSeconds`, `beanTemperature`, `firstCrackReached`. El resto es opcional; si falta, el asistente responde sin ese dato.
- `heatLevelPercent` va de 0 a 100. `temperatureRisePerMinute` es en °C por minuto.
- Unity no manda `userId` ni `knowledgeLevel`: los pone el backend.

## Respuesta 200

```json
{
  "status": "ANSWERED",
  "question": "¿Debería bajar el gas ahora?",
  "answer": "Texto de 2 o 3 frases para mostrar como subtítulo.",
  "citations": [{"fileName": "cafe-manual-de-tostado_1.pdf", "pageNumber": 8, "title": "Manual básico..."}],
  "audio": {"format": "wav", "sampleRateHz": 22050, "base64": "UklGR..."},
  "providers": {"transcription": "groq", "answer": "groq", "speech": "azure"},
  "latency": {"transcriptionMillis": 1047, "retrievalMillis": 719, "answerMillis": 1219, "speechMillis": 2953, "totalMillis": 5938}
}
```

- `status`:
  - `ANSWERED`: respuesta basada en los PDFs o en el estado del tueste.
  - `ABSTAINED`: no hay información para responder; `answer` dice que no sabe e invita a preguntar sobre el tueste.
  - `UNAVAILABLE`: algún proveedor falló; `answer` es una frase fija ("No puedo responder en este momento, sigue con tu tueste.").
- `audio` es un WAV PCM16 mono codificado en base64 (22 050 Hz con Azure o con Piper). Puede venir `null` si no se pudo generar ninguna voz: en ese caso Unity muestra solo el texto.
- `question` es lo que entendió la transcripción; puede ser `null` en `UNAVAILABLE`.

## Errores

Todos responden `{"code": "...", "message": "..."}`. `message` ya está en español y se puede mostrar al jugador.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `AUDIO_INVALID` | Falta el audio o no es WAV PCM16 mono 16 kHz |
| 400 | `AUDIO_TOO_SHORT` / `AUDIO_TOO_LONG` | Menos de 0,3 s o más de 15 s |
| 400 | `CONTEXT_INVALID` | Falta `context` o su JSON no es válido |
| 401 | — | Sin token o token inválido |
| 409 | `ASSISTANT_BUSY` | El jugador ya tiene una pregunta en curso |
| 413 | `AUDIO_TOO_LARGE` | El audio pasa el tamaño máximo |
| 422 | `SILENT_AUDIO` | No se detectó voz en la grabación |
| 429 | `RATE_LIMITED` | Más de 6 preguntas por minuto |
| 503 | `ASSISTANT_UNAVAILABLE` | El servicio IA no responde |

Sugerencia para Unity: esperar hasta 30 s, bloquear el botón de hablar mientras hay una pregunta en curso y, con 422, pedirle al jugador que repita.

## Probarlo sin Unity

Desde `IA/`, con un WAV de la pregunta:

```bash
# Directo al servicio IA
.venv/Scripts/python scripts/voice_probe.py pregunta.wav --level BEGINNER --heat 80 --rise 14

# A través del backend, como lo hará Unity
.venv/Scripts/python scripts/voice_probe.py pregunta.wav --backend http://127.0.0.1:8081 --token <jwt>
```

El script muestra el JSON y la latencia, reproduce la respuesta (en Windows) y con `--save respuesta.wav` la guarda. Para una pregunta de seguimiento repito `--attempt <mismo uuid>`.

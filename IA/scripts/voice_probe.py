"""Prueba la pregunta por voz sin Unity: manda un WAV, muestra la respuesta y reproduce el audio.

Directo al servicio IA (sin autenticación, como lo llama el backend):
    .venv/Scripts/python scripts/voice_probe.py pregunta.wav

A través del backend, como lo hará Unity (con el JWT de un jugador):
    .venv/Scripts/python scripts/voice_probe.py pregunta.wav --backend http://127.0.0.1:8081 --token <jwt>
"""
import argparse
import base64
import json
import sys
import time
import uuid
from pathlib import Path

import httpx

IA_PATH = "/api/v1/voice/questions"
BACKEND_PATH = "/api/v1/roasting/assistant/questions"
DEFAULT_IA_URL = "http://127.0.0.1:8082"
TIMEOUT_SECONDS = 60


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("wav", type=Path, help="WAV PCM16 mono 16 kHz con la pregunta")
    parser.add_argument("--ia", default=DEFAULT_IA_URL, help="URL del servicio IA")
    parser.add_argument("--backend", help="URL del backend; si se da, la pregunta pasa por él")
    parser.add_argument("--token", help="JWT del jugador (solo con --backend)")
    parser.add_argument("--user-id", type=int, default=1, help="Usuario (solo directo al servicio IA)")
    parser.add_argument("--level", default="BEGINNER", choices=["BEGINNER", "INTERMEDIATE", "ADVANCED"])
    parser.add_argument("--attempt", default=str(uuid.uuid4()), help="roastAttemptId; repítelo para seguir la charla")
    parser.add_argument("--elapsed", type=float, default=420, help="Segundos de tueste")
    parser.add_argument("--bean", type=float, default=192.0, help="Temperatura del grano (°C)")
    parser.add_argument("--target", type=float, default=205.0)
    parser.add_argument("--charge", type=float, default=190.0)
    parser.add_argument("--first-crack-at", type=float, help="Segundo en que ocurrió el primer crack")
    parser.add_argument("--heat", type=float, help="Nivel de calor del quemador (0-100)")
    parser.add_argument("--rise", type=float, help="Subida de temperatura en °C por minuto")
    parser.add_argument("--save", type=Path, help="Guarda el audio de la respuesta en este archivo")
    parser.add_argument("--no-play", action="store_true", help="No reproduce el audio")
    return parser.parse_args()


def context_for(args: argparse.Namespace) -> dict:
    state = {"elapsedSeconds": args.elapsed, "beanTemperature": args.bean, "targetTemperature": args.target,
             "chargeTemperature": args.charge, "firstCrackReached": args.first_crack_at is not None,
             "firstCrackElapsedSeconds": args.first_crack_at, "heatLevelPercent": args.heat,
             "temperatureRisePerMinute": args.rise}
    context = {"roastAttemptId": args.attempt, "roastState": state}
    if not args.backend:
        context |= {"userId": args.user_id, "knowledgeLevel": args.level}
    return context


def play(wav: bytes) -> None:
    if sys.platform != "win32":
        print("(reproducción automática solo en Windows; usa --save)")
        return
    import winsound
    winsound.PlaySound(wav, winsound.SND_MEMORY)


def main() -> int:
    args = parse_args()
    # La consola de Windows usa cp1252 y no puede mostrar todo lo que escribe el modelo.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    url = (args.backend.rstrip("/") + BACKEND_PATH) if args.backend else (args.ia.rstrip("/") + IA_PATH)
    headers = {"Authorization": f"Bearer {args.token}"} if args.token else {}
    started = time.monotonic()
    response = httpx.post(url, headers=headers, timeout=TIMEOUT_SECONDS, trust_env=False,
                          files={"audio": ("question.wav", args.wav.read_bytes(), "audio/wav")},
                          data={"context": json.dumps(context_for(args))})
    elapsed_millis = round((time.monotonic() - started) * 1000)
    print(f"HTTP {response.status_code} en {elapsed_millis} ms (medido por el cliente)")
    body = response.json()
    audio = body.pop("audio", None)
    print(json.dumps(body, ensure_ascii=False, indent=2))
    if not audio:
        return 0 if response.is_success else 1
    wav = base64.b64decode(audio["base64"])
    print(f"Audio: {len(wav)} bytes a {audio['sampleRateHz']} Hz")
    if args.save:
        args.save.write_bytes(wav)
    if not args.no_play:
        play(wav)
    return 0


if __name__ == "__main__":
    sys.exit(main())

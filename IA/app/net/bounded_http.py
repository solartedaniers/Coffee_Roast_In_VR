"""Llamadas HTTP acotadas a los proveedores configurados (adaptado de remote_http de Musiyo API).

Solo se permiten los orígenes configurados, sin redirecciones ni proxies heredados del sistema,
con respuestas de tamaño limitado. Los errores nunca llevan la llave ni el cuerpo de la respuesta.
"""
import json
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlsplit

import httpx

USER_AGENT = "ToastedVR-AI/1.0"


class RemoteServiceError(Exception):
    """El servicio remoto no respondió algo utilizable."""


@dataclass(frozen=True)
class HttpTimeout:
    connect_seconds: float
    read_seconds: float


def origin_of(url: str) -> str:
    parts = urlsplit(url)
    if parts.scheme not in {"http", "https"} or not parts.hostname:
        raise ValueError(f"Unsupported URL: {url!r}")
    port = parts.port or (443 if parts.scheme == "https" else 80)
    return f"{parts.scheme}://{parts.hostname.lower()}:{port}"


class BoundedHttpClient:
    def __init__(self, allowed_base_urls: list[str], max_response_bytes: int,
                 transport: httpx.BaseTransport | None = None):
        self.allowed_origins = {origin_of(url) for url in allowed_base_urls}
        self.max_response_bytes = max_response_bytes
        self.transport = transport

    def post_json(self, url: str, payload: dict[str, Any], headers: dict[str, str], timeout: HttpTimeout) -> Any:
        parts = urlsplit(url)
        if origin_of(url) not in self.allowed_origins or parts.username or parts.password:
            raise RemoteServiceError("URL is not allowed")
        request_headers = headers | {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": USER_AGENT,
        }
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        try:
            with httpx.Client(transport=self.transport, trust_env=False, follow_redirects=False,
                              timeout=httpx.Timeout(timeout.read_seconds, connect=timeout.connect_seconds)) as client:
                with client.stream("POST", url, content=body, headers=request_headers) as response:
                    if response.status_code != 200:
                        # 401/403 (llave), 429 (cuota) y 5xx: el proveedor no está disponible.
                        raise RemoteServiceError(f"Remote service returned {response.status_code}")
                    data = bytearray()
                    for chunk in response.iter_bytes():
                        data.extend(chunk)
                        if len(data) > self.max_response_bytes:
                            raise RemoteServiceError("Remote response too large")
        except httpx.HTTPError as error:
            raise RemoteServiceError(f"Remote service unreachable ({type(error).__name__})") from None
        try:
            return json.loads(data)
        except (ValueError, UnicodeError):
            raise RemoteServiceError("Remote service returned invalid JSON") from None

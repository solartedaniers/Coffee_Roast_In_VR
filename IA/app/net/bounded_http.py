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
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        data = self._post(url, headers | {"Content-Type": "application/json", "Accept": "application/json"},
                          timeout, self.max_response_bytes, content=body)
        return self._parse_json(data)

    def post_multipart_json(self, url: str, fields: dict[str, str], file: tuple[str, bytes, str],
                            headers: dict[str, str], timeout: HttpTimeout) -> Any:
        """Formulario multipart con un archivo (por ejemplo el audio para Whisper); responde JSON."""
        name, content, content_type = file
        data = self._post(url, headers | {"Accept": "application/json"}, timeout, self.max_response_bytes,
                          data=fields, files={"file": (name, content, content_type)})
        return self._parse_json(data)

    def post_bytes(self, url: str, content: bytes, headers: dict[str, str], timeout: HttpTimeout,
                   max_response_bytes: int) -> bytes:
        """Cuerpo binario o de texto en ambos sentidos (audio, SSML), con su propio tamaño máximo."""
        return bytes(self._post(url, headers, timeout, max_response_bytes, content=content))

    def _post(self, url: str, headers: dict[str, str], timeout: HttpTimeout, max_response_bytes: int,
              **body: Any) -> bytearray:
        parts = urlsplit(url)
        if origin_of(url) not in self.allowed_origins or parts.username or parts.password:
            raise RemoteServiceError("URL is not allowed")
        request_headers = headers | {"User-Agent": USER_AGENT}
        try:
            with httpx.Client(transport=self.transport, trust_env=False, follow_redirects=False,
                              timeout=httpx.Timeout(timeout.read_seconds, connect=timeout.connect_seconds)) as client:
                with client.stream("POST", url, headers=request_headers, **body) as response:
                    if response.status_code != 200:
                        # 401/403 (llave), 429 (cuota) y 5xx: el proveedor no está disponible.
                        raise RemoteServiceError(f"Remote service returned {response.status_code}")
                    data = bytearray()
                    for chunk in response.iter_bytes():
                        data.extend(chunk)
                        if len(data) > max_response_bytes:
                            raise RemoteServiceError("Remote response too large")
        except httpx.HTTPError as error:
            raise RemoteServiceError(f"Remote service unreachable ({type(error).__name__})") from None
        return data

    @staticmethod
    def _parse_json(data: bytearray) -> Any:
        try:
            return json.loads(data)
        except (ValueError, UnicodeError):
            raise RemoteServiceError("Remote service returned invalid JSON") from None

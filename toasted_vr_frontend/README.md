# 🖥️☕ Frontend de Toasted VR

Es la aplicación web del simulador, hecha en React con Create React App. Tiene el registro y el inicio de sesión, el simulador de tueste que corre la física en el navegador, el historial de sesiones, el ranking y la retroalimentación de la IA. Solo habla con el backend (`Toasted_VR`), nunca directo con el servicio IA ni con el simulador para Unity.

## 🗂️ Estructura

| Carpeta | Qué tiene |
|---|---|
| 🧩 `src/components` | Componentes de la interfaz. |
| 🪝 `src/hooks` | Hooks propios. |
| 🔌 `src/services` | Llamadas a la API del backend. |
| ⚙️ `src/config` | Lectura de las variables de entorno. |
| 🌐 `src/locals/es.json` | Todos los textos visibles. |
| 🔥 `src/domain/roasting` | La física del tueste y la nota. El simulador para Unity (`roast-simulator/`) importa esta misma carpeta, así que los dos usan exactamente la misma física. |

## 📋 Requisitos

Node 20 o más nuevo (el CI y la imagen de Docker usan Node 20) y npm.

## 💻 Cómo lo corro en el host

```bash
cd toasted_vr_frontend
npm install
npm start
```

🌐 Queda en `http://localhost:3000` y por defecto llama al backend en `http://localhost:8081/api/v1`.

📦 Para generar el build de producción en `build/`:

```bash
npm run build
```

## 🔑 Variables de entorno

Copio `.env.example` a `.env` si quiero cambiar algún valor; el `.env` real nunca se sube.

| Variable | Qué es |
|---|---|
| 🔗 `REACT_APP_API_BASE_URL` | URL base de la API del backend. |
| ⏱️ `REACT_APP_EVALUATION_POLL_INTERVAL_MS` | Cada cuánto consulto si la calificación de la sesión ya está lista. |
| 🔁 `REACT_APP_EVALUATION_POLL_MAX_ATTEMPTS` | Cuántas veces la consulto antes de dejar de esperar. |

## 🐳 Docker

`docker-compose.frontend.yml` construye la aplicación con Node 20 y la sirve con nginx en el puerto `3000` del host. Lo levanto junto con los demás servicios desde la raíz del repositorio, en PowerShell:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml up --build -d
```

💡 `REACT_APP_API_BASE_URL` no queda fija en la imagen: el build lleva un marcador y `entrypoint.sh` lo reemplaza por el valor real cada vez que arranca el contenedor. Así la misma imagen sirve para cualquier URL del backend; para cambiarla defino la variable y recreo solo el contenedor del frontend, sin reconstruir (los comandos están en `Toasted_VR/README.md`).

## 🧪 Tests

```bash
CI=true npm test -- --watchAll=false
```

⚠️ Sin `CI=true`, `npm test` se queda en modo interactivo esperando cambios. En el CI el job `frontend-tests` corre `npm ci`, los tests y `npm run build`.
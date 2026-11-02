# ⚙️☕ Backend de Toasted VR

## 🐳 Docker y pruebas en la red local

El archivo Compose del backend es un override del servicio de PostgreSQL que ya tiene el Compose del repositorio. Corro los comandos de abajo desde `Toasted_VR`; reutilizan el contenedor `toasted_vr_postgres`, su red de Compose y su volumen con nombre que ya existe. ⚠️ Nunca le agrego `-v` al comando `down`, porque esa opción borra los volúmenes de Compose.

🔑 Si hace falta, copio `.env.example` a `.env` y pongo valores privados en `POSTGRES_PASSWORD`, `APP_JWT_SECRET`, `UNITY_CODE_HMAC_PEPPER` y `UNITY_CODE_ENCRYPTION_KEY`. `POSTGRES_PASSWORD` tiene que coincidir con la contraseña con la que se inicializó el volumen de PostgreSQL que ya existe.

🚀 Para levantar o reconstruir el stack:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml up --build -d
```

📜 Para seguir los logs del backend:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml logs -f backend
```

🩺 Para verificar desde el PC el endpoint público de la documentación de la API:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8081/v3/api-docs | Select-Object StatusCode
```

🛑 Para detener el stack sin borrar los datos de la base:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml down
```

🌐 Para saber la dirección IPv4 del PC en la red local:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object InterfaceAlias,IPAddress
```

📱 Desde otro dispositivo de la misma red local que tenga PowerShell, reemplazo los marcadores por la dirección del PC y un código de acceso de Unity real. PowerShell guarda el código en memoria solo para esta petición y el comando no lo imprime aparte:

```powershell
$pcAddress = '<PC_IPV4_ADDRESS>'
$unityCode = Read-Host 'Unity access code'
$body = @{ code = $unityCode } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "http://${pcAddress}:8081/api/v1/auth/unity-login" -ContentType 'application/json' -Body $body
Write-Output "Unity login succeeded for $($loginResponse.user.username)."
Remove-Variable unityCode,body,loginResponse
```

🥽 Configuro el cliente de Unity del Meta Quest para que llame a:

```text
http://<PC_IPV4_ADDRESS>:8081/api/v1/auth/unity-login
```

🧱 Si el Firewall de Windows bloquea el tráfico de la red local, corro esto una vez desde un PowerShell con permisos de administrador:

```powershell
New-NetFirewallRule -DisplayName 'Toasted VR backend 8081' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
```

## 📦 Stack completo (base de datos + backend + frontend + simulador) con un solo comando

El frontend tiene su propio override de Compose (`toasted_vr_frontend/docker-compose.frontend.yml`) que lo construye y lo sirve con nginx, y el simulador de tueste para Unity tiene el suyo (`roast-simulator/docker-compose.simulator.yml`). El backend encuentra al simulador por el nombre del servicio (`DOCKER_ROAST_SIMULATOR_BASE_URL`). El servicio IA (`IA/`) y Ollama se quedan a propósito fuera de todos los contenedores y siguen corriendo en el host; el backend llega al servicio IA por `host.docker.internal` (`DOCKER_AI_SERVICE_BASE_URL`).

📂 Esto lo corro desde la raíz del repositorio (no desde `Toasted_VR`):

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml up --build -d
```

🛑 Para detener el stack completo sin borrar los datos de la base:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml down
```

🖥️ El frontend queda en `http://localhost:3000`. Su `REACT_APP_API_BASE_URL` se resuelve al arrancar el contenedor, no queda fijo en el build: la imagen se construye una sola vez con un marcador y `toasted_vr_frontend/entrypoint.sh` lo reemplaza por el valor real cada vez que arranca el contenedor. Para apuntar el frontend a otra URL del backend (por ejemplo, la IPv4 del PC en la red local para un build del Meta Quest), defino `REACT_APP_API_BASE_URL` antes de levantar el stack y recreo solo el contenedor del frontend, sin reconstruir nada:

```powershell
$env:REACT_APP_API_BASE_URL = 'http://<PC_IPV4_ADDRESS>:8081/api/v1'
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml up -d --force-recreate frontend
```

## 🔑 Variables de entorno

Todas están en `.env.example`; el `.env` real nunca se sube. Estos son los grupos, solo con los nombres:

| Grupo | Variables |
|---|---|
| 🖧 Servidor | `SERVER_PORT`, `APP_CORS_ALLOWED_ORIGINS` |
| 🐘 PostgreSQL en Docker | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| 🗄️ Base de datos | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER_CLASS_NAME`, `JPA_DATABASE_PLATFORM`, `JPA_DDL_AUTO`, `JPA_SHOW_SQL` |
| 🔐 JWT | `APP_JWT_SECRET`, `APP_JWT_EXPIRATION_SECONDS`, `APP_JWT_REFRESH_EXPIRATION_HOURS` |
| 👤 Usuarios | `PASSWORD_MIN_LENGTH`, `PASSWORD_MAX_LENGTH`, `USERNAME_MIN_LENGTH`, `USERNAME_MAX_LENGTH` |
| 🥽 Códigos de Unity | `UNITY_CODE_HMAC_PEPPER`, `UNITY_CODE_ENCRYPTION_KEY`, `UNITY_ACCESS_CODE_EXPIRATION_MINUTES` |
| 📧 Correo | `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE` |
| 🔢 Códigos de verificación | `VERIFICATION_CODE_LENGTH`, `VERIFICATION_CODE_EXPIRATION_MINUTES`, `ONE_TIME_CODE_MAX_ATTEMPTS`, `ONE_TIME_CODE_MAX_RESENDS`, `ONE_TIME_CODE_RESEND_LOCK_MINUTES`, `PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES` |
| ✉️ Validación de correo | `EMAIL_KNOWN_PROVIDERS`, `EMAIL_MAX_TYPO_DISTANCE`, `EMAIL_DNS_CHECK_ENABLED`, `EMAIL_DNS_TIMEOUT_MILLIS` |
| ☕ Tueste | `ROAST_HISTORY_PAGE_SIZE`, `ROAST_RANKING_SIZE`, `ROAST_RANKING_POLL_SECONDS`, `ROAST_CHARGE_TEMPERATURE_MIN`, `ROAST_CHARGE_TEMPERATURE_MAX`, `ROAST_TARGET_TEMPERATURE_MIN`, `ROAST_TARGET_TEMPERATURE_MAX`, `ROAST_FINAL_TEMPERATURE_MAX`, `ROAST_RAW_TEMPERATURE_CEILING`, `ROAST_BURNED_TEMPERATURE_CEILING`, `ROAST_DEFECT_SCORE_MIN`, `ROAST_DEFECT_SCORE_MAX`, `ROAST_PERFECT_SCORE_MIN`, `ROAST_PERFECT_SCORE_MAX`, `ROAST_RANKING_COMBINED_SINCE` |
| 📈 Traza del tueste | `ROAST_TRACE_SUPPORTED_VERSION`, `ROAST_TRACE_MAX_SAMPLES`, `ROAST_TRACE_MAX_GAS_CHANGES`, `ROAST_TRACE_MAX_EVENTS`, `ROAST_TRACE_MAX_ALERTS`, `ROAST_TRACE_MAX_ALERT_ID_LENGTH`, `ROAST_TRACE_TIME_TOLERANCE_SECONDS`, `ROAST_TRACE_TEMPERATURE_TOLERANCE` |
| 🏅 Calificación del tueste | `ROAST_EVALUATION_RULES_WEIGHT`, `ROAST_EVALUATION_AI_WEIGHT`, `ROAST_EVALUATION_DEFECT_SCORE_CAP`, `ROAST_EVALUATION_SCHEDULER_ENABLED`, `ROAST_EVALUATION_POLL_MILLIS`, `ROAST_EVALUATION_BATCH_SIZE`, `ROAST_EVALUATION_MAX_ATTEMPTS`, `ROAST_EVALUATION_BASE_RETRY_SECONDS`, `ROAST_EVALUATION_MAX_RETRY_SECONDS`, `ROAST_GRADE_EXCELLENT_MIN`, `ROAST_GRADE_GOOD_MIN`, `ROAST_GRADE_FAIR_MIN` |
| 🤖 Servicio IA y Ollama | `AI_SERVICE_BASE_URL`, `DOCKER_AI_SERVICE_BASE_URL`, `OLLAMA_BASE_URL`, `DOCKER_OLLAMA_BASE_URL`, `RAG_EMBEDDING_MODEL` |
| 🔥 Simulador para Unity | `ROAST_SIMULATOR_BASE_URL`, `DOCKER_ROAST_SIMULATOR_BASE_URL`, `ROAST_SIMULATOR_CONNECT_TIMEOUT_MILLIS`, `ROAST_SIMULATOR_READ_TIMEOUT_MILLIS` |
| 🛡️ Administrador inicial | `APP_BOOTSTRAP_ADMIN_ENABLED`, `APP_BOOTSTRAP_ADMIN_NAME`, `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_PASSWORD` |

💡 Las variables que empiezan con `DOCKER_` las usa el backend cuando corre en Docker; las demás, cuando corre en el host.

## 🧪 Tests

📂 Desde `Toasted_VR`:

```bash
./mvnw -B test      # en Windows: mvnw.cmd -B test
```

Usan el perfil `test` (`src/test/resources/application-test.yml`): H2 en memoria, correo deshabilitado y servicio IA simulado, así que no necesitan PostgreSQL, el servicio IA ni SMTP. En el CI los corre el job `backend-tests`.
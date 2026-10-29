# Backend de Toasted VR

## Docker y pruebas en la red local

El archivo Compose del backend es un override del servicio de PostgreSQL que ya tiene el Compose del repositorio. Corro los comandos de abajo desde `Toasted_VR`; reutilizan el contenedor `toasted_vr_postgres`, su red de Compose y su volumen con nombre que ya existe. Nunca le agrego `-v` al comando `down`, porque esa opción borra los volúmenes de Compose.

Si hace falta, copio `.env.example` a `.env` y pongo valores privados en `POSTGRES_PASSWORD`, `APP_JWT_SECRET`, `UNITY_CODE_HMAC_PEPPER` y `UNITY_CODE_ENCRYPTION_KEY`. `POSTGRES_PASSWORD` tiene que coincidir con la contraseña con la que se inicializó el volumen de PostgreSQL que ya existe.

Para levantar o reconstruir el stack:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml up --build -d
```

Para seguir los logs del backend:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml logs -f backend
```

Para verificar desde el PC el endpoint público de la documentación de la API:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8081/v3/api-docs | Select-Object StatusCode
```

Para detener el stack sin borrar los datos de la base:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml down
```

Para saber la dirección IPv4 del PC en la red local:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object InterfaceAlias,IPAddress
```

Desde otro dispositivo de la misma red local que tenga PowerShell, reemplazo los marcadores por la dirección del PC y un código de acceso de Unity real. PowerShell guarda el código en memoria solo para esta petición y el comando no lo imprime aparte:

```powershell
$pcAddress = '<PC_IPV4_ADDRESS>'
$unityCode = Read-Host 'Unity access code'
$body = @{ code = $unityCode } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "http://${pcAddress}:8081/api/v1/auth/unity-login" -ContentType 'application/json' -Body $body
Write-Output "Unity login succeeded for $($loginResponse.user.username)."
Remove-Variable unityCode,body,loginResponse
```

Configuro el cliente de Unity del Meta Quest para que llame a:

```text
http://<PC_IPV4_ADDRESS>:8081/api/v1/auth/unity-login
```

Si el Firewall de Windows bloquea el tráfico de la red local, corro esto una vez desde un PowerShell con permisos de administrador:

```powershell
New-NetFirewallRule -DisplayName 'Toasted VR backend 8081' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
```

## Stack completo (base de datos + backend + frontend) con un solo comando

El frontend tiene su propio override de Compose (`toasted_vr_frontend/docker-compose.frontend.yml`) que lo construye y lo sirve con nginx. El servicio IA (`IA/`, retroalimentación + RAG) y Ollama se quedan a propósito fuera de todos los contenedores y siguen corriendo en el host; el backend llega al servicio IA por `host.docker.internal` (`DOCKER_AI_SERVICE_BASE_URL`), y agregar el frontend no cambia eso.

Esto lo corro desde la raíz del repositorio (no desde `Toasted_VR`):

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml up --build -d
```

Para detener el stack completo sin borrar los datos de la base:

```powershell
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml down
```

El frontend queda en `http://localhost:3000`. Su `REACT_APP_API_BASE_URL` se resuelve al arrancar el contenedor, no queda fijo en el build: la imagen se construye una sola vez con un marcador y `toasted_vr_frontend/entrypoint.sh` lo reemplaza por el valor real cada vez que arranca el contenedor. Para apuntar el frontend a otra URL del backend (por ejemplo, la IPv4 del PC en la red local para un build del Meta Quest), defino `REACT_APP_API_BASE_URL` antes de levantar el stack y recreo solo el contenedor del frontend, sin reconstruir nada:

```powershell
$env:REACT_APP_API_BASE_URL = 'http://<PC_IPV4_ADDRESS>:8081/api/v1'
docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml up -d --force-recreate frontend
```

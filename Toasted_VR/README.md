# Toasted VR backend

## Docker and local LAN testing

The backend Compose file is an override for the repository's existing PostgreSQL Compose service. Run the commands below from `Toasted_VR`; they reuse the `toasted_vr_postgres` container, its Compose network, and its existing named volume. Never add `-v` to the `down` command because that option deletes Compose volumes.

Copy `.env.example` to `.env` if needed, then set private values for `POSTGRES_PASSWORD`, `APP_JWT_SECRET`, `UNITY_CODE_HMAC_PEPPER`, and `UNITY_CODE_ENCRYPTION_KEY`. `POSTGRES_PASSWORD` must match the password used when the existing PostgreSQL volume was initialized.

Start or rebuild the stack:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml up --build -d
```

Follow backend logs:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml logs -f backend
```

Verify the public API documentation endpoint from the PC:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8081/v3/api-docs | Select-Object StatusCode
```

Stop the stack without deleting database data:

```powershell
docker compose --env-file .\.env -f ..\docker-compose.yml -f .\docker-compose.backend.yml down
```

Discover the PC's LAN IPv4 address:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object InterfaceAlias,IPAddress
```

From another PowerShell-capable device on the same LAN, replace the placeholders with the PC address and a real Unity access code. PowerShell keeps the code in memory for this request and the command does not print it separately:

```powershell
$pcAddress = '<PC_IPV4_ADDRESS>'
$unityCode = Read-Host 'Unity access code'
$body = @{ code = $unityCode } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "http://${pcAddress}:8081/api/v1/auth/unity-login" -ContentType 'application/json' -Body $body
Write-Output "Unity login succeeded for $($loginResponse.user.username)."
Remove-Variable unityCode,body,loginResponse
```

Configure the Meta Quest Unity client to call:

```text
http://<PC_IPV4_ADDRESS>:8081/api/v1/auth/unity-login
```

If Windows Firewall blocks LAN traffic, run this once from an elevated PowerShell prompt:

```powershell
New-NetFirewallRule -DisplayName 'Toasted VR backend 8081' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
```

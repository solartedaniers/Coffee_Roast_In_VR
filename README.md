# Simulador Inmersivo VR del Proceso del Cafe

## Descripcion General
Este repositorio contiene el codigo fuente del ecosistema del **Simulador Inmersivo VR del Proceso del Cafe**, dividido en dos componentes principales:

1. **Backend (`Toasted_VR`)**: servidor de aplicaciones y APIs construido con Java y Spring Boot.
2. **Frontend (`toasted_vr_frontend`)**: aplicacion web de acompanamiento desarrollada en React.js.

Actualmente el backend utiliza **Supabase** como proveedor administrado de **PostgreSQL**. La aplicacion se conecta mediante **Spring Data JPA + Hibernate**.

---

## Arquitectura y Patrones de Diseno

El proyecto sigue una arquitectura cliente-servidor orientada a APIs REST. El backend centraliza la logica de negocio, seguridad y persistencia, mientras que el frontend consume la API y presenta la experiencia al usuario.

### Backend (Spring Boot / Java)
- **Controllers**: exponen los endpoints REST.
- **Services**: implementan la logica de negocio, validaciones, OTP y autenticacion.
- **Repositories**: gestionan la persistencia con Spring Data JPA sobre PostgreSQL/Supabase.
- **Entities**: mapean objetos Java a tablas PostgreSQL mediante Hibernate.
- **DTOs**: desacoplan la API de las entidades internas.
- **Security**: protege rutas y flujos sensibles.

### Frontend (React.js)
- Componentes funcionales y hooks.
- Renderizado condicional para flujos de registro y verificacion.
- Textos centralizados para facilitar mantenimiento.
- CSS propio sin Bootstrap ni Tailwind.

---

## Como Correr el Proyecto Localmente

Debes levantar backend y frontend en terminales separadas.

### 1. Backend (`Toasted_VR`)

**Requisitos previos**
- JDK 17 o superior.
- Un proyecto de Supabase creado.
- Credenciales SMTP validas si vas a enviar correos reales.

**Configurar Supabase**
1. Crea un proyecto en Supabase.
2. Entra al dashboard y abre `Connect`.
3. Copia el `JDBC connection string`, el usuario y la contrasena de la base de datos.
4. Crea `Toasted_VR/.env` tomando como base `Toasted_VR/.env.example`.

**Variables importantes del `.env`**
```env
DB_URL=jdbc:postgresql://TU_HOST/postgres?sslmode=require
DB_USERNAME=TU_USUARIO_SUPABASE
DB_PASSWORD=TU_PASSWORD_SUPABASE
DB_DRIVER_CLASS_NAME=org.postgresql.Driver
JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true
```

**Notas**
- Si Supabase te entrega una conexion pooler por puerto `6543`, agrega `&prepareThreshold=0` al `DB_URL`.
- Con `JPA_DDL_AUTO=update`, Hibernate crea o actualiza automaticamente las tablas necesarias.
- No necesitas crear manualmente la tabla `users` si la app arranca correctamente contra Supabase.

**Ejecutar el backend**
1. Entra a la carpeta:
   ```bash
   cd Toasted_VR
   ```
2. Inicia la aplicacion:
   - **Windows**
     ```bash
     mvnw.cmd spring-boot:run
     ```
   - **Linux/Mac**
     ```bash
     ./mvnw spring-boot:run
     ```
3. El backend quedara disponible en `http://localhost:8081`.

### 2. Frontend (`toasted_vr_frontend`)

**Requisitos previos**
- Node.js 18 o superior (probado con Node 24.19.0; `package-lock.json` fue regenerado con esa version, pero no exige una version exacta).
- NPM instalado.

**Ejecutar el frontend**
1. Entra a la carpeta:
   ```bash
   cd toasted_vr_frontend
   ```
2. Instala dependencias:
   ```bash
   npm install
   ```
3. Inicia el servidor de desarrollo:
   ```bash
   npm start
   ```
4. La aplicacion quedara disponible en `http://localhost:3000`.

### 3. Retroalimentacion con IA (Groq + Ollama) - Opcional

Tengo la parte de IA separada en su propio servicio, la carpeta `IA/`, escrito
en Python con FastAPI: genera la retroalimentacion que se muestra al finalizar
un tueste y hace el RAG sobre los PDFs de `IA/rag-docs/`. La retroalimentacion
la redacta Groq (`openai/gpt-oss-120b`, capa gratuita) y, si Groq falla o se
queda sin cuota, Ollama local (`phi4-mini`). Corre en el host, fuera de Docker,
igual que Ollama. El frontend y Unity siguen llamando al backend como siempre,
y el backend le delega el trabajo al servicio IA.

**Instalacion**
1. Instalo Ollama desde [https://ollama.com](https://ollama.com).
2. Descargo los modelos:
   ```bash
   ollama pull nomic-embed-text
   ollama pull phi4-mini
   ```
3. Si quiero usar Groq, creo una llave en [https://console.groq.com](https://console.groq.com)
   y la pongo como `GROQ_API_KEY` en `IA/.env` (ese archivo nunca se sube).
4. Levanto el servicio IA siguiendo `IA/README.md` (Python 3.12, entorno
   virtual en `IA/.venv` y `python -m app` desde `IA/`; queda en
   `http://127.0.0.1:8082`).

El backend encuentra el servicio IA con `AI_SERVICE_BASE_URL` (backend en el
host) o `DOCKER_AI_SERVICE_BASE_URL` (backend en Docker) en `Toasted_VR/.env`,
ver `Toasted_VR/.env.example`.

**Es opcional.** Si el servicio IA no esta corriendo, o ni Groq ni Ollama responden, el resto de
la aplicacion funciona con normalidad; simplemente no se muestra la
retroalimentacion de IA y en su lugar aparece el mensaje "Retroalimentacion
no disponible por ahora".

### 4. Simulador de tueste para Unity (Node)

Unity usa la misma fisica del simulador web sin copiarla: la carpeta
`roast-simulator/` es un servicio en Node 24, sin dependencias, que importa
directamente `toasted_vr_frontend/src/domain/roasting`. Unity nunca lo llama
directo: juega a traves de `/api/v1/roasting/simulations` del backend, que
valida el token y guarda la sesion al descargar. El simulador nunca queda
abierto hacia la red.

Lo levanto de una de dos formas:

- Con Node en el host (`127.0.0.1:8083`), cuando el backend tambien corre en el host:
  ```bash
  cd roast-simulator
  npm start
  ```
- Con Docker, junto con los demas servicios, agregando su compose:
  ```powershell
  docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml -f .\roast-simulator\docker-compose.simulator.yml up --build -d
  ```
  El backend en Docker lo encuentra por el nombre del servicio y el puerto solo
  se publica en `127.0.0.1:8083`.

El backend lo encuentra con `ROAST_SIMULATOR_BASE_URL` (backend en el host) o
`DOCKER_ROAST_SIMULATOR_BASE_URL` (backend en Docker) en `Toasted_VR/.env`. El
contrato para Unity esta en `IA/docs/unity-roast-simulation-contract.md` y los
detalles del servicio en `roast-simulator/README.md`. La web no lo necesita:
sigue corriendo la fisica en el navegador.

---

## Flujo de Pull Requests y CI

Trabajo cada cambio en su propia rama y lo integro a `main` solo por Pull Request. Cada PR hacia `main` (y cada push a `main`) dispara el workflow `.github/workflows/ci.yml` en GitHub Actions, que corre cuatro checks:

- `backend-tests`: los tests de `Toasted_VR` con Java 17 y Maven (`./mvnw -B test`). Usan el perfil `test` (H2 en memoria, correo deshabilitado y servicio IA simulado), así que no necesitan PostgreSQL, el servicio IA ni SMTP.
- `frontend-tests`: en `toasted_vr_frontend` con Node 20 corre `npm ci`, los tests con `CI=true` y `npm run build`.
- `simulator-tests`: los tests de `roast-simulator` con Node 24 (`npm test`). No tienen dependencias: importan el dominio de la web directamente.
- `ai-tests`: los tests del servicio `IA/` con Python 3.12 y pytest. No necesitan PostgreSQL, Ollama ni Groq.

Solo fusiono un PR cuando los checks están en verde. Para revisarlo antes de abrir el PR, corro lo mismo localmente:

```bash
cd Toasted_VR && ./mvnw -B test
cd IA && .venv/Scripts/python -m pytest -q
cd roast-simulator && npm test
cd toasted_vr_frontend && npm ci && CI=true npm test -- --watchAll=false && CI=true npm run build
```

---

## Contexto Importante para Futuros Desarrolladores

1. **Base de datos actual:** el proyecto usa **Supabase** como proveedor administrado de **PostgreSQL**. Si en documentacion antigua aparece MySQL, ya no aplica.
2. **Creacion automatica de esquema:** con `JPA_DDL_AUTO=update`, Spring Boot/Hibernate crea o actualiza tablas en PostgreSQL segun las entidades Java.
3. **Verificacion por correo:** el registro envia un codigo OTP de 6 digitos con expiracion para confirmar el email del usuario.
4. **SMTP real:** para Gmail debe usarse una App Password, no la contrasena normal de la cuenta.
5. **Archivos sensibles:** el archivo `.env` no debe subirse al repositorio. Solo debe versionarse `.env.example`.

---

## Sobre el Diagrama de Arquitectura

Si tu diagrama actualmente dice **PostgreSQL**, no esta mal, porque Supabase usa PostgreSQL por debajo. Pero para que quede mas claro y actualizado, conviene cambiar la caja de base de datos a alguno de estos nombres:

- `Supabase (PostgreSQL)`
- `Supabase PostgreSQL`
- `Base de Datos Gestionada - Supabase (PostgreSQL)`

Mi recomendacion es que no reemplaces `PostgreSQL` por completo, sino que lo dejes explicito como servicio administrado. Por ejemplo:

`Servidor BD UCC` -> `Supabase`

Y dentro de esa caja:
- `PostgreSQL`
- `Auth / Managed DB` solo si realmente vas a usar mas servicios de Supabase despues
- `Backups Administrados` si quieres reflejar que la capa ya no es local/manual

Si por ahora solo usas Supabase como base de datos, la forma mas clara y honesta es dejarlo como **`Supabase (PostgreSQL)`**.



Abre Docker Desktop y espera a que esté completamente listo.
Ve a la raíz del proyecto:
   cd C:\Users\Alexander\Documents\Proyecto de Grado\Graduation_Project
Levanta los 3 servicios (sin --build si no cambiaste nada de código desde hoy — arranca más rápido):
   docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml up -d
Confirma que los 3 quedaron arriba sin reiniciarse:
   docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml ps

Los 3 deben decir Up (postgres además healthy) — ninguno debe decir Restarting.

Prueba en el navegador:
   http://localhost:3000



   # Detén los contenedores actuales

   * docker compose down

   # Vuelve a levantar todo el entorno limpio

   * docker compose --env-file .\Toasted_VR\.env -f .\docker-compose.yml -f .\Toasted_VR\docker-compose.backend.yml -f .\toasted_vr_frontend\docker-compose.frontend.yml up --build -d

   # Verifica que todo esté corriendo correctamente:

   * docker ps
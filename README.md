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

### 3. Retroalimentacion con IA (Ollama + Phi-4-mini) - Opcional

Tengo la parte de IA separada en su propio servicio, la carpeta `IA/`: genera
la retroalimentacion que se muestra al finalizar un tueste con un modelo local
via [Ollama](https://ollama.com) y hace el RAG sobre los PDFs de
`IA/src/main/resources/rag-docs/`. Corre en el host, fuera de Docker, igual
que Ollama. El frontend y Unity siguen llamando al backend como siempre, y el
backend le delega el trabajo al servicio IA.

**Instalacion**
1. Instalo Ollama desde [https://ollama.com](https://ollama.com).
2. Descargo los modelos:
   ```bash
   ollama pull phi4-mini
   ollama pull nomic-embed-text
   ```
3. Levanto el servicio IA siguiendo `IA/README.md` (`./mvnw spring-boot:run`
   desde `IA/`, queda en `http://127.0.0.1:8082`).

El backend encuentra el servicio IA con `AI_SERVICE_BASE_URL` (backend en el
host) o `DOCKER_AI_SERVICE_BASE_URL` (backend en Docker) en `Toasted_VR/.env`,
ver `Toasted_VR/.env.example`.

**Es opcional.** Si el servicio IA u Ollama no estan corriendo, el resto de
la aplicacion funciona con normalidad; simplemente no se muestra la
retroalimentacion de IA y en su lugar aparece el mensaje "Retroalimentacion
no disponible por ahora".

---

## Flujo de Pull Requests y CI

Trabajo cada cambio en su propia rama y lo integro a `main` solo por Pull Request. Cada PR hacia `main` (y cada push a `main`) dispara el workflow `.github/workflows/ci.yml` en GitHub Actions, que corre tres checks:

- `backend-tests`: los tests de `Toasted_VR` con Java 17 y Maven (`./mvnw -B test`). Usan el perfil `test` (H2 en memoria, correo deshabilitado y servicio IA simulado), así que no necesitan PostgreSQL, el servicio IA ni SMTP.
- `frontend-tests`: en `toasted_vr_frontend` con Node 20 corre `npm ci`, los tests con `CI=true` y `npm run build`.
- `ai-tests`: los tests del servicio `IA/` con Java 17 y Maven. También usan H2 y no necesitan Ollama.

Solo fusiono un PR cuando los checks están en verde. Para revisarlo antes de abrir el PR, corro lo mismo localmente:

```bash
cd Toasted_VR && ./mvnw -B test
cd IA && ./mvnw -B test
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
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
- Node.js 18 o superior.
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


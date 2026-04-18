# Simulador Inmersivo VR del Proceso del Café

## Descripción General
Este repositorio contiene el código fuente del ecosistema del **Simulador Inmersivo VR del Proceso del Café**, dividido en dos componentes principales:
1. **Backend (`Toasted_VR`)**: Servidor de aplicaciones y APIs construido con Java y Spring Boot.
2. **Frontend (`toasted_vr_frontend`)**: Aplicación web (Portal de acompañamiento / Companion App) desarrollada en React.js.

---

## 🛠 Arquitectura y Patrones de Diseño Utilizados

El proyecto sigue una arquitectura cliente-servidor orientada a microservicios/APIs (REST). El backend actúa como proveedor de seguridad, persistencia de datos y reglas de negocio, mientras que el frontend actúa como la capa de presentación consumidora.

### En el Backend (Spring Boot / Java)
Se ha utilizado el **Patrón Arquitectónico en Capas (Layered Architecture)**, una derivación del clásico MVC, segmentado de la siguiente manera:
- **Controladores (Controllers / Endpoints):** Capa encargada de exponer la API REST, recibir las peticiones HTTP y devolver las respuestas.
- **Servicios (Services):** Capa donde reside toda la lógica y las reglas de negocio complejas (ej. validación de correos, cifrado, generación de códigos OTP).
- **Repositorios (Repositories / DAO):** Capa encargada de la persistencia de datos. Maneja las consultas a MySQL utilizando **Spring Data JPA**.
- **Modelos / Entidades (Entities):** Clases de Java mapeadas directamente a las tablas de la base de datos MySQL (ORM Hibernate).
- **DTOs (Data Transfer Objects):** Patrón utilizado para transportar datos entre el cliente (React) y el servidor (Spring Boot) sin exponer la estructura interna ni los campos confidenciales de la base de datos.
- **Seguridad:** Uso de **Spring Security** con tokens **JWT** para proteger las rutas.

### En el Frontend (React.js)
Se implementó una **Arquitectura Basada en Componentes (Component-Based Architecture)** y programación funcional:
- **Componentes Funcionales y Hooks:** Se utilizan `useState` y funciones flecha para gestionar el estado de los componentes y su ciclo de vida.
- **Renderizado Condicional:** Las interfaces mutan dinámicamente según el estado del usuario (ej. pasando de "Registro" a "Verificación de Código") sin recargar la página (comportamiento SPA - Single Page Application).
- **Internacionalización / Centralización de Textos:** Separación de los copys y textos en archivos de configuración externos (como `src/locals/es.json`) para facilitar el mantenimiento y evitar texto *hardcodeado*.
- **CSS Modular Variables:** Diseño compacto y responsivo basado en variables de entorno CSS (`--color-primary`, etc.) garantizando una estética de modo oscuro (Dark Mode), optimizada para no generar *scrolls* innecesarios y verse como una app premium.

---

## 🚀 Cómo Correr el Proyecto Localmente

Para ejecutar el proyecto, debes levantar el Backend y el Frontend en dos consolas/terminales separadas.

### 1. Configurar y Ejecutar el Backend (Spring Boot)

**Requisitos Previos:**
- Java Development Kit (JDK) 17 o superior.
- Motor de Base de Datos MySQL ejecutándose en el puerto `3306`.

**Pasos:**
1. Crear la base de datos manualmente en MySQL (por ejemplo, desde MySQL Workbench):
   ```sql
   CREATE DATABASE toasted_vr_db;
   ```
2. Abre una terminal y ve a la carpeta del backend:
   ```bash
   cd Toasted_VR
   ```
3. Verifica la configuración en tu archivo `.env`. Asegúrate de que las contraseñas de tu base de datos y tu servicio SMTP de Gmail sean correctas:
   ```env
   DB_URL=jdbc:mysql://localhost:3306/toasted_vr_db?useSSL=false&serverTimezone=UTC
   DB_USERNAME=root
   DB_PASSWORD=tu_contraseña_mysql
   MAIL_USERNAME=tu_correo@gmail.com
   MAIL_PASSWORD=tu_app_password_generado_en_gmail
   ```
4. Compila y arranca el servidor utilizando el wrapper de Maven que viene incluido:
   - **En Windows:**
     ```bash
     mvnw.cmd spring-boot:run
     ```
   - **En Linux/Mac:**
     ```bash
     ./mvnw spring-boot:run
     ```
5. El servidor backend quedará disponible en: `http://localhost:8081`

### 2. Configurar y Ejecutar el Frontend (React.js)

**Requisitos Previos:**
- Node.js (v18+) y NPM instalados en la máquina.

**Pasos:**
1. Abre una nueva terminal y dirígete a la carpeta del frontend:
   ```bash
   cd toasted_vr_frontend
   ```
2. Instala todas las dependencias y librerías necesarias:
   ```bash
   npm install
   ```
3. Levanta el servidor de desarrollo web:
   ```bash
   npm start
   ```
4. El navegador se abrirá automáticamente y podrás interactuar con la aplicación en: `http://localhost:3000`

---

## 📌 Contexto Importante para Futuros Desarrolladores

Si te integras a este proyecto, aquí hay algunas reglas clave que debes conocer sobre lo que se ha hecho hasta ahora:

1. **JPA DDL Auto:** El backend está configurado con `JPA_DDL_AUTO=update`. Esto significa que Spring Boot creará o modificará automáticamente las tablas en MySQL para coincidir con las Entidades de Java. ¡No es necesario crear las tablas manualmente por scripts SQL!
2. **Autenticación en Dos Pasos (MFA por Correo):** El formulario de registro no activa la cuenta de inmediato. Al enviarse, la cuenta queda pendiente y el sistema dispara un código de seguridad OTP de 6 dígitos al correo del usuario. El Front-end tiene un componente específico con una cuenta regresiva de 2 minutos para validarlo.
3. **Contraseñas de Aplicación de Gmail:** Para que el servidor backend envíe los correos OTP correctamente, la cuenta de Gmail utilizada en el `.env` NO utiliza la contraseña normal de inicio de sesión, sino una "Contraseña de Aplicación" (App Password) generada desde los ajustes de seguridad de Google (se requiere tener verificación de dos pasos activada en esa cuenta de Google).
4. **Diseño Visual:** La interfaz no usa frameworks como Bootstrap ni Tailwind. Se construyó con "Vanilla CSS" buscando un acabado inmersivo de "Glassmorphism" con luces de ambiente (`ambient-light`). Si agregas componentes nuevos, intenta reutilizar las clases genéricas como `.primary-button` o `.field-input` ubicadas en `App.css`.

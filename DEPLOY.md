# Guía de Despliegue — StockFlow Backend

Esta guía describe cómo desplegar StockFlow Backend en **Render** con base de datos **Supabase Postgres** y frontend en **Vercel**.

---

## Perfiles de Spring Boot

| Perfil | Archivo | Uso |
|--------|---------|-----|
| `dev`  | `application-dev.yml`  | Desarrollo local (PostgreSQL en Docker) |
| `uat`  | `application-uat.yml`  | User Acceptance Testing en Render |
| `prod` | `application-prod.yml` | Producción en Render |

El perfil **no está hardcodeado** en `application.yml`. Se controla vía la variable de entorno `SPRING_PROFILES_ACTIVE` en cada entorno.

---

## Variables de entorno requeridas

### Variables comunes (UAT y PROD)

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `uat` o `prod` |
| `SPRING_DATASOURCE_URL` | URL JDBC de Supabase | `jdbc:postgresql://db.xxxx.supabase.co:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos | `<tu-password>` |
| `JWT_SECRET` | Clave secreta para firmar JWT (mín. 32 chars) | `<generado-aleatoriamente>` |
| `SENDGRID_API_KEY` | API key de SendGrid para emails | `SG.xxx...` |
| `MERCADOPAGO_ACCESS_TOKEN` | Access token backend de Mercado Pago | `APP_USR-...` |
| `MERCADOPAGO_WEBHOOK_SECRET` | Secreto para validar webhook (`X-Webhook-Token` o `?token=`) | `<secreto-random>` |
| `MERCADOPAGO_NOTIFICATION_URL` | URL pública del webhook backend | `https://api.stockflow.pe/api/webhooks/mercadopago` |

> **Alternativa de nombres legacy**: Si ya tienes configuradas las variables `DATABASE_URL`, `DB_USERNAME` y `DB_PASSWORD`, el backend también las acepta. Se recomienda migrar al formato `SPRING_DATASOURCE_*`.

### Variable opcional

| Variable | Descripción | Default |
|----------|-------------|---------|
| `PORT` | Puerto del servidor (Render lo inyecta automáticamente) | `8080` |
| `FRONTEND_URL` | URL del frontend (usada internamente) | Según perfil |

---

## Configuración en Render

### 1. Crear el servicio (Web Service)

1. En Render, crea un **Web Service** desde tu repositorio GitHub.
2. Selecciona **Docker** como entorno (si usas el `Dockerfile`) o **Java** si usas el buildpack.
3. Configura el comando de inicio (si no usas Dockerfile):
   ```
   java -jar target/stockflow-backend-1.0.0.jar
   ```
4. Render expone el servicio en el puerto `$PORT` — el `application.yml` ya lo toma con `${PORT:8080}`.

### 2. Variables de entorno en Render (PROD)

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxxx.supabase.co:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<password>
JWT_SECRET=<secret-de-minimo-32-caracteres>
SENDGRID_API_KEY=SG.xxx...
MERCADOPAGO_ACCESS_TOKEN=APP_USR-...
MERCADOPAGO_WEBHOOK_SECRET=<secreto-random>
MERCADOPAGO_NOTIFICATION_URL=https://api.stockflow.pe/api/webhooks/mercadopago
```

### 3. Variables de entorno en Render (UAT)

```
SPRING_PROFILES_ACTIVE=uat
SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxxx.supabase.co:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<password-uat>
JWT_SECRET=<secret-uat>
SENDGRID_API_KEY=SG.xxx...
MERCADOPAGO_ACCESS_TOKEN=APP_USR-...
MERCADOPAGO_WEBHOOK_SECRET=<secreto-random>
MERCADOPAGO_NOTIFICATION_URL=https://api-uat.stockflow.pe/api/webhooks/mercadopago
```

### 4. Dominios recomendados en Render

| Entorno | Backend (Render custom domain) | Frontend (Vercel) |
|---------|-------------------------------|-------------------|
| PROD    | `api.stockflow.pe`            | `www.stockflow.pe` / `stockflow.pe` |
| UAT     | `api-uat.stockflow.pe`        | `uat.stockflow.pe` |

Configura los custom domains en el panel de tu servicio Render y apunta los registros DNS (CNAME) al dominio interno de Render.

---

## Configuración en Vercel (Frontend)

En el panel de Vercel, agrega la variable de entorno para que el frontend apunte al backend correcto:

### PROD
```
VITE_API_URL=https://api.stockflow.pe/api
```

### UAT
```
VITE_API_URL=https://api-uat.stockflow.pe/api
```

### Local (`.env.local`)
```
VITE_API_URL=http://localhost:8080/api
```

---

## Conexión a Supabase Postgres

1. En tu proyecto de Supabase, ve a **Settings → Database**.
2. Copia la **Connection string** en formato URI: `postgresql://postgres:<password>@db.<ref>.supabase.co:5432/postgres`.
3. Convierte a formato JDBC:
   ```
   jdbc:postgresql://db.<ref>.supabase.co:5432/postgres
   ```
4. Úsala como valor de `SPRING_DATASOURCE_URL`.

> **TLS**: Supabase requiere SSL. Si obtienes errores de SSL, agrega `?sslmode=require` al final de la URL JDBC.

---

## Despliegue con Docker (recomendado)

El repositorio incluye un `Dockerfile` multi-stage que:
- Compila el JAR con Maven (JDK 21)
- Empaqueta solo el runtime (JRE 21 Alpine)
- Expone el puerto `8080`
- Corre como usuario no-root `stockflow`

### Build local
```bash
docker build -t stockflow-backend .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://... \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e JWT_SECRET=mi-secreto-jwt-de-32-chars \
  stockflow-backend
```

### Desarrollo local (docker-compose)
```bash
# Levanta PostgreSQL local
docker-compose up -d

# Corre la app en modo dev (usa application-dev.yml)
./mvnw spring-boot:run
```

---

## CORS

Cada perfil tiene CORS configurado para su dominio:

| Perfil | Orígenes permitidos |
|--------|---------------------|
| `dev`  | `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` |
| `uat`  | `https://uat.stockflow.pe` |
| `prod` | `https://www.stockflow.pe`, `https://stockflow.pe` |

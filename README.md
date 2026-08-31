# Lookalike

Lookalike is a public entertainment web application where anonymous visitors
compare one photo against configured personality profiles.

## Prerequisites

- Node.js `>=24.15.0 <25`
- npm from the same Node installation
- JDK 25
- Docker Desktop or Docker Engine with Docker Compose
- Git

Use the Maven Wrapper in `backend/`; no separate Maven installation is required.

## Setup

Windows PowerShell:

```powershell
$env:Path = 'C:\Program Files\nodejs;' + $env:Path
$env:JAVA_HOME = '<path-to-jdk-25>'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
cd frontend
npm.cmd install
cd ..
```

macOS/Linux:

```sh
export JAVA_HOME=<path-to-jdk-25>
export PATH="$JAVA_HOME/bin:$PATH"
cd frontend
npm install
cd ..
```

Optional local environment:

```sh
cp .env.example .env
```

Before starting the backend, set a non-production JWT secret in the backend
process environment. To create a local user for manual testing, also enable the
local bootstrap values:

```text
APP_AUTH_JWT_SECRET=<at-least-32-random-local-characters>
APP_AUTH_LOCAL_BOOTSTRAP_ENABLED=true
APP_AUTH_LOCAL_BOOTSTRAP_EMAIL=local.user@example.com
APP_AUTH_LOCAL_BOOTSTRAP_PASSWORD=<local-password>
APP_AUTH_LOCAL_BOOTSTRAP_ROLES=USER,ADMIN
```

The bootstrap user is created only with the Spring `local` profile and only when
explicitly enabled. Passwords are stored as BCrypt hashes, and refresh-token
material is stored only as a SHA-256 hash. If you keep these values in `.env`,
load them into your shell or IDE run configuration before starting Spring Boot.

## Run

Start PostgreSQL:

```sh
docker compose up -d db
```

Start the backend.

Windows:

```powershell
$env:JAVA_HOME = '<path-to-jdk-25>'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:APP_AUTH_JWT_SECRET = '<at-least-32-random-local-characters>'
$env:SPRING_PROFILES_ACTIVE = 'local'
Set-Location backend
.\mvnw.cmd spring-boot:run
Set-Location ..
```

macOS/Linux:

```sh
export JAVA_HOME=<path-to-jdk-25>
export PATH="$JAVA_HOME/bin:$PATH"
export APP_AUTH_JWT_SECRET=<at-least-32-random-local-characters>
export SPRING_PROFILES_ACTIVE=local
cd backend
./mvnw spring-boot:run
cd ..
```

Start the frontend.

Windows:

```powershell
cd frontend
npm.cmd start
```

macOS/Linux:

```sh
cd frontend
npm start
```

Frontend: `http://localhost:4200`

Backend health: `http://localhost:8080/api/v1/health`

In local development, Angular uses `frontend/proxy.conf.json` to send relative
`/api/v1` requests from `http://localhost:4200` to the backend on
`http://localhost:8080`. Production is expected to use same-origin
frontend/backend deployment; cross-origin production deployment is outside the
current scope.

Authentication endpoints:

- `GET http://localhost:8080/api/v1/auth/csrf`
- `POST http://localhost:8080/api/v1/auth/login`
- `POST http://localhost:8080/api/v1/auth/refresh`
- `POST http://localhost:8080/api/v1/auth/logout`
- `GET http://localhost:8080/api/v1/auth/me`
- `GET http://localhost:8080/api/v1/auth/admin-check`

`GET /api/v1/health` is public. Other protected backend endpoints require a
valid bearer token, and role-restricted backend endpoints return `403` when the
authenticated user lacks the required role. Login, refresh, and logout require
the readable `XSRF-TOKEN` cookie value to be sent in the `X-XSRF-TOKEN` header.
The refresh token is an HttpOnly cookie and is never returned in JSON.

## Test

Frontend unit tests.

Windows:

```powershell
cd frontend
npm.cmd test
```

macOS/Linux:

```sh
cd frontend
npm test
```

Frontend production build.

Windows:

```powershell
cd frontend
npm.cmd run build
```

macOS/Linux:

```sh
cd frontend
npm run build
```

Backend tests.

Windows:

```powershell
Set-Location backend
.\mvnw.cmd test
Set-Location ..
```

macOS/Linux:

```sh
cd backend
./mvnw test
cd ..
```

Backend PostgreSQL integration tests.

Windows:

```powershell
docker compose up -d db
Set-Location backend
.\mvnw.cmd verify -Pintegration
Set-Location ..
docker compose down
```

macOS/Linux:

```sh
docker compose up -d db
cd backend
./mvnw verify -Pintegration
cd ..
docker compose down
```

Local authentication smoke check after PostgreSQL and the backend are running:

```sh
curl -i http://localhost:8080/api/v1/auth/me
curl -c cookies.txt -i http://localhost:8080/api/v1/auth/csrf
curl -b cookies.txt -c cookies.txt -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <value-from-XSRF-TOKEN-cookie>" \
  -d '{"email":"local.user@example.com","password":"<local-password>"}'
```

The first command should return `401` without a bearer token. The second returns
`204` and establishes the readable XSRF cookie. The login command returns a
short-lived access token and typed user roles when local bootstrap credentials
are valid. The refresh token is stored in an HttpOnly cookie.

## Continuous integration

GitHub Actions runs the `CI` workflow for pull requests targeting `main`, pushes
to `main`, and manual dispatches. Older in-progress runs for the same workflow
and ref are canceled automatically.

The required branch-protection check names are:

- `frontend`
- `backend`

The `frontend` check installs dependencies with `npm ci`, runs
`npm test -- --watch=false`, and runs `npm run build` from `frontend/`.

The `backend` check starts an isolated `postgres:18.4-alpine` service with
CI-only database values, sets a non-production CI-only JWT value, and runs
`./mvnw verify -Pintegration` from `backend/`. This runs Surefire unit tests,
the Failsafe `RefreshSessionIT`, Flyway migrations from an empty PostgreSQL
database, and Hibernate schema validation.

Stop local PostgreSQL without deleting the named volume:

```sh
docker compose down
```

## Product identity

- Product name: `Lookalike`
- Project slug: `lookalike-app`
- Backend artifact: `lookalike-backend`
- Java group and base package: `com.gerardoicu.lookalike`
- Angular application identifier: `lookalike-web`
- Local PostgreSQL database identifier: `lookalike`

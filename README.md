# Angular Spring PostgreSQL Foundation

Reusable starter for a standalone Angular application, a Spring Boot backend,
and local PostgreSQL.

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
frontend/backend deployment; cross-origin production deployment is outside this
foundation.

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

GitHub Template repositories do not copy branch-protection settings. Configure
the required `frontend` and `backend` checks after creating a repository from
this template.

Stop local PostgreSQL without deleting the named volume:

```sh
docker compose down
```

## First use

Use this foundation once to initialize a product identity before business work
begins:

1. Create a repository using GitHub `Use this template`.
2. Clone the generated repository.
3. Create the first Issue using `Initialize project`.
4. Complete it as `PROJECT-001: Initialize product identity`.
5. Open Codex in Plan mode.
6. Paste the Issue URL into the bootstrap prompt below.
7. Review the plan.
8. Approve only with `APPROVE PLAN: PROJECT-001`.
9. Implement on `initialization/001-product-identity`.
10. Merge only after CI passes.
11. Begin business tickets only after initialization is complete.

Copy-ready Codex bootstrap prompt:

```text
Plan PROJECT-001 from this Issue: <paste Issue URL>

Read root AGENTS.md first. Treat the Issue as authoritative scope, but not as
authorization to implement. This first turn is plan-only. Use one agent.

Inspect only current identity files and their direct dependencies. Classify the
exact before/after identity mappings for product display name and purpose,
repository/project slug, Java group, base package, package directories, imports,
tests, Maven coordinates, Angular workspace/application identity and visible
title, local PostgreSQL defaults, and directly affected README, architecture,
and agent rules.

Retain the top-level frontend/ and backend/ directories. Preserve /api/v1,
authentication behavior, roles, schema, Flyway history, dependencies, CI, and
framework versions unless the initialization Issue explicitly introduces a
separately approved gate.

Exclude business modules, entities, tables, sample domain features, production
infrastructure, and speculative abstractions. Propose focused validation.
Finish with NOT AUTHORIZED TO IMPLEMENT.
```

Approved initialization implementation must also remove
`.github/ISSUE_TEMPLATE/initialize.yml` from the generated product repository
after the initialization Issue exists, replace this template-specific section
with the product's actual identity and purpose, replace or remove
template-only initialization rules from `AGENTS.md`, update `ARCHITECTURE.md`
only with the initialized current state, and preserve the created Issue and
merged Pull Request as the durable initialization history.

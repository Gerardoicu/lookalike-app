# Lookalike

Lookalike is a public entertainment web application where anonymous visitors
compare one photo against configured personality profiles.

## Prerequisites

- Node.js `>=24.15.0 <25`
- npm from the same Node installation
- JDK 25
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

## Run

Start the backend.

Windows:

```powershell
$env:JAVA_HOME = '<path-to-jdk-25>'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Location backend
.\mvnw.cmd spring-boot:run
Set-Location ..
```

macOS/Linux:

```sh
export JAVA_HOME=<path-to-jdk-25>
export PATH="$JAVA_HOME/bin:$PATH"
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

`GET /api/v1/health` is public and returns the backend health status.

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

Backend verification.

Windows:

```powershell
Set-Location backend
.\mvnw.cmd verify
Set-Location ..
```

macOS/Linux:

```sh
cd backend
./mvnw verify
cd ..
```

## Continuous integration

GitHub Actions runs the `CI` workflow for pull requests targeting `main`, pushes
to `main`, and manual dispatches. Older in-progress runs for the same workflow
and ref are canceled automatically.

The required branch-protection check names are:

- `frontend`
- `backend`

The `frontend` check installs dependencies with `npm ci`, runs
`npm test -- --watch=false`, and runs `npm run build` from `frontend/`.

The `backend` check runs `./mvnw verify` from `backend/`.

## Product identity

- Product name: `Lookalike`
- Project slug: `lookalike-app`
- Backend artifact: `lookalike-backend`
- Java group and base package: `com.gerardoicu.lookalike`
- Angular application identifier: `lookalike-web`

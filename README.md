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

## Anonymous security configuration

Lookalike uses reusable anonymous anti-abuse controls for future analysis
requests. The current application can still start and serve health without real
Cloudflare credentials. Guarded analysis flows fail closed if required secrets
are missing.

Safe local defaults are configured in `backend/src/main/resources/application.properties`.
Override these values with environment variables when a protected analysis flow
is enabled:

- `LOOKALIKE_TURNSTILE_SECRET_KEY`
- `LOOKALIKE_TURNSTILE_EXPECTED_HOSTNAMES`
- `LOOKALIKE_TURNSTILE_EXPECTED_ACTION`
- `LOOKALIKE_TURNSTILE_SITEVERIFY_CONNECT_TIMEOUT`
- `LOOKALIKE_TURNSTILE_SITEVERIFY_READ_TIMEOUT`
- `LOOKALIKE_MULTIPART_FILE_SIZE_THRESHOLD`
- `LOOKALIKE_VISITOR_COOKIE_SIGNING_SECRET`
- `LOOKALIKE_VISITOR_COOKIE_SECURE`
- `LOOKALIKE_VISITOR_COOKIE_MAX_AGE`
- `LOOKALIKE_ANALYSIS_COOLDOWN`
- `LOOKALIKE_AI_MODEL_DIR`
- `LOOKALIKE_FACE_MAX_IMAGE_BYTES`
- `LOOKALIKE_FACE_MAX_WIDTH`
- `LOOKALIKE_FACE_MAX_HEIGHT`
- `LOOKALIKE_FACE_MAX_PIXELS`
- `LOOKALIKE_FACE_DETECTOR_INPUT_SIZE`
- `LOOKALIKE_FACE_MIN_CONFIDENCE`
- `LOOKALIKE_RATE_LIMIT_CAPACITY`
- `LOOKALIKE_RATE_LIMIT_WINDOW`
- `LOOKALIKE_RATE_LIMIT_CLEANUP_INTERVAL`
- `LOOKALIKE_MAX_KNOWN_CONTENT_LENGTH_BYTES`
- `LOOKALIKE_MAX_UPLOAD_SIZE`
- `LOOKALIKE_MAX_REQUEST_SIZE`

Do not commit real secret values. Cloudflare Turnstile site keys are public
client configuration; Turnstile secret keys and visitor-cookie signing secrets
must remain backend-only.

## Local facial analysis check

FACE-001 adds `POST /api/v1/facial-analyses` for one transient JPEG upload.
Uploaded photos are validated and processed in memory only. They are not written
to databases, filesystem storage, object storage, logs, or analysis history.

Download the AI-001 model files into the ignored local model directory described
in `docs/ai-001-facial-similarity-engine.md`, then run the backend with
Cloudflare Turnstile test credentials:

```powershell
Set-Location backend
$env:LOOKALIKE_VISITOR_COOKIE_SIGNING_SECRET="local-dev-secret-with-enough-entropy"
$env:LOOKALIKE_TURNSTILE_SECRET_KEY="1x0000000000000000000000000000000AA"
$env:LOOKALIKE_TURNSTILE_EXPECTED_HOSTNAMES="example.com"
$env:LOOKALIKE_TURNSTILE_EXPECTED_ACTION=""
$env:LOOKALIKE_AI_MODEL_DIR=(Resolve-Path "src\test\resources\ai-001\local\models").Path
.\mvnw.cmd spring-boot:run
```

Send one JPEG using Cloudflare's dummy token. Cloudflare's dummy Siteverify
response uses the test hostname `example.com`; for localhost widget testing,
configure the expected hostname as `localhost` and submit a real widget token
instead.

```powershell
curl.exe -i -X POST "http://localhost:8080/api/v1/facial-analyses" `
  -H "X-Turnstile-Token: XXXX.DUMMY.TOKEN.XXXX" `
  -F "image=@C:\path\one-face.jpg;type=image/jpeg"
```

A valid one-face image returns `{"successful":true}` and sets the anonymous
visitor cookie. Corrupt images return `FACE_IMAGE_CORRUPT`, no-face images
return `FACE_NO_USABLE_FACE`, multiple-face images return
`FACE_MULTIPLE_USABLE_FACES`, and an immediate repeat with the returned cookie
returns `SECURITY_COOLDOWN_ACTIVE`.

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

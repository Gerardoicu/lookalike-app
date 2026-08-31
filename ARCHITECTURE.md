# Architecture

This repository is the Lookalike Angular and Spring foundation. This document is
the authority for backend, frontend, API, testing, security-boundary, and
clean-code decisions. `AGENTS.md` remains the operational policy for agents and
points here for cross-cutting design rules.

## Purpose And Boundaries

Lookalike has three local boundaries:

- `frontend/`: standalone Angular application with Angular Material, routing,
  and typed HTTP infrastructure.
- `backend/`: Spring Boot application exposing HTTP APIs.

Lookalike is a public entertainment web application where anonymous visitors
compare one photo against configured personality profiles. The repository
currently exposes public health only.

Do not add business modules, databases, caches, server-side sessions,
authentication frameworks, roles, production infrastructure, or speculative
architecture outside an approved ticket.

## Runtime Dependency Direction

Runtime dependency direction is one-way:

- Browser clients load the Angular application.
- Angular calls versioned backend APIs through relative `/api/v1/**` URLs.
  Local development uses the Angular proxy; production is same-origin.
- Spring MVC exposes backend HTTP endpoints under `/api/v1`.

## Security Boundaries

Lookalike is anonymous and has no accounts, login, roles, or protected user
resources. Public access must remain deliberate. `GET /api/v1/health` is public
and simple.

Anonymous analysis requests use a reusable anti-abuse boundary before expensive
work executes:

1. Request-size precheck.
2. Signed visitor cookie validation.
3. In-memory fixed-window burst rate limit.
4. Ten-minute cooldown check.
5. Cloudflare Turnstile token verification.
6. Feature-owned request validation and AI inference.

The signed visitor cookie is the source of truth for the cooldown. It contains a
generated visitor id and the timestamp of the last successful analysis, signed
with HMAC-SHA256. Passing Turnstile alone must not start the cooldown; feature
controllers record a successful analysis only after the feature work completes.

Rate limiting is in-memory and keyed by a validated visitor id when available,
or by the servlet remote address when no valid visitor cookie exists. Arbitrary
forwarded-IP headers are not trusted. Cloudflare proxy header trust is a later
deployment concern.

Cloudflare Turnstile is rendered explicitly in the Angular SPA. The backend is
the only caller of Siteverify and validates success, configured hostnames,
configured action, missing or oversized tokens, invalid tokens, and
`timeout-or-duplicate` responses. Turnstile secrets and cookie signing secrets
are external configuration and must not be committed.

Do not add authentication, authorization, accounts, sessions, persistent visitor
records, browser fingerprinting, or protected resources without an approved
ticket that establishes a concrete security boundary.

## Backend Layer Ownership

Backend code is organized by business feature. Shared technical infrastructure
may exist only when it has a clear owner and stable cross-feature purpose.

Controllers own HTTP mapping, request validation entry points, principal access,
status codes, headers, response DTO selection, and delegation to services. They
must not access repositories, own transactions, contain business rules, or expose
entities.

Services own use cases, orchestration, and business behavior. Service
interfaces are created only when multiple implementations exist or when a
meaningful adapter boundary exists.

Persistence repositories are not part of the current foundation. Do not add
repositories or a persistence layer without an approved persistence requirement.

## API Contracts And Error Handling

Public APIs use strongly typed request and response DTOs. Java records are
preferred for immutable contracts and projections.

Entities must not cross the API boundary. Spring Data `Page` must not cross the
API boundary. A purpose-specific `PageResponse<T>` is allowed when pagination is
part of an endpoint contract. A universal `ApiResponse<T>` wrapper containing
`success`, `data`, and `error` is prohibited.

HTTP errors use RFC 9457 `ProblemDetail`, centralized exception translation,
stable backend `ErrorCode` enum values, and typed field-validation details.
Clients must branch on stable machine-readable codes and fields, not
human-readable messages.

Sorting parameters exposed by APIs must map through approved typed values or
owner-local constants. Do not pass arbitrary backend internals from the frontend
into backend processing.

## Transactions And External I/O

External I/O calls must not execute once per element inside loops or streams
when batching is possible.
Implementations must collect unique identifiers, execute bounded batch queries,
create typed lookup maps, handle missing and duplicate identifiers, preserve
required input order, and chunk exceptionally large identifier collections.

## Frontend Architecture

Angular code is organized by feature. Application-wide infrastructure belongs in
`core`. `shared` stays small and genuinely reusable.

The Angular application remains standalone with routing enabled, SCSS styles,
Angular Material, TypeScript strictness, and typed reactive forms. Prefer
`inject()`, functional interceptors and guards, signals for local and derived
synchronous state, and RxJS for asynchronous composition.

Do not use `any`, double assertions, type-suppression comments as workarounds,
unsafe assertion chains, nested subscriptions, duplicated HTTP requests, global
helper dumping grounds, or arbitrary backend property names for sorting.

Frontend APIs use relative `/api/v1/**` URLs.

## Controlled Values And Clean Code

Represent controlled sets with enums, framework-provided typed values, or
focused owner-local constants. Do not add generic `Constants`, `Utils`,
`Common`, or `Helpers` classes.

Repeated behavior is extracted only when occurrences share meaning, ownership,
semantics, and reason to change. Keep abstractions small, local, and justified
by current code. KISS and YAGNI are mandatory. Do not add speculative
dependencies, framework layers, code generators, mapping libraries, or
infrastructure.

One-off prose, intentional invalid test inputs, and independent
external-contract expectations may stay inline when extracting them would reduce
clarity.

## Testing Responsibilities

Tests follow architectural boundaries:

- MVC tests prove HTTP mapping, validation, serialization, security, status
  codes, headers, and error contracts.
- Service tests prove business behavior and orchestration.
- Angular tests cover components, services, forms, HTTP behavior, and
  interceptors or guards when present.

Bugs and non-obvious rules require focused regression tests.

## Explicit Prohibitions

The foundation explicitly prohibits:

- Persistence or external I/O inside batchable loops or streams.
- Universal success/data/error API wrappers.
- Clients branching on human-readable error messages.
- Unsafe raw controlled strings.
- Generic utility or constants dumping grounds.
- Speculative dependencies, code generators, or architecture layers.

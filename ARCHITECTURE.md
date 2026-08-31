# Architecture

This repository is the Lookalike Angular, Spring, and PostgreSQL foundation. This
document is the authority for backend, frontend, API, persistence, database,
testing, and clean-code decisions. `AGENTS.md` remains the operational policy
for agents and points here for cross-cutting design rules.

## Purpose And Boundaries

Lookalike has three local boundaries:

- `frontend/`: standalone Angular application with Angular Material, routing,
  and typed authentication infrastructure.
- `backend/`: Spring Boot application exposing HTTP APIs, enforcing
  authentication and roles, and owning database migrations.
- `compose.yaml`: local PostgreSQL dependency for development and integration
  checks.

Lookalike is a public entertainment web application where anonymous visitors
compare one photo against configured personality profiles. The repository
currently exposes public health, authentication endpoints, current-user
identity, and an admin check for authorization verification.

Do not add business modules, entities, tables, roles, production infrastructure,
or speculative architecture outside an approved ticket.

## Runtime Dependency Direction

Runtime dependency direction is one-way:

- Browser clients load the Angular application.
- Angular calls versioned backend APIs through relative `/api/v1/**` URLs.
  Local development uses the Angular proxy; production is same-origin.
- Spring MVC exposes backend HTTP endpoints under `/api/v1`.
- Spring Security validates short-lived JWT access tokens and enforces backend
  authorization.
- Spring Data JPA talks to PostgreSQL.
- Flyway applies schema changes before JPA validates mappings.
- Refresh-token families and single-use refresh sessions are stored server-side
  as hashes so they can be rotated, revoked, expired, and invalidated by logout.

Frontend guards are navigation UX only. Backend authorization is the security
boundary.

## Security Boundaries

The backend owns authentication, authorization, token validation, role checks,
and protected resource decisions. Angular may hide navigation or redirect users
for usability, but it must not be treated as an authorization control.

Access tokens are short-lived and kept in memory by the frontend. Refresh tokens
are HttpOnly cookies. Cookie-backed authentication POSTs require CSRF
protection. Refresh session token material is stored server-side only as a hash.

Public access must remain deliberate. `GET /api/v1/health` is public and simple.
Authentication endpoints may be public only where the authentication flow
requires it. Protected endpoints require backend-enforced authentication and, if
applicable, backend-enforced roles.

## Backend Layer Ownership

Backend code is organized by business feature. Shared technical infrastructure
may exist only when it has a clear owner and stable cross-feature purpose.

Controllers own HTTP mapping, request validation entry points, principal access,
status codes, headers, response DTO selection, and delegation to services. They
must not access repositories, own transactions, contain business rules, or expose
entities.

Services own use cases, orchestration, business behavior, authorization-adjacent
application decisions, and transaction boundaries. Service interfaces are
created only when multiple implementations exist or when a meaningful adapter
boundary exists.

Repositories own persistence only. They must not contain business workflows,
HTTP concerns, security decisions, or generic application behavior. Do not add a
generic application `BaseRepository`.

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
owner-local constants. Do not pass arbitrary backend property names from the
frontend into persistence queries.

## Persistence And Query Selection

Use the simplest query mechanism that fits the use case:

- Derived Spring Data methods for short fixed predicates.
- JPQL for readable fixed queries.
- Projections for partial read models.
- Specification or Criteria for genuinely dynamic optional filters.
- Custom repository fragments for complex persistence behavior.

Native queries are prohibited in application persistence code, including
`nativeQuery = true`, `@NativeQuery`, `@NamedNativeQuery`,
`EntityManager.createNativeQuery`, and JDBC-based repository bypasses. Flyway SQL
is excluded from this prohibition. Any future native-query exception requires
evidence, PostgreSQL integration testing, impact analysis, and explicit
architecture approval.

JPQL property names may remain inside the query-language contract, but query
values must always be typed parameters and never string-concatenated.

Criteria and programmatic JPA paths must use the standard static metamodel
generated by Hibernate, such as
`root.join(Order_.customer).get(Customer_.id)`. Experimental Lombok
`@FieldNameConstants` is prohibited. This decision selects the standard JPA
static metamodel as the type-safe mechanism but does not add Lombok, Hibernate
Processor, or any generated-output dependency under this documentation ticket.

## Transactions And External I/O

Services own transaction boundaries. Controllers do not start transactions.
Repositories participate in transactions for persistence work but do not
orchestrate use cases.

Repository, `EntityManager`, HTTP-client, or other external I/O calls must not
execute once per element inside loops or streams when batching is possible.
Implementations must collect unique identifiers, execute bounded batch queries,
create typed lookup maps, handle missing and duplicate identifiers, preserve
required input order, and chunk exceptionally large identifier collections.

Fetch plans are selected per use case through projections, fetch joins, entity
graphs, or batch loading. Associations are lazy by design. Global eager loading
must not be used as an N+1 workaround.

## Entity And Relationship Rules

JPA entities model persistence state and invariants, not API contracts. They
should keep relationships deliberate, lazy by default, and scoped to real
navigation needs.

Do not use Lombok `@Data` on JPA entities because generated equality, string
rendering, and accessors can accidentally traverse lazy relationships or expose
mutable persistence state. Lombok may be used only where the generated behavior
is explicit and safe for the type's persistence semantics.

Closed enums are represented with Java and TypeScript enums and persisted by
name. Do not compare raw controlled strings in authorization rules,
configuration logic, cryptographic configuration, protocol handling, or other
controlled-value decisions.

Optimistic locking, audit columns, soft deletion, triggers, stored procedures,
partitioning, and speculative indexes are not default choices. Add them only
when an approved ticket establishes a concrete requirement.

## Database And Flyway Rules

Flyway is the only schema-change authority. Hibernate validates mappings only,
with `spring.jpa.hibernate.ddl-auto=validate`. Applied migrations are immutable;
corrections require new forward migrations.

Database objects use lower `snake_case` and a consistent plural-table
convention. Tables require primary keys and deliberate decisions for `NOT NULL`,
`UNIQUE`, `CHECK`, foreign keys, delete behavior, and indexes.

Use PostgreSQL-native types deliberately:

- Money uses `numeric(precision, scale)` and Java `BigDecimal`.
- Moments use `timestamptz` and Java `Instant`.
- Date-only values use `date` and Java `LocalDate`.
- Closed enums are persisted by name.

Local PostgreSQL is defined in root `compose.yaml` with service name `db`,
image `postgres:18.4-alpine`, port `5432`, environment-overridable values, and
storage mounted at `/var/lib/postgresql`.

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

Frontend APIs use relative `/api/v1/**` URLs. Frontend guards and visible UI
state are usability features only; backend security remains authoritative.

## Controlled Values And Clean Code

Represent controlled sets with enums, framework-provided typed values, or
focused owner-local constants. Do not add generic `Constants`,
`SecurityConstants`, `Utils`, `Common`, or `Helpers` classes.

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
- Service tests prove business behavior, orchestration, authorization-adjacent
  decisions, and transaction outcomes.
- PostgreSQL integration tests prove migrations, mappings, repositories,
  projections, transaction semantics, constraints, and performance-sensitive
  fetch plans.
- Angular tests cover components, services, forms, interceptors, guards, and
  HTTP behavior.

H2 is prohibited. PostgreSQL is the integration database. Bugs and non-obvious
rules require focused regression tests.

## Explicit Prohibitions

The foundation explicitly prohibits:

- Native application queries.
- Experimental Lombok `@FieldNameConstants`.
- Lombok `@Data` on JPA entities.
- Persistence or external I/O inside batchable loops or streams.
- Global eager-loading workarounds.
- Entities in API contracts.
- Spring Data `Page` in API contracts.
- Universal success/data/error API wrappers.
- Clients branching on human-readable error messages.
- Unsafe raw controlled strings.
- Generic utility or constants dumping grounds.
- Speculative dependencies, code generators, or architecture layers.

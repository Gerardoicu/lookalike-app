# Repository Agent Rules

1. Keep this repository a reusable Angular, Spring, and PostgreSQL foundation.
2. Preserve `.git/` and never commit or push unless the user explicitly asks.
3. Do not modify or delete `.idea/`; keep it ignored from the root.
4. Keep all repository artifacts in English.
5. Keep root hygiene files authoritative for editor, git, and environment rules.
6. Do not add nested `.git` directories, nested `AGENTS.md` files, or AI-tool configuration.
7. Do not add root convenience scripts unless the repository direction changes.
8. Keep the frontend in `frontend/`, the backend in `backend/`, and local PostgreSQL in root `compose.yaml`.
9. Do not containerize the frontend or backend.
10. `ARCHITECTURE.md` is the authoritative reference for backend, frontend, API, persistence, database, testing, security-boundary, and clean-code decisions.
11. Keep this file operational and concise; do not duplicate the architecture manual here.

## Task Lifecycle

12. A GitHub Issue defines requirements and scope. It does not authorize implementation.
13. Pasting, assigning, referencing, or asking to implement an Issue does not approve a gated implementation plan.
14. Roadmap text and prior conversation do not approve a plan.
15. Generic messages such as "continue", "proceed", "implement it", or "start the ticket" do not approve gated changes.
16. Small, explicit, non-gated fixes may be implemented directly when the user asks for implementation.
17. Do not expand gated planning into speculative architecture or unrelated research.

## Mandatory Preflight

18. Before any file write, dependency installation, schema command, or implementation action, identify whether the task triggers an approval gate.
19. Approval gates include authentication or authorization.
20. Approval gates include database schema or migration changes.
21. Approval gates include public API additions or breaking changes.
22. Approval gates include production dependency additions or replacements.
23. Approval gates include cross-module abstractions.
24. Approval gates include destructive or irreversible actions.
25. Approval gates include material scope beyond the ticket.
26. If any gate is triggered, the first turn must be plan-only.
27. Plan-only means no file changes, installations, migrations, staging, commits, or implementation commands.

## Required Plan

28. The plan must identify the ticket.
29. The plan must identify triggered gates.
30. The plan must identify proposed files.
31. The plan must identify dependencies.
32. The plan must identify API changes.
33. The plan must identify schema changes.
34. The plan must identify security decisions.
35. The plan must identify tests.
36. The plan must identify exclusions.
37. The plan must identify material risks.
38. Stop after presenting the plan.

## Explicit Approval

39. The only implementation approval for a gated ticket is a user message matching `APPROVE PLAN: <TICKET-ID>`.
40. Approval applies only to the latest plan for that exact ticket.
41. An Issue, assignment, branch, previous implementation request, or successful test is not approval.
42. If the approved plan changes materially during implementation, stop and present the plan delta.
43. A material plan delta requires a user message matching `APPROVE PLAN UPDATE: <TICKET-ID>`.
44. Final reports for gated implementation must state which approval phrase authorized the work.
45. If no valid approval exists for a gated change, the final verdict must be `NOT AUTHORIZED TO IMPLEMENT`.

## Branch Protection

46. Planning may occur on `main` because it is read-only.
47. Before implementation, `main` must be clean and synchronized.
48. Approved ticket implementation must occur on a dedicated English task branch.
49. Never implement a ticket directly on `main`.
50. If `main` is dirty before a new ticket, stop instead of stashing, resetting, or moving unrelated work automatically.

## Project Initialization Gate

- While template placeholder identity remains, business feature implementation is blocked.
- The first product task must be `PROJECT-001: Initialize product identity`, created through the initialization Issue form.
- The first turn is plan-only, and implementation requires exactly `APPROVE PLAN: PROJECT-001`.
- Initialization occurs on `initialization/001-product-identity`.
- Permitted initialization changes are product identity, Java, Maven, and Angular identifiers, local database identifiers, directly affected current documentation, and identity-specific rules already present in `AGENTS.md`.
- Initialization must remove the one-time issue form and template-only first-use instructions from the generated product.
- Initialization must not add business modules, entities, tables, roles, dependencies, deployment infrastructure, or speculative architecture.
- Business work starts only after initialization is merged and CI passes.
- Reusable-template maintenance remains allowed when the task explicitly targets the generic foundation rather than a product feature.

## Implementation Rules

51. Follow `ARCHITECTURE.md` for layer ownership, API contracts, persistence, database, Angular, testing, controlled-value, Lombok, JPA metamodel, and clean-code decisions.
52. Use Angular CLI and Angular Core `22.1.x`.
53. Use Angular Material `22.1.0`.
54. Keep TypeScript strict.
55. Do not add frontend libraries beyond Angular, Angular Material, RxJS, and test/build tooling without an approved gated plan.
56. Use Java 25 and Spring Boot `4.1.0`.
57. Keep the Maven artifact `backend`, group `com.example`, and base package `com.example.foundation`.
58. Use the Maven Wrapper from `backend/` for Maven commands.
59. Use the JDK exposed through `JAVA_HOME` and `PATH`; do not search for or hardcode workstation-specific JDK paths.
60. Run Maven Wrapper commands from `backend/`, where `pom.xml` is located.
61. Retain `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/mvnw`, and `backend/mvnw.cmd`.
62. Do not add Actuator, H2, Testcontainers, mapping libraries, code generators, speculative infrastructure, or other dependencies without an approved gated plan.
63. Keep `GET /api/v1/health` public and simple.
64. Do not add a service for health unless it gains business behavior.
65. Keep controller tests as MVC-slice tests when PostgreSQL is not needed.
66. Do not keep generated full-context tests that require a database.

## Database And Local Environment

67. Keep `spring.jpa.hibernate.ddl-auto=validate`.
68. Keep `spring.jpa.open-in-view=false`.
69. Treat Flyway as the only schema-change authority.
70. Put migrations under `backend/src/main/resources/db/migration/`.
71. Use safe local-development defaults for datasource properties.
72. Allow datasource settings to be overridden by environment variables.
73. Keep `.env.example` safe for local development and never store production credentials.
74. Ignore `.env`.
75. Use PostgreSQL service name `db` in Compose.
76. Use the `postgres:18.4-alpine` image unless intentionally upgraded.
77. Mount PostgreSQL storage at `/var/lib/postgresql`.
78. Do not change Compose to a PostgreSQL legacy data subdirectory mount.
79. Expose local PostgreSQL on port `5432`.
80. Keep Compose values environment-overridable.

## Documentation And Completion

81. Keep README commands current for Windows, macOS, and Linux.
82. Keep `ARCHITECTURE.md` limited to current boundaries, dependency direction, and approved conventions.
83. Keep issue templates focused on approved requirement fields and the gated-plan notice.
84. Keep pull request notes focused on behavior, verification, risk, and gate compliance.
85. Remove unused code and dependencies when a slice is removed.
86. Validate with the documented checks before declaring implementation complete.
87. Keep CI cloud-provider-neutral, least-privilege, pinned to full action SHAs, and validation-only until deployment is approved.
88. Keep this file at or below 200 lines by consolidating rules rather than appending duplicates.

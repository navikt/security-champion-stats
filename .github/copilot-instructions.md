# Copilot instructions

## Project

Security Champion Stats is a Nav employee web application for the Security Champion program. Employees can view
participants and events; participants can view their membership and scores; administrators manage participation,
scoring, integrations, and program dashboards.

This repository contains a Kotlin/Spring Boot backend and a TypeScript/Next.js frontend. The backend uses PostgreSQL
and Flyway. Integrations include Entra ID, Teamkatalogen, Slack, Delta, and GitHub. Applications are built and
deployed through the repository's GitHub Actions and NAIS configuration.

## Working agreements

- Leave touched code better than you found it. When adding or changing a feature, also clean up the old code in the area
  you touch that violates these guidelines (for example test or local data and profile branches in production code,
  dead code, or misleading names), and update affected tests and docs. Do not widen the cleanup to unrelated areas;
  list those separately as follow-ups.
- Keep changes focused. Break larger work into testable steps and verify each step before proceeding.
- For substantial or ambiguous changes, present the proposed approach and get approval before editing.
- Do not add unrelated features, broad documentation, or comments that only restate the code.
- Preserve existing behavior unless a change is part of the approved request. Do not discard unrelated worktree changes.
- Describe behavior changes and their rationale in commit messages and PR bodies; use Git history and blame to
  trace why code changed. Update READMEs only when setup, usage, or operational guidance needs correcting, not as
  a change log.
- Never expose credentials, personal data, or internal exception details in API responses.

## Architecture and code

- Keep dependencies directed inward: application logic should not depend on integration or web-framework details.
- Keep production code (`src/main`) free of mock, fixture, test, or local-only data and logic. Do not branch on
  `local` or `test` profiles in production classes. Put fixtures and stubs under `src/test`, and point local runs at
  stubs through configuration (for example a base URL).
- Use constructor injection for Spring dependencies. Introduce interfaces where they clarify an application boundary or
  improve testability; avoid abstractions without a clear purpose.
- Keep persistence failures explicit. Do not convert unexpected database errors into empty results or success-shaped
  responses; allow them to reach the appropriate error boundary.
- Use idiomatic Kotlin and TypeScript, explicit null handling, and the established patterns in nearby code.
- Keep frontend API route handlers consistent: proxy backend status, body, and content type; use RFC 9457 Problem
  Details (`application/problem+json`) for errors produced at the frontend boundary.
- Backend HTTP errors use RFC 9457 Problem Details. Return sanitized details for unexpected errors and log server-side
  failures appropriately.
- Validate untrusted input and enforce authorization in the backend; frontend checks are not an authorization boundary.
- Add database changes as Flyway migrations under `apps/backend/src/main/resources/db/migration/` using
  `V{version}__{description}.sql`. Prefer additive, data-preserving migrations.

## Tests and verification

- Backend tests use JUnit 5 and MockK; controller tests use Spring MockMvc, and database integration tests use
  Testcontainers. Keep tests under `apps/backend/src/test/kotlin/` in the corresponding package structure.
- Frontend tests use Vitest and Testing Library. Keep tests near the feature or source file they exercise.
- Run checks for every affected app:
  - Backend: from `apps/backend`, run `./gradlew build`.
  - Frontend: from `apps/frontend`, run `pnpm test` and `pnpm build`.
- Report any failed or blocked checks accurately; do not claim unrun checks passed.

### Time and timestamp assertions

- Use fixed ISO 8601 timestamps, normally at whole-second precision, for persistence and lifecycle fixtures. Derive later syncs, deployments, and cutoffs from the same fixed instant instead of separate `now()` calls.
- Assert exact equality for supplied timestamps that the database can represent, including timestamps nested in data classes, lists, and maps.
- Do not compare live-clock timestamps by truncating both sides to milliseconds, seconds, or minutes. Java and Kotlin instants support nanoseconds; PostgreSQL stores microseconds and can round into the next millisecond or second.
- For timestamps generated inside the code under test, use an injected fixed clock where supported. Otherwise, assert a bounded time range with an explicit allowance for database precision.
- Keep the full date and timezone. Do not compare only `HH:mm` or `mm:ss`, which can hide incorrect dates.
- Use subsecond fixtures only when that precision is the behavior under test, and account explicitly for the storage boundary's rounding. Do not reduce production timestamp precision to fix a test.
- Keep SLA, ordering, and retention boundary tests deterministic with explicit times on each side of the boundary. Do not add sleeps or broad tolerances to make them pass.

## Relevant references

- `apps/backend/Readme.md` and `apps/frontend/Readme.md`: application setup and behavior.
- `.github/workflows/`: CI and deployment workflows.

# Copilot instructions

## Project

Security Champion Stats is a Nav employee web application for the Security Champion program. Employees can view
participants and events; participants can view their membership and scores; administrators manage participation,
scoring, integrations, and program dashboards.

This repository contains a Kotlin/Spring Boot backend and a TypeScript/Next.js frontend. The backend uses PostgreSQL
and Flyway. Integrations include Entra ID, Teamkatalogen, Slack, Delta, and GitHub. Applications are built and
deployed through the repository's GitHub Actions and NAIS configuration.

## Working agreements

- Keep changes focused. Break larger work into testable steps and verify each step before proceeding.
- For substantial or ambiguous changes, present the proposed approach and get approval before editing.
- Do not add unrelated features, broad documentation, or comments that only restate the code.
- Preserve existing behavior unless a change is part of the approved request. Do not discard unrelated worktree changes.
- When changing behavior or setup, update the relevant app README briefly.
- Never expose credentials, personal data, or internal exception details in API responses.

## Architecture and code

- Keep dependencies directed inward: application logic should not depend on integration or web-framework details.
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

## Relevant references

- `apps/backend/Readme.md` and `apps/frontend/Readme.md`: application setup and behavior.
- `.github/workflows/`: CI and deployment workflows.

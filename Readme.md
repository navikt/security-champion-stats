# Security Champion Stats

A web application for the Nav Security Champion program and its administrators.

## Applications

| Application | Description | Documentation |
| --- | --- | --- |
| Backend | Kotlin and Spring Boot API, backed by PostgreSQL | [Backend README](apps/backend/Readme.md) |
| Frontend | Next.js web application for participants and administrators | [Frontend README](apps/frontend/Readme.md) |

## Tech stack

| Area | Technologies |
| --- | --- |
| Backend | Kotlin, Spring Boot, PostgreSQL, Flyway |
| Frontend | TypeScript, React, Next.js, Nav Aksel |

## Run locally

Install and [activate mise](https://mise.jdx.dev/getting-started.html) to manage development tools.
Run `mise install` in each application directory to install the tools declared in its `mise.toml`.
Install Docker separately; they are not included in the mise configuration.
Start the backend and frontend in separate terminals; the frontend's local mode expects the backend to be running.

```bash
cd apps/backend
mise install
docker compose up -d postgres team-catalog-stub
./gradlew bootRun --args='--spring.profiles.active=local'
```

```bash
cd apps/frontend
mise install
pnpm install
pnpm dev:local
```

The frontend is available at `http://localhost:3000`. See the application READMEs for tests, configuration, and
additional development details.

## CI and deployment

Each application has its own GitHub Actions workflows for tests, builds, and deployment to NAIS. Deployment
manifests are in [`apps/backend/.nais/`](apps/backend/.nais/) and [`apps/frontend/.nais/`](apps/frontend/.nais/).

# Security Champion Stats backend

The Kotlin and Spring Boot API for the Security Champion Stats application.

## Run locally

**Prerequisites:** [mise](https://mise.jdx.dev/getting-started.html), activated in your shell, and Docker.
`mise install` installs Java 25 and the other tools declared in [`mise.toml`](mise.toml).

From `apps/backend`, start the local PostgreSQL database and Teamkatalogen stub, then run the application with the
local profile:

```bash
mise install
docker compose up -d postgres team-catalog-stub
./gradlew bootRun --args='--spring.profiles.active=local'
```

The API runs at `http://localhost:8080`. Start the [frontend](../frontend/Readme.md) separately to use the full
application.

## API documentation

The OpenAPI document is generated from the Spring MVC controllers and DTOs at
[`http://localhost:8080/v3/api-docs`](http://localhost:8080/v3/api-docs). Open Swagger UI at
[`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) to browse it while the backend is
running. In production, API requests use Entra ID authentication; administrator operations require membership in the
configured Entra group.

## Build and test

```bash
./gradlew build
```

## Tech stack

- Kotlin and Spring Boot
- PostgreSQL with Flyway migrations
- Integrations include Teamkatalogen, Slack, Delta, Microsoft Graph, and GitHub

## Configuration

Application defaults are in [`application.yaml`](src/main/resources/application.yaml); local overrides are in
[`application-local.yaml`](src/main/resources/application-local.yaml). The local Docker Compose setup starts
PostgreSQL and the WireMock Teamkatalogen stub. Database changes belong in
[`src/main/resources/db/migration/`](src/main/resources/db/migration/).

## Deployment

The [backend deployment workflow](../../.github/workflows/deploy-backend.yaml) builds and deploys the service to
NAIS on pushes to `main`. The deployment manifest is [`.nais/nais.yaml`](.nais/nais.yaml).

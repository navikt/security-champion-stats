# Security Champion Stats backend application

## Overview
The backend application is built using Kotlin and Spring Boot, and it serves as the API for the frontend application.
It provides endpoints for fetching security champion statistics, managing security champions, and supporting the
Security Champion program over time. The backend application is responsible for handling business logic, data storage
and retrieval, and authentication and authorization for the frontend application.

Participation is managed in the application: employees can self-enroll, and administrators can manage
active status. The scheduled Teamkatalogen sync refreshes profiles for existing participants without
creating, deactivating, or restoring participation.

### Data flow ([mermaid](https://github.blog/2022-02-14-include-diagrams-markdown-files-mermaid/) syntax)
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend
    participant DB as Database
    participant TK as Teamkatalogen
    participant Slack as Slack
    participant BES as Backend scheduler
    participant Scoring as Slack scoring job

    FE->>BE: Request one of the endpoints (e.g. get security champion stats)
    BE->>DB: Query for data related to the request (e.g. security champion stats)
    DB-->>BE: Return or update data (e.g. security champion stats)
    BE-->>FE: Return response (e.g. security champions stats)
    
    BES->>TK: Get all teams
    TK-->>BES: [team, team, …]
    BES->>DB: Fetch existing program participants
    DB-->>BES: [participant, participant, …]
    BES-->>BES: Match profile details by NAVident and email
    BES->>DB: Update profile details without changing participation
    loop paginated
        Scoring->>DB: Fetch active members eligible for point calculation
        DB-->>Scoring: [member, member, …]
        Scoring->>Slack: Get Slack activity for each member
        Slack-->>Scoring: [activity, activity, …]
        Scoring->>DB: Update points for qualifying activity
    end    
```

## How to run
To run the backend application, follow these steps:
1. Make sure you have Java 25 installed on your machine.
2. Start the local database: `docker compose up -d postgres`
3. Run the application with the local profile: `./gradlew bootRun --args='--spring.profiles.active=local'`
4. To run tests, use the command: `./gradlew test`
5. Swagger API documentation is available at `http://localhost:8080/swagger-ui.html` (no authentication required in local profile).

In production, Swagger endpoints are protected with Basic Authentication. Configure credentials in `application.yaml`:
```yaml
swagger:
  username: admin
  password: your-secure-password
```
Access Swagger UI via browser at `http://localhost:8080/swagger-ui.html` and use the configured credentials when prompted.

This is best run together with the frontend application so you can see the data in the UI. To run the frontend
application, follow the instructions in `apps/frontend/Readme.md`.

## Useful Commands

**View PostgreSQL logs (all statements):**
```shell
docker compose logs -f postgres
```
The database container is configured with `log_statement=all`, so every SQL query is logged to stderr and visible here.

**Connect to the local database (psql):**
```shell
docker exec -it postgres psql -U security -d security_champion_stats
```

**List tables and inspect data:**
```sql
\dt
SELECT * FROM member;
```

**Reset the database (wipe volume and restart):**
```shell
docker compose down -v && docker compose up -d postgres
```
Flyway will re-run all migrations on the next application startup.

**Run only a specific test class:**
```shell
./gradlew test --tests "navikt.appsec.securitychampionapp.*ClassName*"
```

## Technologies Used
- Kotlin: A modern programming language that runs on the JVM and is fully interoperable with Java
- Spring Boot: A framework for building production-ready applications with Java and Kotlin
- PostgreSQL: A powerful, open-source relational database management system
- Flyway: A database migration tool that helps manage and version control database schema changes
- JUnit: A testing framework for Java and Kotlin applications
- MockK: A mocking library for Kotlin
- Docker: A platform for developing, shipping, and running applications in containers

## Folder Structure
```
src/main/kotlin/.../
├── app/
│   ├── api/
│   │   ├── Controller.kt           # Public API endpoints (/api/*)
│   │   ├── AdminController.kt      # Admin-only endpoints (/api/admin/*)
│   │   └── dto/                    # Request/response DTOs
│   └── jobs/
│       ├── SyncJob.kt              # Daily sync: updates profiles for existing participants
│       ├── CalculatePointsJob.kt   # Daily job: calculates Slack activity points for all members
│       └── ResetPointsSyncJob.kt   # Scheduled job: resets all points and levels
├── config/                         # Spring configuration (Security, Swagger, Slack, TeamCatalog, Web)
├── integrations/
│   ├── postgress/                  # PostgreSQL repository, job lock, and DTOs
│   ├── slack/                      # Slack API service, activity and channel membership services
│   └── teamCatalog/                # Teamkatalogen client and DTOs
├── security/                       # Token introspection, auth filter, and principal DTOs
└── utils/
    └── Validate.kt                 # Input validation and level calculation

src/main/resources/
├── application.yaml                # Main configuration
├── application-local.yaml          # Local dev overrides (mocked integrations)
├── db/migration/                   # Flyway SQL migrations (V1–V11)
└── mock/                           # Static mock responses for Slack and Teamkatalogen (local profile)

gradle/libs.versions.toml           # Centralized dependency version catalog
```

### API Endpoints

**Public (`/api`)**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/members` | List active participant names and teams |
| GET | `/api/validate` | Validate the current user and return participation status |
| GET | `/api/membership` | Fetch the authenticated user's participation details |
| POST | `/api/enroll` | Enroll the authenticated employee in the program |

**Admin (`/api/admin`)** — requires admin role
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/member` | Manually add a member |
| DELETE | `/api/admin/member/{id}` | Delete a member |
| GET | `/api/admin/participants` | List program participants |
| PUT | `/api/admin/participants/{id}/status` | Activate or deactivate a participant |
| DELETE | `/api/admin/participants/{id}` | Permanently delete a participant after confirmation |
| POST | `/api/admin/points` | Add points to a member |
| GET | `/api/admin/dashboard/members` | Get SC count over time |

### Scheduled Jobs
| Job | Schedule | Description |
|-----|----------|-------------|
| `SyncJob` | Daily at 12:00 | Updates participant profiles from Teamkatalogen |
| `CalculatePointsJob` | Daily at 13:00 | Fetches Slack activity and updates points/levels for all active members |
| `ResetPointsSyncJob` | Configurable via `jobs.reset-points.cron` | Resets all points and levels |

## Contributing
Contributions to the backend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.
4. If you have any questions or need help, feel free to reach out to the appsec team!

# Security champions stats backend application.

## Overview
The backend application is built using Kotlin and Spring Boot, and it serves as the API for the frontend application.
It provides endpoints for fetching security champions statistics, managing security champions and much more with time.
The backend application is responsible for handling business logic, data storage and retrival, and authentication and 
authorization for the frontend application.

### DataFlow ([mermaid] (https://github.blog/2022-02-14-include-diagrams-markdown-files-mermaid/) -syntax)
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend
    participant DB as Database
    participant TK as Teamkatalogen
    participant Slack as Slack
    participant BES as Backend scheduler (e.g. cronjob)

    FE->>BE: Request towards one of the endpoints (e.g. get security champions stats)
    BE->>DB: Query for data related to request (e.g. security champions stats)
    DB-->>BE: Return/change data (e.g. security champions stats)
    BE-->>FE: Return response (e.g. security champions stats)
    
    BES->>TK: Get all teams
    TK-->>BES: [team, team, …]
    BES-->>BES: Map to list of member with role SECURITY_CHAMPION: [team_member, team_member, …]
    BES->>DB: Fetch current list of team members with role SECURITY_CHAMPION
    DB-->>BES: [team_member, team_member, …]
    BES-->>BES: Calculate diff between current and previous list
    BES->>DB: Store current list of champions for next time [team_member, team_member, …]
    loop paginated
        BES->>Slack: Get all activity for team_member with role SECURITY_CHAMPION and have agreed to share it
        Slack-->>BES: [activity, activity, …]
        BES-->>BES: Map each activity to corresponding team_member and calculate points for each team_member based on activity
    end
    left of BES: Runs on a schedule (e.g. once a week)
    
```

## How to run
To run the backend application, follow these steps:
1. Make sure you have Java 17 or higher installed on your machine.
2. Build the application using Gradle: `./gradlew build`
3. Expose necessary environment variables for the application to run
4. Run the application: `./gradlew bootRun`
5. The application will start on `http://localhost:8080` and you can access the API endpoints.

## Technologies Used
- Kotlin: A modern programming language that runs on the JVM and is fully interoperable with Java
- Spring Boot: A framework for building production-ready applications with Java and Kotlin
- PostgreSQL: A powerful, open-source relational database management system
- Flyway: A database migration tool that helps manage and version control database schema changes
- JUnit: A testing framework for Java and Kotlin applications
- MockK: A mocking library for Kotlin
- Docker: A platform for developing, shipping, and running applications in containers

## Folder Structure
- `stats/`: Contains the main application code for the backend, including controllers, services, repositories, and models.
- `src/test/`: Contains unit and integration tests for the application.
- `common/`: Contains shared code and utilities that can be used across different modules of the application. Also contains configuration classes
- `gradle/libs.versions.toml`: Contains version numbers for all dependencies used in the application, making it easier to manage and update them.

## Contributing
Contributions to the backend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.
4. If you have any questions or need help, feel free to reach out to the appsec team!

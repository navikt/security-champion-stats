# Copilot Instructions for Security champions levels

**EXTREMELY IMPORTANT**:
- Break up changes into smaller tasks and verify functionality before moving on.
  If in doubt, ask for clarification. For example, if asked to implement functionality for fetching a set of data.
  Do not stray outside of this task. Ask if the user wants you to add documentation or extend the functionality
  of the initial request.
- Do NOT do add anything comprehensive unless specifically instructed.
- Do NOT add documentation unless specifically asked.
- Do NOT add comments in code unless the logic is VERY complex.
- The user will verify functionality manually and ask for changes if needed. No need to build or run the application for verification.
- **DO NOT ADD EXTRA DOCUMENTATION OR EXPLANATIONS UNLESS SPECIFICALLY ASKED.**
- Do not use timeout when running terminal commands, we are running zsh on macos.
- When adding or removing functionality we update the root README.md with relevant information. Keep the information here VERY concise and to the point. For example when adding or removing a package or refactoring existing folder structure.

## Project Overview:

Security Champion Stats is a application for tracking and displaying statistics related to security champions within an organization. 
The application should be designed to motivate and engage security champions by gamifying their experience and providing a platform for
users to track their progress and see other security champions. 

The application is also meant to be used by appsec team for administrating, like adding points or deleting members and so on.
Also should be used by appsec team to track activity of security champions and see how the security champion program is doing in general.

The project is divided into two main parts: the backend and the frontend. The backend is responsible for handling the 
business logic, data storage, and API endpoints, while the frontend is responsible for presenting the data in a simple way. 
The project must be able to run locally for development and testing, as well as in serverless
environment (gcp) for production use. The docker images will use distroless as base images.
For testing we will avoid mocking as much as possible and use test containers or similar solutions.

### Integrations
- **Entra ID** - For authentication and user management.
- **PostgreSQL** - For data storage.
- **Teamkatalogen** - For fetching SCs information and displaying it in the application.
- **Slack** - For fetching SCs activity and calculating points based on activity.

### Key Architectural Principles
- **Clean Architecture**: Dependencies point inward (infrastructure → usecase → domain)
- **Dependency Injection**: Use constructor injection for all dependencies, avoid service locators or global state.
- **Single Responsibility**: Each class or module has one clear purpose.
- **Testability**: Design for testability with clear separation of concerns and use of interfaces
- **Interface Segregation**: Avoid large, monolithic interfaces; prefer smaller, focused ones.

## Technology Stack:

### Backend Core:
- **Framework**: Spring Boot
- **Language**: Kotlin
- **Serialization** kotlinx.serialization
- **Testing**: JUnit 5, MockK, Testcontainers
- **Build Tool**: Gradle

#### Backend Ifrastructure:
- **Database**: PostgreSQL (with Flyway for migrations)
- **Authentication**: Azure AD (via Spring Security)
- **Containerization**: Docker (distroless base images)
- **Deployment**: GCP Cloud run with NAIS platform

### Frontend Core:
- **Framework**: React, Next.js
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **Build tool**: pnpm

## Coding Conventions:

### Code Style Guidelines:

- **Line Length**: 120 characters max
- **Imports**: Organize with wildcards for 5+ imports from same package, otherwise explicit imports.
- **Documentation**: Inline comments for complex logic only
- **Naming Conventions**:
  - Classes: PascalCase
  - Methods/Functions: camelCase
  - Variables: camelCase
  - Constants: UPPER_SNAKE_CASE
- **Nullability**: Explicit null handling, prefer safe calls (`?.`)

### File Organization:
#### Backend:
- **One public class per file** (private helpers allowed)
- **File name matches primary class name**
- **Package structure reflects architectural layers**
- **Test files mirror main source structure** 

#### Frontend:
- **One component per file** (unless closely related)
- **File name matches component name**
- **Organize by feature or domain**
- **Test files mirror main source structure**

### Error handling:
#### Backend:
The project adheres to Problem Details RFC9457 for error handling.
Example request: 
```
POST /purchase HTTP/1.1
Host: store.example.com
Content-Type: application/json
Accept: application/json, application/problem+json

{
"item": 123456,
"quantity": 2
}
```

Problem details response:
```
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json
Content-Language: en

{
 "type": "https://example.com/probs/out-of-credit",
 "title": "You do not have enough credit.",
 "detail": "Your current balance is 30, but that costs 50.",
 "instance": "/account/12345/msgs/abc",
 "balance": 30,
 "accounts": ["/account/12345",
              "/account/67890"]
}
```
```kotlin
// Exception handling with proper logging
try {
    // Business logic
} catch (e: SerializationException) {
    call.respond(HttpStatusCode.BadRequest, ErrorResponse(...))
} catch (e: IOException) {
    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(...))
}
```

#### Frontend:
The project adheres to Problem Details RFC0759 for error handling.
Example request:
```
try {
 // Business logic
} catch (error) {
  console.error('An error occurred:', error);
}

```

## Testing Conventions:

### Test Structure:

#### Backend:
- **Location**: `src/test/kotlin` mirrors `src/main/kotlin`
- **Naming**: `ClassNameTest` for unit tests, `ClassNameIntegrationTest` for integration tests
- **Test Method Naming**: Backtick syntax with descriptive names using spaces (e.g., `fun \`should generate valid PNG when given valid request\`()`)
- **Frameworks**: JUnit 5 for testing, MockK for mocking, Testcontainers for integration tests with real dependencies
- **Pattern**: Descriptive sentences that clearly explain the behavior being tested

### Test Categories:

#### Backend:
- **Unit Tests**: Test individual classes or methods in isolation, using mocks for dependencies.
- **Integration Tests**: Full application context (`testApplication`)
- **API Tests**: End-to-end endpoint testing

#### Frontend:
- **Unit Tests**: Test individual components or functions in isolation, using mocks for dependencies.
- **Integration Tests**: Test component interactions and state management.

### Test Patterns:

#### Backend:
```kotlin
@Test
fun `should generate valid PNG when given valid request`() = testApplication {
    application { module() }
    val response = client.post("/snap") {
        contentType(ContentType.Application.Json)
        setBody(validSnapRequest)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.contentType()?.match(ContentType.Image.PNG) == true)
}

@Test
fun `should reject request with invalid preset`() = testApplication {
    application { module() }
    val response = client.post("/snap") {
        contentType(ContentType.Application.Json)
        setBody(invalidPresetRequest)
    }
    assertEquals(HttpStatusCode.BadRequest, response.status)
}
```
#### Frontend:
```typescript
test('renders component with valid props', () => {
  render(<MyComponent title="Test Title" />);
  expect(screen.getByText('Test Title')).toBeInTheDocument();
});
```

### Test Naming Conventions

#### Backend:
- **Positive Tests**: `should [expected behavior] when [condition]` (e.g., `should generate valid image when given valid input`)
- **Negative Tests**: `should [error behavior] when [invalid condition]` (e.g., `should reject request when preset is invalid`)
- **Feature Tests**: `should [feature behavior] for [specific case]` (e.g., `should produce larger images for presentation preset`)
- **Validation Tests**: `should validate [rule] and [expected result]` (e.g., `should validate input and return error details`)

## API Design Patterns

### RESTful Conventions
- **Endpoints**: Descriptive nouns (`/snap` for image generation)
- **HTTP Methods**: POST for resource creation, GET for retrieval
- **Status Codes**: Proper HTTP semantics (200, 400, 500, etc.)

### Request/Response Structure
- **Consistent Naming**: camelCase for JSON fields
- **Optional Parameters**: Nullable with sensible defaults
- **Backward Compatibility**: Deprecated fields maintained with warnings
- **Extensibility**: Preset system for common configurations

## Configuration & Environment

### Application Configuration

#### Backend:
- **Build Config**: `build.gradle.kts` with version catalogs (`libs.versions.toml`)
- **Environment Variables**: For secrets and deployment-specific values
- **Stable dependencies**: We will stick to the latest stable release of all dependencies.

## Common Patterns & Best Practices

- We follow best practices from the kotlin foundation and Spring documentation.
- We follow best practices for React and Next.js development, including component design, state management, and performance optimization.

### Use Case Pattern
```kotlin
class GenerateCodeImageUseCase(
    private val highlighterService: CodeHighlighterService,
    private val rendererFactory: ImageRendererFactory
) {
    suspend fun execute(request: GenerateImageRequest): ByteArray {
        // Business logic here
    }
}
```

### Factory Pattern
```kotlin
class ImageRendererFactory {
    fun createRenderer(designSystem: String): ImageRenderer = when (designSystem) {
        "material" -> MaterialDesignImageRenderer()
        "macos" -> Java2DImageRenderer()
        else -> Java2DImageRenderer() // default
    }
}
```

### Leader Election Pattern
```kotlin
class LeaderElection(private val httpClient: HttpClient) {
    suspend fun isLeader(): Boolean {
        val electorUrl = System.getenv("ELECTOR_PATH") ?: return true // Local dev
        val response = httpClient.get(electorUrl)
        val leaderInfo: LeaderInfo = response.body()
        return hostname == leaderInfo.name
    }
    
    suspend fun <T> ifLeader(operation: suspend () -> T): T? {
        return if (isLeader()) operation() else null
    }
}
```

### Database Transaction Pattern
```kotlin
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

// Batch operations with chunking
suspend fun upsertCves(cves: List<NvdCveData>) {
    cves.chunked(500).forEach { batch ->
        dbQuery {
            batch.forEach { cve ->
                // Upsert logic
            }
        }
    }
}
```

## Performance Considerations

- **Caching**: Valkey cache for API responses (configurable TTL)
- **Database Indexes**: Proper indexes on frequently queried fields
- **Connection Pooling**: HikariCP for efficient database connection management
- **Batch Processing**: Chunked operations for large datasets (e.g., 500 CVEs per batch)
- **Rate Limiting**: Respect external API rate limits (NVD: 6 seconds between requests)

## Database Patterns

### Repository Pattern
```kotlin
interface NvdRepository {
    suspend fun getCveData(cveId: String): NvdCveData?
    suspend fun upsertCves(cves: List<NvdCveData>)
    suspend fun getLastModifiedDate(): LocalDateTime?
}
```

### Migration Management
- **Location**: `src/main/resources/db/migration/`
- **Naming**: `V{version}__{description}.sql` (e.g., `V1__create_nvd_tables.sql`)
- **Execution**: Flyway runs migrations automatically on application startup
- **Reversibility**: Avoid destructive changes; use new migrations to modify schema

### NVD Sync Strategy
- **Initial Sync**: Year-by-year from 2002 to present (~12-15 hours, leader-only)
- **Incremental Sync**: Every 2 hours using `lastModifiedDate` tracking (leader-only)
- **Leader Election**: Kubernetes native leader election prevents duplicate syncs
- **Date Format**: ISO 8601 with UTC timezone (`2024-01-01T00:00:00.000Z`)
- **Error Handling**: HTTP status checking before response deserialization

## Security Considerations

- **Input Validation**: All inputs validated before processing
- **Error Information**: No sensitive data in error responses
- **CORS Configuration**: Properly configured for web clients
- **Content Type Validation**: Strict content type checking

## Additional Guidelines

## Code Review Standards
- Check for logic errors, code style, and architectural consistency.
- Ensure all code (including Copilot-generated) is readable, maintainable, and tested.
- Review for security issues, proper error handling, and input validation.
- Require at least one approving review before merging PRs.

## Security Standards
- Follow OWASP Top 10 guidelines for web application security.
- Never commit secrets or sensitive data; use environment variables.
- Regularly update dependencies and review for vulnerabilities.
- Validate all user input and sanitize outputs.
- Use secure defaults for CORS, headers, and authentication.

## CI/CD Best Practices
- All code must pass linting, static analysis, and tests before merge.
- Use conventional commit messages for automated changelog generation.
- Automate releases and Docker builds via GitHub Actions.

## Key File References
- **README.md**: Project overview, setup, and API usage.
- **copilot-instructions.md**: Coding, commit, and workflow standards.
- **.github/workflows/**: CI/CD automation.
- **scripts/**: Automation scripts for testing.

## Documentation Update Workflow
- Update copilot-instructions.md and other docs for any new conventions or major changes.
- Review documentation changes in PRs; require approval before merging.
- Keep documentation concise, actionable, and up-to-date.
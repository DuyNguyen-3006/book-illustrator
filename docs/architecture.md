# Backend Architecture — book-illustrator

> **Amendment, 2026-08-14 — see §4 and §9, and `DECISIONS.md`.** The user made an
> explicit, deliberate call to merge the domain model and the JPA `@Entity` class into
> one — `domain/entity/{User,Project}.java` carries `@Entity`/`@Id`/`@Column` directly.
> §4 and §9 below (as originally written) say the opposite ("Never use a JPA entity as a
> domain model"). That original text is left in place for context; **§4/§9 are
> superseded where they conflict with this note.** `ArchitectureTest` was updated to
> match: domain may depend on `jakarta.persistence`, still may not depend on Spring
> itself or on `infrastructure`/`interfaces`.

## 1. Purpose

This document defines the backend architecture rules for the `book-illustrator`
project.
The backend uses:

* Java
* Spring Boot
* PostgreSQL
* JPA/Hibernate for persistence
* Gemini API for AI generation
* Clean Architecture
* Modular monolith architecture

The architecture must remain pragmatic and right-sized for this assessment.
Do not introduce abstractions, layers, patterns, or infrastructure that do not
provide a concrete architectural benefit.

## 2. Core Architecture

Use pragmatic Clean Architecture with the following dependency direction:

```
                    ┌─────────────────────┐
                    │     Interfaces      │
                    │   REST Controllers  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │     Application     │
                    │ Use Cases / Ports   │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │       Domain        │
                    │ Models / Rules      │
                    └─────────────────────┘

Infrastructure ───────────────► Application
Infrastructure ───────────────► Domain
```

The key rule is:
Dependencies point inward. Domain must remain independent from frameworks
and external systems.
Domain MUST NOT depend on:

* Spring
* Spring Boot
* Spring Data
* JPA
* Hibernate
* PostgreSQL
* Gemini SDK
* HTTP clients
* Jackson
* REST
* Infrastructure implementations

Application MUST NOT directly depend on:

* Spring Data repositories
* JPA repositories
* PostgreSQL
* Gemini client implementations
* HTTP client implementations
* filesystem implementations

Application accesses external systems through ports/interfaces that it owns.

## 3. Project Structure

Use the following backend structure:

```
backend/
└── src/
    ├── main/
    │   ├── java/com/bookillustrator/
    │   │
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── User.java
    │   │   │   ├── Project.java
    │   │   │   ├── Character.java
    │   │   │   └── Chapter.java
    │   │   │
    │   │   ├── enums/
    │   │   │   ├── ProjectStatus.java
    │   │   │   ├── PipelineStep.java
    │   │   │   └── StepState.java
    │   │   │
    │   │   └── exception/
    │   │       └── DomainException.java
    │   │
    │   ├── application/
    │   │   ├── usecase/
    │   │   │   ├── user/
    │   │   │   ├── project/
    │   │   │   └── pipeline/
    │   │   │
    │   │   ├── port/
    │   │   │   ├── input/
    │   │   │   └── output/
    │   │   │
    │   │   └── dto/
    │   │
    │   ├── infrastructure/
    │   │   ├── persistence/
    │   │   │   ├── entity/
    │   │   │   ├── repository/
    │   │   │   └── mapper/
    │   │   │
    │   │   ├── gemini/
    │   │   │   ├── GeminiClient.java
    │   │   │   ├── GeminiGatewayAdapter.java
    │   │   │   └── dto/
    │   │   │
    │   │   └── storage/
    │   │       └── LocalFileStorage.java
    │   │
    │   └── interfaces/
    │       └── rest/
    │           ├── controller/
    │           ├── request/
    │           ├── response/
    │           └── GlobalExceptionHandler.java
    │
    └── test/
        └── java/com/bookillustrator/
            ├── domain/
            ├── application/
            └── infrastructure/
```

Do not reorganize the project into microservices. This project is a modular
monolith.

## 4. Domain Layer

The domain layer contains business concepts and business rules.
For this project the main domain models are:

```
User
Project
Character
Chapter
```

Pipeline enums:

```java
public enum PipelineStep {
    STYLE,
    CHARACTERS,
    PORTRAITS,
    CHAPTERS,
    ILLUSTRATIONS
}
```

```java
public enum StepState {
    IDLE,
    RUNNING,
    FAILED,
    COMPLETED
}
```

The project should distinguish:

* `status` — overall project lifecycle
* `currentStep` — current pipeline step
* `stepState` — execution state of the current step

Example:

```
status       = IN_PROGRESS
currentStep  = PORTRAITS
stepState    = RUNNING
```

This means the project is in progress and the PORTRAITS step is currently
running.
Domain models must not be JPA entities.
Do NOT write:

```java
@Entity
public class Project { ... }
```

inside the domain package.
Instead:

```
domain/model/Project.java
```

and separately:

```
infrastructure/persistence/entity/ProjectEntity.java
```

## 5. Application Layer

The application layer contains use cases and application workflows.
Typical use cases:

```
user/
├── IdentifyUserUseCase

project/
├── CreateProjectUseCase
├── GetProjectsUseCase
└── GetProjectUseCase

pipeline/
├── RunStyleStepUseCase
├── RunCharactersStepUseCase
├── RunPortraitsStepUseCase
├── RunChaptersStepUseCase
└── RunIllustrationsStepUseCase
```

A use case should coordinate the workflow, but should not contain HTTP-specific
logic or database-specific implementation details.
Example:

```java
public interface RunCharactersStepUseCase {
    CharactersResult execute(UUID projectId);
}
```

The implementation:

1. Loads the project through a port.
2. Validates the current pipeline state.
3. Attempts to acquire execution ownership.
4. Marks the step as running.
5. Calls Gemini through `GeminiGateway`.
6. Persists the result.
7. Marks the step as completed.
8. Handles failure and retry state appropriately.

## 6. Input Ports

Input ports define what the application exposes to the outside world.
Example:

```java
public interface CreateProjectUseCase {
    ProjectResult execute(CreateProjectCommand command);
}
```

Controllers depend on input ports/use cases.
Controllers must NOT directly access repositories.

## 7. Output Ports

Application-defined output ports isolate the application from external systems.

Database

```java
public interface ProjectRepository {

    Optional<Project> findById(UUID id);

    List<Project> findByUserId(UUID userId);

    Project save(Project project);
}
```

Gemini

```java
public interface GeminiGateway {

    StyleResult generateStyle(
        String bookText,
        String customStyle
    );

    List<CharacterResult> generateCharacters(
        GeminiContext context
    );

    PortraitResult generatePortrait(
        CharacterPrompt prompt
    );

    List<ChapterResult> generateChapters(
        GeminiContext context
    );

    IllustrationResult generateIllustration(
        ChapterPrompt prompt,
        List<CharacterReference> characters
    );
}
```

The exact methods and DTOs must be adapted to the actual Gemini notebook/API
pipeline. Do not invent pipeline mechanics that are not supported by the
assessment notebook.

## 8. Infrastructure Layer

Infrastructure contains implementations of application ports.

Persistence

```
infrastructure/persistence/
├── entity/
├── repository/
└── mapper/
```

Example:

```java
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    private UUID userId;

    private String title;

    private String status;

    private String currentStep;

    private String stepState;

    private Instant stepStartedAt;

    @Version
    private Long version;
}
```

Spring Data repository:

```java
public interface JpaProjectRepository
        extends JpaRepository<ProjectEntity, UUID> {
}
```

Adapter:

```java
@Component
public class ProjectRepositoryAdapter
        implements ProjectRepository {

    private final JpaProjectRepository repository;

    // map Entity <-> Domain
}
```

The application must depend on `ProjectRepository`, not on
`JpaProjectRepository`.

## 9. Domain Model vs Persistence Entity

Never use a JPA entity as a domain model.
Use:

```
Domain:
domain/model/Project.java

Persistence:
infrastructure/persistence/entity/ProjectEntity.java
```

Mapping:

```
Project
   ↕
ProjectMapper
   ↕
ProjectEntity
```

This prevents JPA/Hibernate concerns from leaking into the domain.
Do not expose JPA entities from REST controllers.

## 10. REST Layer

REST controllers belong under:

```
interfaces/rest/
├── controller/
├── request/
├── response/
└── GlobalExceptionHandler.java
```

Controllers must be thin.
A controller may:

* Parse HTTP requests
* Validate request DTOs
* Convert request DTOs to application commands
* Invoke a use case
* Convert application results to response DTOs
* Return HTTP responses

A controller must NOT:

* Call repositories directly
* Call Gemini directly
* Contain pipeline business rules
* Implement retry logic
* Implement concurrency logic
* Implement database transactions for business workflows
* Contain Gemini prompt construction logic unless that prompt is explicitly
  part of the interface contract

Example:

```java
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request) {

        var result = createProjectUseCase.execute(
            request.toCommand()
        );

        return ResponseEntity.ok(
            ProjectResponse.from(result)
        );
    }
}
```

## 11. DTO Rules

Do not reuse one DTO for every layer.

REST:

```
interfaces/rest/request/
interfaces/rest/response/
```

Application:

```
application/dto/
```

Domain:

```
domain/model/
```

The expected flow is:

```
HTTP Request DTO
       ↓
Application Command
       ↓
Use Case
       ↓
Domain
       ↓
Application Result
       ↓
HTTP Response DTO
```

Never do:

```java
@PostMapping
public ProjectEntity create(ProjectEntity request)
```

## 12. Gemini Integration

Gemini must be hidden behind `GeminiGateway`.
The pipeline application code should depend on:

```
GeminiGateway
```

not:

```
GeminiClient
```

Infrastructure may contain:

```
GeminiClient
GeminiGatewayAdapter
Gemini request/response DTOs
Gemini-specific error handling
```

Expected dependency:

```
Pipeline Use Case
       ↓
GeminiGateway
       ↓
GeminiGatewayAdapter
       ↓
GeminiClient / REST API
       ↓
Gemini
```

Do not call Gemini from controllers.
Do not put Gemini SDK types in the domain.
Do not put API keys in source code.
Use environment variables/configuration.

## 13. Pipeline Architecture

The pipeline consists of five ordered steps:

```
STYLE
  ↓
CHARACTERS
  ↓
PORTRAITS
  ↓
CHAPTERS
  ↓
ILLUSTRATIONS
```

A step cannot be executed before its required previous step has completed.
Each step should have explicit state:

```
IDLE
RUNNING
FAILED
COMPLETED
```

The application must support:

* Refresh
* Second browser tab
* Double click
* Gemini failure
* Retry
* Resume after interruption
* Preventing duplicate Gemini calls

The exact pipeline input/output/context chaining must follow the assessment
notebook. Do not invent different mechanics just to fit the architecture.

## 14. Concurrency / Duplicate Execution

The application must prevent two requests from executing the same pipeline
step concurrently.
Preferred mechanism:

```sql
UPDATE projects
SET step_state = 'RUNNING',
    step_started_at = NOW()
WHERE id = ?
  AND step_state = 'IDLE';
```

The request owns the execution only when exactly one row is updated.
Conceptually:

```
Request A
    ↓
conditional UPDATE
    ↓
1 row updated
    ↓
owns execution
    ↓
Gemini call


Request B
    ↓
conditional UPDATE
    ↓
0 rows updated
    ↓
step already running
    ↓
DO NOT call Gemini
```

Do not hold a `SELECT ... FOR UPDATE` database transaction open for the entire
Gemini API request.
The database lock/ownership operation should be short-lived.
If a running step needs stale-lock recovery, use an explicit timestamp/TTL
strategy and a conditional update. The TTL must be chosen based on the actual
expected Gemini execution time and documented in `DECISIONS.md`.

## 15. PostgreSQL

PostgreSQL is the persistent data store.
Expected core tables:

```
users
projects
characters
chapters
```

The `projects` table should contain the fields required to represent pipeline
state, such as:

```
status
current_step
step_state
step_started_at
version
```

Use database constraints for invariants where appropriate.
Examples:

* Foreign keys
* Unique email
* Required fields
* Appropriate indexes
* Optimistic locking/version where needed

Database schema must be managed through migrations.
Preferred migration structure:

```
src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_projects.sql
├── V3__create_characters.sql
└── V4__create_chapters.sql
```

Use Flyway unless the project has an explicit reason to use another migration
tool.

## 16. Transactions

Transactions belong at the application/use-case boundary where a business
operation needs atomic persistence.
Do not keep a database transaction open while waiting for Gemini.

Bad:

```
BEGIN TRANSACTION
    acquire DB lock
    call Gemini
    wait 10-30+ seconds
    save result
COMMIT
```

Prefer:

```
Short transaction:
    acquire execution ownership
    COMMIT

Gemini API call

Short transaction:
    persist result
    mark step completed
    COMMIT
```

The exact transaction boundaries must preserve the application's consistency
rules.

## 17. Exception Handling

Application/domain exceptions should be converted to HTTP responses by:

```
interfaces/rest/GlobalExceptionHandler.java
```

Examples:

```
ProjectNotFoundException
InvalidPipelineStateException
StepAlreadyRunningException
GeminiGenerationException
```

Do not expose raw database exceptions or Gemini SDK exceptions directly to
clients.
Responses should use a consistent error format (the `ApiResponse` envelope —
see `.claude/skills/backend-rules/SKILL.md` §1).

## 18. Testing Rules

Tests should be organized around architecture boundaries.

```
src/test/java/com/bookillustrator/
├── domain/
├── application/
└── infrastructure/
```

Domain tests
Test business rules without Spring.
Examples:

```
cannot skip pipeline step
cannot complete an idle step
valid state transition
invalid state transition
```

Application tests
Mock/fake output ports:

```
ProjectRepository
GeminiGateway
CharacterRepository
ChapterRepository
```

Test:

```
successful step
failed Gemini call
retry
resume
invalid step order
duplicate execution
```

Integration tests
Test:

```
Spring Boot
+
PostgreSQL
+
JPA
```

Concurrency behaviour must be tested against a real PostgreSQL environment,
not only mocked repositories.

## 19. Avoid Over-Engineering

Do NOT automatically create all of these for every feature:

```
Entity
Aggregate
DomainEntity
DTO
Command
Query
Mapper
Assembler
Factory
DomainService
ApplicationService
Repository
RepositoryImpl
RepositoryAdapter
```

Only introduce a class/interface when it provides a real architectural or
business benefit.
For a simple CRUD operation, a small use case and repository port may be
enough.
For pipeline execution, stronger separation is justified because the pipeline
contains:

* State transitions
* External AI calls
* Concurrency
* Retry
* Resume
* Persistence

The architecture should be more structured around those areas and simpler
elsewhere.

## 20. Naming Rules

Use clear, intention-revealing names.

Good:

```
RunCharactersStepUseCase
ProjectRepository
GeminiGateway
ProjectRepositoryAdapter
GeminiGatewayAdapter
InvalidPipelineStateException
```

Avoid generic names such as:

```
Helper
Utils
Manager
CommonService
BaseService
GenericRepository
```

unless there is a specific reason.
Use singular domain names:

```
Project
Character
Chapter
User
```

Use action-oriented use case names:

```
CreateProject
GetProject
RunStyleStep
RunCharactersStep
```

## 21. Configuration and Secrets

Never hard-code:

```
Gemini API keys
Database passwords
Secrets
Tokens
```

Use environment variables/configuration.
Example:

```
GEMINI_API_KEY=
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
```

Commit only:

```
.env.example
```

Never commit:

```
.env
real API keys
real credentials
```

## 22. Git / Documentation Expectations

Important architectural decisions must be recorded in:

```
DECISIONS.md
```

Decisions should explain:

* What was chosen
* What alternatives were considered
* Where AI suggested something different
* Where I pushed back on AI
* Where AI caught my mistake
* The trade-off/cost of the decision

Do not turn `DECISIONS.md` into a worklog.
Git history records implementation history.

## 23. Implementation Order

When implementing the backend, follow this order:

```
1. Create Spring Boot project
        ↓
2. Configure PostgreSQL
        ↓
3. Create database migrations
        ↓
4. Implement domain models/enums
        ↓
5. Implement application ports
        ↓
6. Implement project/user use cases
        ↓
7. Implement persistence adapters
        ↓
8. Implement Gemini gateway
        ↓
9. Implement pipeline use cases
        ↓
10. Implement concurrency protection
        ↓
11. Implement retry/resume
        ↓
12. Implement REST controllers
        ↓
13. Add unit tests
        ↓
14. Add PostgreSQL integration/concurrency tests
        ↓
15. Verify complete pipeline
```

Do not start by generating all layers and all classes at once.
Implement one vertical slice at a time and keep the code compiling and tested.

## 24. Claude Behaviour Rules

When working on this project:

1. Follow this architecture unless the user explicitly changes it.
2. Do not introduce microservices.
3. Do not move business logic into controllers.
4. Do not couple domain models to Spring/JPA.
5. Do not expose JPA entities through REST.
6. Do not call Gemini directly from controllers.
7. Do not call repositories directly from controllers.
8. Do not create unnecessary abstractions.
9. Before adding a new layer/pattern, explain the concrete problem it solves.
10. Preserve the dependency direction.
11. Keep pipeline state transitions explicit and testable.
12. Protect Gemini calls from duplicate execution.
13. Do not hold PostgreSQL transactions open during Gemini API calls.
14. Follow the actual Gemini notebook/reference pipeline for pipeline mechanics.
15. Do not invent API behaviour when the assessment source does not specify it.
16. Keep secrets outside source code.
17. Write tests for important state and concurrency rules.
18. Prefer small, understandable code over architecture for architecture's sake.

## 25. Target Architecture Summary

The final dependency flow should look like:

```
                    FRONTEND
                       │
                       │ HTTP
                       ▼
              ┌─────────────────┐
              │   REST Layer    │
              │ Controllers     │
              │ Request/Response│
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  APPLICATION    │
              │                 │
              │ Use Cases       │
              │ Commands/DTOs   │
              │ Input Ports     │
              │ Output Ports    │
              └───────┬─────────┘
                      │
                      ▼
              ┌─────────────────┐
              │     DOMAIN      │
              │                 │
              │ Project         │
              │ User            │
              │ Character       │
              │ Chapter         │
              │ Pipeline Rules  │
              └─────────────────┘
                      ▲
                      │
              ┌───────┴─────────┐
              │  INFRASTRUCTURE │
              │                 │
              │ PostgreSQL/JPA  │
              │ Gemini          │
              │ File Storage    │
              └─────────────────┘
```

The most important architectural rule is:

```
External systems depend on the application/domain abstractions,
not the other way around.
```

The goal is not to maximize the number of classes or layers. The goal is to
keep the pipeline's business rules, state management, concurrency, and AI
integration understandable, testable, and independent from framework details.

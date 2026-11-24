# Candidate Management API
#### This repository extends the candidate API into an event-driven system with Kafka. See [ROADMAP](/ROADMAP.md)

A Spring Boot REST application for managing candidate records. It provides a
REST API under `/api/v1/candidates` to create, view, update, soft-delete and
search candidates. Search supports free text, filters, sorting and pagination.
It also includes optimistic locking, RFC 7807 error responses, a Flyway-managed
H2 schema, OpenAPI/Swagger docs, request-ID logging, an optional demo data
profile, and a small single-page web UI built with plain HTML, CSS and
JavaScript.

## Requirements

- **JDK 21.** `JAVA_HOME` must point to a JDK 21 installation, because the
  Maven Wrapper uses it.
- **Maven Wrapper.** It is included (`mvnw`, `mvnw.cmd`), so you don't need to
  install Maven separately.

Set `JAVA_HOME` for the current shell and check which Java version Maven uses.
Replace the example paths below with the directory of your JDK 21 installation.

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
cd candidate-service
.\mvnw.cmd -v
```

Linux/macOS (bash/zsh). Replace the example path with your JDK 21 directory:

```shell
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
cd candidate-service
.\mvnw.cmd -v
```

`cd candidate-service
.\mvnw.cmd -v` must report `Java version: 21...`. A `JAVA_HOME` change only applies
to shells (and IDEs) started after the change. Already open terminals keep the
old value.

## How to run

Use `cd candidate-service 
.\mvnw.cmd` on Windows PowerShell and `cd candidate-service
./mvnw` on Linux/macOS. Stop the
application with `Ctrl+C`.

### Default profile (file-based H2)

```powershell
cd candidate-service
.\mvnw.cmd spring-boot:run
```

```shell
./mvnw spring-boot:run
```

- The app starts on port 8080.
- Data is stored in an H2 file database at `./data/candidatesdb`, so it
  survives restarts.
- Flyway creates and migrates the schema on startup.
- The H2 console is disabled.

### Persistence

Data is stored in `./data` and survives application restarts. The default and
demo profiles use the same database; deleting this directory removes the data.

### Demo profile (sample data and H2 console)

```powershell
cd candidate-service
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=demo"
```

```shell
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=demo
```

The `demo` profile:

- Enables the H2 console.
- Loads 30 clearly fictional candidates through `CandidateService`. They have
  `example.com` emails and the surname `Demo`.

Loading only happens when the candidate table is empty, including soft-deleted
rows. If any rows already exist, it logs
`Skipping demo data: candidate rows already exist` and changes nothing. The
demo profile uses the same `./data/candidatesdb` file as the default profile.

To start again from an empty database, stop the application and delete the
`data` directory. Flyway recreates the schema on the next start.

### PostgreSQL profile

This project has no `postgres` profile and no `docker-compose` file. It runs on
H2 only (see [Known limitations](#known-limitations)).

## URLs

With the application running locally on the default port:

| URL | What it is |
|---|---|
| http://localhost:8080/ | Candidate web UI |
| http://localhost:8080/swagger-ui.html | Swagger UI (redirects to `/swagger-ui/index.html`) |
| http://localhost:8080/v3/api-docs | OpenAPI document (JSON) |
| http://localhost:8080/actuator/health | Health check (`{"groups":["liveness","readiness"],"status":"UP"}`) |
| http://localhost:8080/actuator/info | Info endpoint (currently returns `{}`) |
| http://localhost:8080/h2-console | H2 console, **demo profile only** (404 otherwise) |

H2 console login: JDBC URL `jdbc:h2:file:./data/candidatesdb`, user name `sa`,
empty password.

## API overview

Base path: `/api/v1/candidates`. Request and response bodies are JSON. Every
response carries an `X-Request-Id` header. Only valid UUID request IDs are echoed;
otherwise a UUID is generated. Access logs use matched route templates (or
`unmatched`), never raw URIs or query strings.

| Method | Path | Success | Description |
|---|---|---|---|
| `POST` | `/api/v1/candidates` | `201 Created` with `Location` header and body | Create a candidate |
| `GET` | `/api/v1/candidates` | `200 OK` with a page of results | Search, filter, sort and page active candidates |
| `GET` | `/api/v1/candidates/{id}` | `200 OK` | Get one active candidate (404 if missing or deleted) |
| `PUT` | `/api/v1/candidates/{id}` | `200 OK` | Full update; requires the current `version` |
| `DELETE` | `/api/v1/candidates/{id}` | `204 No Content` | Soft delete |

### Request fields (POST and PUT)

| Field | Rules |
|---|---|
| `firstName`, `lastName` | Required, max 100 characters |
| `email` | Required, valid email, max 254 characters; stored trimmed and lowercased; unique |
| `phone` | Max 30 characters |
| `currentTitle`, `currentCompany`, `location` | Max 150 characters each |
| `experienceYears` | Decimal ≥ 0, up to 3 integer digits and 1 decimal place |
| `noticePeriodDays` | Integer ≥ 0 |
| `status` | `NEW`, `SCREENING`, `INTERVIEWING`, `OFFERED`, `HIRED` or `REJECTED`. Defaults to `NEW` on create; if null on update, the current status is kept |
| `notes` | Max 4000 characters |
| `skills` | Up to 50 names, each 1–50 characters; stored trimmed, lowercased, deduplicated. Null or empty on update clears skills |
| `version` | Required on `PUT` (the value from the last read); ignored on `POST` |

PUT replaces the whole record. Omitted optional fields are cleared. It is not a
PATCH.

### Search query parameters (`GET /api/v1/candidates`)

All parameters are optional. Filters are combined with AND. Soft-deleted
candidates are never returned.

| Parameter | Behaviour |
|---|---|
| `q` | Free text: max 100 characters and 5 whitespace-separated words. Each word must match (case-insensitive, substring) first name, last name, email, current title or current company. `%`, `_` and `!` are matched literally. |
| `status` | One or more statuses, repeated (`status=NEW&status=SCREENING`) or comma-separated (`status=NEW,SCREENING`); matches any |
| `location` | Max 150 characters; case-insensitive exact match |
| `skills` | Comma-separated, max 20 entries of max 100 characters; normalized to lowercase |
| `skillMatch` | `ALL` (default) or `ANY` |
| `minExperience`, `maxExperience` | Inclusive, ≥ 0; min must not exceed max |
| `maxNoticePeriod` | Inclusive, ≥ 0 days |
| `page` | Zero-based, default `0`; `page * size` must not exceed `100000`, otherwise narrow the search |
| `size` | 1–100, default `20` |
| `sort` | `field,asc` or `field,desc`. Fields: `createdAt`, `updatedAt`, `firstName`, `lastName`, `experienceYears`, `status`. Default `createdAt,desc`. Nulls sort last, and `id` ascending is the tie-breaker. |

The response shape is:

```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 2, "totalPages": 1 }
```

### curl examples

These examples use bash syntax (Linux/macOS or Git Bash on Windows). In
Windows PowerShell, call `curl.exe`, because `curl` is an alias for
`Invoke-WebRequest` there. Inline JSON quoting also differs in PowerShell, so
Git Bash is easier for the POST and PUT examples. The IDs, versions and
timestamps below come from one real run and will differ for you.

**Create**

```shell
curl -i -X POST http://localhost:8080/api/v1/candidates \
  -H "Content-Type: application/json" \
  -d '{
        "firstName": "Asha",
        "lastName": "Readme",
        "email": "asha.readme@example.com",
        "currentTitle": "Backend Developer",
        "currentCompany": "Example Corp",
        "location": "Pune",
        "experienceYears": 4.5,
        "noticePeriodDays": 30,
        "skills": ["Java", "Spring", "SQL"]
      }'
```

```text
HTTP/1.1 201
X-Request-Id: 42635e43-639f-4f83-8b5d-b296d1d20d06
Location: http://localhost:8080/api/v1/candidates/33
Content-Type: application/json

{"id":33,"firstName":"Asha","lastName":"Readme","email":"asha.readme@example.com","phone":null,"currentTitle":"Backend Developer","currentCompany":"Example Corp","location":"Pune","experienceYears":4.5,"noticePeriodDays":30,"status":"NEW","notes":null,"skills":["java","spring","sql"],"createdAt":"2026-11-17T12:56:28.036560Z","updatedAt":"2026-11-17T12:56:28.036560Z","version":0}
```

**Get by id**

```shell
curl -i http://localhost:8080/api/v1/candidates/33
```

**Update (send the current `version`)**

```shell
curl -i -X PUT http://localhost:8080/api/v1/candidates/33 \
  -H "Content-Type: application/json" \
  -d '{
        "firstName": "Asha",
        "lastName": "Readme",
        "email": "asha.readme@example.com",
        "currentTitle": "Senior Backend Developer",
        "currentCompany": "Example Corp",
        "location": "Pune",
        "experienceYears": 5,
        "noticePeriodDays": 30,
        "status": "SCREENING",
        "skills": ["java", "spring", "sql", "docker"],
        "version": 0
      }'
```

The response is `200 OK` with `"status":"SCREENING"` and `"version":1`.
Sending `"version": 0` again now returns `409 Conflict`; see the error example
below.

**Search and filter with pagination**

```shell
curl "http://localhost:8080/api/v1/candidates?q=developer&status=NEW,SCREENING&location=pune&skills=java,spring&skillMatch=ALL&minExperience=2&maxExperience=10&maxNoticePeriod=60&page=0&size=10&sort=experienceYears,desc"
```

**Delete (soft delete)**

```shell
curl -i -X DELETE http://localhost:8080/api/v1/candidates/33
```

This returns `204 No Content`. A later `GET /api/v1/candidates/33` returns 404.

## Error format

Errors are RFC 7807 `ProblemDetail` responses with content type
`application/problem+json`.

| Status | Title |
|---|---|
| 400 | `Validation failed` (with an `errors` list) |
| 400 | `Malformed request` (malformed JSON or an invalid enum value in the body) |
| 404 | `Candidate not found` |
| 409 | `Duplicate email` |
| 409 | `Concurrent modification` |
| 409 | `Data conflict` |
| 500 | `Internal server error` (generic message; no internal details) |

**Validation error (400).** This response came from posting
`{"firstName":"","lastName":"Readme","email":"not-an-email","experienceYears":-1}`:

```json
{
  "detail": "Request validation failed",
  "instance": "/api/v1/candidates",
  "status": 400,
  "title": "Validation failed",
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "experienceYears", "message": "must be greater than or equal to 0" },
    { "field": "firstName", "message": "must not be blank" }
  ]
}
```

Invalid search parameters use the same format. An inverted experience range
is reported on the field `experienceRangeValid`.

**Concurrent modification (409).** This came from a PUT with a stale `version`:

```json
{
  "detail": "The candidate was modified by someone else. Reload it and try again.",
  "instance": "/api/v1/candidates/33",
  "status": 409,
  "title": "Concurrent modification"
}
```

A duplicate email returns `409` with title `Duplicate email` and detail
`A candidate with this email already exists`.

## Tests

Run the full build and test suite:

```powershell
cd candidate-service
.\mvnw.cmd clean verify
```

```shell
./mvnw clean verify
```

The suite contains 162 JUnit tests:

| Layer | Test classes | What they cover |
|---|---|---|
| Persistence | `CandidateRepositoryTest`, `CandidateSpecificationsTest` (`@DataJpaTest`) | Run against the Flyway-created schema with Hibernate `validate`. Cover save/read with skills, unique email, soft-delete visibility, auditing, optimistic locking, every search filter, skill ALL/ANY, literal wildcards, sorting and paging. |
| Service | `CandidateServiceTest` (Mockito) | Business rules: duplicate email (including deleted rows), version checks, skill resolution, soft delete. |
| Web | `CandidateControllerTest` (`@WebMvcTest` + `@MockitoBean`) | Status codes, `Location` header, query binding and validation, ProblemDetail errors, no leaking of internal details. |
| Integration | `CandidateApiIntegrationTest`, `OpenApiIntegrationTest`, `DemoDataLoaderTest`, `StaticUiTest`, `CandidatesApplicationTests` (`@SpringBootTest`) | Full CRUD and search flow, the OpenAPI document and Swagger UI, demo loader idempotency, `GET /` serving the UI, and context startup. |
| Unit | `CandidateMapperTest`, `SkillTest`, `RequestIdFilterTest` | Mapping and normalization, skill equality, request-ID validation and MDC cleanup. |

Each `@SpringBootTest` class uses its own in-memory H2 database; tests do not
open the application's `./data` database.

There is also an optional browser test for the UI. It is not part of the
Maven build and needs Node.js and Chrome or Edge (set `CHROME_PATH` if the
browser is not in a standard location):

```powershell
cd candidate-service
node --test src\test\browser\candidate-ui.test.js
```

## Project structure

```text
candidate-service/
src/main/java/com/suraj/candidates
├── CandidatesApplication.java
├── candidate/        Entity, enum, repository, specifications, service,
│   │                 controller, mapper, exceptions
│   └── dto/          CandidateRequest, CandidateResponse,
│                     CandidateSearchCriteria, PageResponse
├── skill/            Skill entity and repository
├── config/           JPA auditing, OpenAPI, request-ID filter, demo data loader
├── error/            GlobalExceptionHandler (ProblemDetail)
└── ui/               HomeController (serves the UI at /)
src/main/resources
├── application.yml, application-demo.yml
├── db/migration/V1__init.sql
└── static/           index.html, app.js, styles.css
src/test/java/...     JUnit tests (see above)
src/test/browser/     Optional Node.js browser test
docs/IMPLEMENTATION_NOTES.md   Detailed design and behaviour notes
```

## Known limitations

- There is no authentication or authorization; anyone who can reach the app
  can change data.
- There is no restore endpoint for soft-deleted candidates. A deleted
  candidate's email stays reserved, so it cannot be reused.
- There are no separate skill endpoints. Skills are managed only through the
  candidate `skills` list.
- Only H2 is configured, with no PostgreSQL profile or `docker-compose`. The
  migration avoids vendor-specific SQL and was written with PostgreSQL in mind,
  but it has only been run on H2. MySQL will need a vendor-specific migration.
- The `status` column has no database CHECK constraint, because of an H2 bug
  (see the comment in `V1__init.sql`). Valid values are enforced by the Java
  enum.
- PUT is a full replacement; there is no PATCH.
- The H2 console uses user `sa` with no password, and Swagger UI is always
  enabled. Neither is suitable for production.
- The UI browser test is not run by `mvnw verify`.

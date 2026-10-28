# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Backend for PLAD (Plataforma de Ajuda a Dependência): a Spring Boot 4.1 / Java 25 REST API where users register as either a dependent (`dependente`) or a psychologist (`psicologo`), log in with JWT, and dependents fill out a one-time triage questionnaire. Domain vocabulary, enum values and seed data are in Portuguese.

## Commands

Use the Maven wrapper (`./mvnw` in bash, `.\mvnw.cmd` in PowerShell):

- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run` (the API docs stay closed; add `-Dspring-boot.run.profiles=dev` to open Swagger UI at `/swagger-ui.html`)
- Update the API contract snapshot: `./mvnw test -Dopenapi.update=true` (in PowerShell quote it: `"-Dopenapi.update=true"`, or the dot splits the argument)
- All tests: `./mvnw test`
- Single test class / method: `./mvnw test -Dtest=TestDataRunner` / `./mvnw test -Dtest=TestDataRunner#savesAndFindsUserByEmail`

The app needs a running PostgreSQL at `localhost:5432/dbtest` (user/password `postgres`/`postgres`, see `src/main/resources/application.properties`). **Tests only need Docker running**: they start their own throwaway `postgres:16` via Testcontainers and never touch `pladDB` or `dbtest`. There is no in-memory test DB: `@DataJpaTest` tests use `@AutoConfigureTestDatabase(replace = NONE)`, so they hit a real Postgres with Flyway migrations applied.

Every test that loads a Spring context (`@SpringBootTest`, `@DataJpaTest`) must add `@Import(TestcontainersConfig.class)` (`src/test/java/plad/config`). It declares a single `@ServiceConnection` Postgres container shared by all contexts in the JVM, and its connection replaces `spring.datasource.*`. Unit tests with Mockito don't need it.

Running the app also needs a `JWT_SECRET` env var (at least 32 bytes, e.g. from `openssl rand -base64 48`). There is no default on purpose, so the app refuses to start without it. Tests don't need it: the surefire config in `pom.xml` sets a throwaway `jwt.secret`.

## Architecture

Package root is `plad` (layers: `controllers` → `services` → `repositories` → `entity`, plus `dto`, `exception`, `config`).

- **Schema is owned by Flyway**, not Hibernate: `spring.jpa.hibernate.ddl-auto=validate`. Any entity change requires a new migration `src/main/resources/db/migration/V{n}__description.sql`; never edit an already-applied migration. Triage questions are seeded via migration (V5), with `options` stored as JSONB `[{code, label}]` and `question_type` in `single_choice | multi_choice | free_text`.
- **Postgres native enums** (`user_type`, `sex`, `crm_crp_type`) map to Java enums via `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Java enum constant names must match the DB enum labels exactly (e.g. `UserType.dependente`, `Sex.nao_binario`).
- **User subtypes**: `users` is the base table; `dependents` and `psychologists` share its PK via `@OneToOne @MapsId` on `user_id`. `AuthService.register` creates the `User` plus the matching subtype row in one transaction. A `Triage` is one-to-one with a `Dependent` (`dependent_id` unique); answers live in `result_questions_triages`, which allows multiple rows per question (unique on `triage_id, question_id, value_result`) to support multi-choice.
- **Auth**: stateless JWT (jjwt). `SecurityConfig` permits `POST /auth/login`, `POST /auth/register` and `/error` by name (plus `/v3/api-docs/**` and `/swagger-ui/**`, only while `springdoc.api-docs.enabled=true`) and requires auth for everything else; a missing or invalid token gets a 401 `ErrorResponseDTO` from `SecurityErrorHandler`. `JwtAuthenticationFilter` reads `Authorization: Bearer <token>` and sets the principal to the `userId` (Long) claim — controllers get the current user's ID from the principal, not an entity. Invalid tokens are ignored (request proceeds unauthenticated). `config/RateLimitInterceptor` rate-limits `/auth/login` and `/auth/register` per endpoint and client IP via Bucket4j (5/min, in-memory) in `RateLimiterService`; the IP is `getRemoteAddr()` as resolved by Tomcat (`server.forward-headers-strategy=native`), which honors `X-Forwarded-For` only from peers in `TRUSTED_PROXIES` (CIDR list, default private ranges). `LoginService` adds a second bucket per normalized email (10 failures per 15 min, from any IP): it takes a slot before the password check, so an exhausted bucket answers 429 without revealing whether the password was right, and gives it back on a correct login, so only failures count. `RateLimiterService` picks the limit by key prefix (default 5/min; `login-email:` 10/15 min) and evicts a bucket only after a full refill period of its own. Emails are stored lowercased.
- **Errors**: every error body is `ErrorResponseDTO {code, message, timestamp, errors}`, 401/403 included. Services throw custom exceptions from `plad.exception`; each extends `ApiException` and fixes its `ErrorCode` in `super(...)`, and `config/GlobalExceptionHandler` maps each to an HTTP status and reads the code from the exception. New exceptions need an `ErrorCode`, a handler there and a row in the table below. `errors` is `[{field, message}]` (field in snake_case, e.g. `answers[0].values`) for `VALIDATION_ERROR` and an empty list otherwise; `message` keeps the flat `field: reason; ...` text. 401/403 are raised inside the security filter chain, before MVC, so `config/SecurityErrorHandler` (the `AuthenticationEntryPoint` and `AccessDeniedHandler` set in `SecurityConfig`) writes the same JSON. Errors that Spring maps itself (malformed JSON, unknown route, 405, 415...) use the `HttpStatus` name as the code (`BAD_REQUEST`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`...). The CORS filter's 403 for a disallowed origin is Spring's own plain-text answer.

  | Code | HTTP | Origin |
  |---|---|---|
  | `VALIDATION_ERROR` | 400 | Bean Validation on `@Valid @RequestBody` (fills `errors`) |
  | `INVALID_REGISTRATION` | 400 | `InvalidRegistrationException` |
  | `INVALID_TRIAGE` | 400 | `InvalidTriageException` |
  | `UNAUTHORIZED` | 401 | missing or invalid token (`SecurityErrorHandler`) |
  | `INVALID_CREDENTIALS` | 401 | `InvalidCredentialsException` (wrong e-mail or password) |
  | `FORBIDDEN` | 403 | `ForbiddenActionException`, and `SecurityErrorHandler` access denied |
  | `TRIAGE_NOT_FOUND` | 404 | `TriageNotFoundException` |
  | `EMAIL_IN_USE` | 409 | `EmailAlreadyInUseException` |
  | `TRIAGE_ALREADY_EXISTS` | 409 | `TriageAlreadyExistException` |
  | `DATA_CONFLICT` | 409 | unhandled `DataIntegrityViolationException` |
  | `RATE_LIMITED` | 429 | `TooManyRequestException` (with `Retry-After`) |
  | `INTERNAL_ERROR` | 500 | any other exception |
  | `BAD_REQUEST`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, ... | Spring's status | exceptions mapped by `ResponseEntityExceptionHandler` |
- **Timestamps**: every date/time column is `TIMESTAMPTZ` (V10 converted the old `TIMESTAMP` ones, reading them as `America/Sao_Paulo`) and maps to `Instant`. `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` keeps Hibernate independent of the JVM zone, and surefire runs the test JVM with `-Duser.timezone=America/Sao_Paulo` so zone bugs show up (`InstantRoundTripTest` fails if the JVM isn't in that zone). `@PrePersist` overwrites `createdAt` with `Instant.now()`, so tests can't set it.
- **API contract**: springdoc-openapi generates it and `docs/openapi.json` is the versioned snapshot that `config/OpenApiContractTest` compares with `/v3/api-docs` (normalized: sorted keys, 2-space indent, LF). Docs are closed by default (`springdoc.api-docs.enabled=false`); the `dev` profile (`application-dev.properties`) and the surefire config turn them on, and `OpenApiDisabledTest` checks they answer 401 when off. springdoc builds schemas with its own Jackson 2 mapper and ignores `spring.jackson.*`, so `OpenApiConfig` registers a `ModelResolver` with SNAKE_CASE; the test also checks real serialized keys against the schema. Document new endpoints with `@Operation`/`@ApiResponses` (errors, 401 included, use `ErrorResponseDTO`) and annotate `List` DTO fields with `@ArraySchema(arraySchema = @Schema(...))`, since a plain `@Schema` on a `List` leaks onto its items. An `OpenApiCustomizer` in `OpenApiConfig` marks every property of the response schemas (those reachable from an operation's responses) as `required`, because the API always sends every key; a response field that can be null needs `@Schema(nullable = true)`, which keeps it required but typed nullable. Every new DTO also needs a sample in `requestSamples()`/`responseSamples()` of `OpenApiContractTest`, which fails without one.
- **JSON uses snake_case** globally (`spring.jackson.property-naming-strategy=SNAKE_CASE`), so camelCase record DTO fields serialize as snake_case.

## Conventions

- Standard Java naming: PascalCase types (`UserRepository`, `UserType`, `Sex`), camelCase fields and methods (`realName`, `getRealName`), lowercase package names (`dto`). Column names stay snake_case via `@Column(name = "...")`. Only enum *constants* stay lowercase/snake_case (`dependente`, `nao_binario`, `single_choice`), since they must match the DB enum labels and the JSON values.
- DTOs are Java records with Jakarta Validation annotations; controllers use `@Valid @RequestBody`.
- Constructor injection (no `@Autowired` on fields) in main code.


## Rules

- Never edit an already-applied migration; always create a new `V{n}__...sql`.
- Never change `ddl-auto` or disable Flyway to make something work.
- Ask before adding Maven dependencies.
- Never hardcode secrets; use env vars
- New date/time columns are always `TIMESTAMPTZ`, never `TIMESTAMP`.
- After any change, run `./mvnw test` and fix failures before saying it's done.
- Every new custom exception extends `ApiException` with its own `ErrorCode`, needs a handler in `GlobalExceptionHandler` and a row in the error code table.
- Every API contract change (endpoint, DTO field, status code, validation constraint) updates `docs/openapi.json` in the same PR: run `./mvnw test -Dopenapi.update=true`, review the diff and commit it. `OpenApiContractTest` fails until you do.

## Database

PostgreSQL 16 runs in a Docker container defined in `docker-compose.yml` at the
repo root (container name: `pladDB`, named volume `pladdb_data`). Start it with
`docker compose up -d`. Only the app needs it, at `localhost:5432/dbtest`
(dev-only credentials `postgres`/`postgres`); tests use their own Testcontainers
Postgres and work with `pladDB` stopped.

If the app fails with a connection error (e.g. "Connection refused"), the
container is probably stopped. If tests fail because Docker isn't running or
reachable (Testcontainers "Could not find a valid Docker environment"), Docker
Desktop is probably stopped. In either case: stop and tell me. Do not change
code, config or credentials to work around it.
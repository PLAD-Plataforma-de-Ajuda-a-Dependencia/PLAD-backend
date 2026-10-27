# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Backend for PLAD (Plataforma de Ajuda a Dependência): a Spring Boot 4.1 / Java 25 REST API where users register as either a dependent (`dependente`) or a psychologist (`psicologo`), log in with JWT, and dependents fill out a one-time triage questionnaire. Domain vocabulary, enum values and seed data are in Portuguese.

## Commands

Use the Maven wrapper (`./mvnw` in bash, `.\mvnw.cmd` in PowerShell):

- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run`
- All tests: `./mvnw test`
- Single test class / method: `./mvnw test -Dtest=TestDataRunner` / `./mvnw test -Dtest=TestDataRunner#savesAndFindsUserByEmail`

The app and **all tests** need a running PostgreSQL at `localhost:5432/dbtest` (user/password `postgres`/`postgres`, see `src/main/resources/application.properties`). There is no in-memory test DB: `@DataJpaTest` tests use `@AutoConfigureTestDatabase(replace = NONE)`, so they hit the real Postgres with Flyway migrations applied.

Running the app also needs a `JWT_SECRET` env var (at least 32 bytes, e.g. from `openssl rand -base64 48`). There is no default on purpose, so the app refuses to start without it. Tests don't need it: the surefire config in `pom.xml` sets a throwaway `jwt.secret`.

## Architecture

Package root is `pladBack` (layers: `controllers` → `services` → `repositories` → `entity`, plus `DTO`, `exception`, `config`).

- **Schema is owned by Flyway**, not Hibernate: `spring.jpa.hibernate.ddl-auto=validate`. Any entity change requires a new migration `src/main/resources/db/migration/V{n}__description.sql`; never edit an already-applied migration. Triage questions are seeded via migration (V5), with `options` stored as JSONB `[{code, label}]` and `question_type` in `single_choice | multi_choice | free_text`.
- **Postgres native enums** (`user_type`, `sex`, `crm_crp_type`) map to Java enums via `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Java enum constant names must match the DB enum labels exactly (e.g. `userType.dependente`, `sex.nao_binario`).
- **User subtypes**: `users` is the base table; `dependents` and `psychologists` share its PK via `@OneToOne @MapsId` on `user_id`. `AuthService.register` creates the `User` plus the matching subtype row in one transaction. A `Triage` is one-to-one with a `Dependent` (`dependent_id` unique); answers live in `result_questions_triages`, which allows multiple rows per question (unique on `triage_id, question_id, value_result`) to support multi-choice.
- **Auth**: stateless JWT (jjwt). `SecurityConfig` permits `POST /auth/login`, `POST /auth/register` and `/error` by name and requires auth for everything else; a missing or invalid token gets 401. `JwtAuthenticationFilter` reads `Authorization: Bearer <token>` and sets the principal to the `userId` (Long) claim — controllers get the current user's ID from the principal, not an entity. Invalid tokens are ignored (request proceeds unauthenticated). `config/RateLimitInterceptor` rate-limits `/auth/login` and `/auth/register` per endpoint and client IP via Bucket4j (5/min, in-memory) in `RateLimiterService`. Emails are stored lowercased.
- **Errors**: services throw custom exceptions from `pladBack.exception`; `config/GlobalExceptionHandler` maps each to an HTTP status and an `ErrorResponseDTO(message, timestamp)`. New exceptions need a handler there.
- **JSON uses snake_case** globally (`spring.jackson.property-naming-strategy=SNAKE_CASE`), so camelCase record DTO fields serialize as snake_case.

## Conventions

- Repository interfaces and some enums use lowercase-initial names (`userRepository`, `userType`, `sex`); keep consistent with existing names rather than renaming.
- DTOs are Java records with Jakarta Validation annotations; controllers use `@Valid @RequestBody`.
- Constructor injection (no `@Autowired` on fields) in main code.


## Rules

- Never edit an already-applied migration; always create a new `V{n}__...sql`.
- Never change `ddl-auto` or disable Flyway to make something work.
- Ask before adding Maven dependencies.
- Never hardcode secrets; use env vars
- After any change, run `./mvnw test` and fix failures before saying it's done.
- Every new custom exception needs a handler in `GlobalExceptionHandler`.

## Database

PostgreSQL 16 runs in a Docker container defined in `docker-compose.yml` at the
repo root (container name: `pladDB`, named volume `pladdb_data`). Start it with
`docker compose up -d`. The app and all tests need it at
`localhost:5432/dbtest` (dev-only credentials `postgres`/`postgres`).

If tests or the app fail with a connection error (e.g. "Connection refused"),
the container is probably stopped: stop and tell me. Do not change code,
config or credentials to work around it.
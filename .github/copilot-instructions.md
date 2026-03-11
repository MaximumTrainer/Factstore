# Copilot Instructions for Factstore

## Project Overview

Factstore is a Supply Chain Compliance Fact Store — a full-stack web application for tracking and verifying that software artifacts (container images) meet predefined security and quality requirements before deployment. It uses a Kotlin/Spring Boot backend with a Vue 3/TypeScript frontend.

## Tech Stack

### Backend
- **Language:** Kotlin 2.0.20 on Java 21
- **Framework:** Spring Boot 3.2.5 with Spring Data JPA
- **Database:** H2 (in-memory)
- **Build tool:** Gradle with Kotlin DSL
- **Testing:** JUnit 5 + Mockito-Kotlin
- **API docs:** Springdoc OpenAPI 2.5.0

### Frontend
- **Framework:** Vue 3 (Composition API with `<script setup>`)
- **Language:** TypeScript 5.4
- **Build tool:** Vite 5
- **Styling:** Tailwind CSS 3.4
- **HTTP client:** Axios
- **State management:** Pinia
- **E2E testing:** Playwright

## Prerequisites

- Java 21 (Eclipse Temurin)
- Node.js 20 with npm

## Build & Test Commands

### Backend
```bash
cd backend
./gradlew build          # Compile, test, and produce JAR
./gradlew test           # Run unit tests only
./gradlew bootRun        # Start dev server on port 8080
```

### Frontend
```bash
cd frontend
npm ci                   # Install dependencies
npm run build            # Type-check and build for production
npm run dev              # Start Vite dev server on port 5173
npm run test:e2e         # Run Playwright end-to-end tests
```

## Architecture

The backend follows **Hexagonal Architecture** (Ports and Adapters). Dependencies always point inward — adapters depend on ports, ports depend on the domain.

### Package Layout
```
com.factstore/
├── core/domain/           # Business entities (Flow, Trail, Artifact, Attestation, EvidenceFile)
├── core/port/inbound/     # Driving port interfaces (IFlowService, IAssertService, …)
├── core/port/outbound/    # Driven port interfaces (IFlowRepository, ITrailRepository, …)
├── application/           # Use-case implementations (FlowService, AssertService, …)
├── adapter/inbound/web/   # REST controllers
├── adapter/outbound/persistence/  # JPA repository adapters
├── dto/                   # Request/response DTOs (all in Dtos.kt)
├── exception/             # Custom exceptions and global error handler
└── config/                # CORS and OpenAPI configuration
```

### Domain Model
```
Flow ──< Trail ──< Artifact
               └──< Attestation ──> EvidenceFile
```

### Frontend Layout
```
frontend/src/
├── views/        # Page-level Vue components
├── components/   # Reusable UI components (NavBar, StatusBadge)
├── api/          # Axios client modules per resource
├── router/       # Vue Router configuration
└── types/        # Shared TypeScript interfaces mirroring backend DTOs
```

## Coding Conventions

### Backend (Kotlin)
- Use Kotlin idioms: null safety (`?.`, `?:`), data classes, extension functions.
- Service classes implement inbound port interfaces (e.g., `FlowService : IFlowService`).
- Annotate services with `@Service` and `@Transactional`.
- Controllers use `@RestController` with `@RequestMapping("/api/v1/...")`.
- Use SLF4J for logging via `LoggerFactory.getLogger`.
- Name test functions using backtick syntax (e.g., `` `should create a flow successfully` ``).
- Throw custom exceptions (`NotFoundException`, `ConflictException`) — never return nulls for missing resources.
- DTOs are defined in a single `Dtos.kt` file; use `toResponse()` extension functions for entity-to-DTO conversion.

### Frontend (Vue 3 + TypeScript)
- Use Composition API with `<script setup lang="ts">`.
- Use Tailwind CSS utility classes for styling.
- API modules in `src/api/` export typed async functions wrapping Axios calls.
- Shared TypeScript interfaces live in `src/types/index.ts`.
- Use `ref()` and `reactive()` for component state; prefer `ref()` for primitives.

## Test-Driven Development — Red → Green → Refactor

**Every code change must follow the red-green-refactor cycle.** No production code may be written without a failing test that motivates it.

### The Cycle

1. **Red** — Write a test that describes the desired behaviour. Run it and confirm it fails (compilation errors count as red). Commit the failing test.
2. **Green** — Write the *minimum* production code required to make the failing test pass. Do not write more than needed. Run the full test suite and confirm all tests pass.
3. **Refactor** — Improve the code (naming, structure, duplication) without changing observable behaviour. The test suite must remain green throughout.

Repeat this cycle for every logical unit of work, no matter how small.

### Backend Tests

Backend tests live in `backend/src/test/kotlin/com/factstore/`. Two kinds of tests are expected:

1. **Integration tests** (`@SpringBootTest` + `@Transactional`) — wires the full Spring context with H2 and tests end-to-end service behaviour. These live directly in `com.factstore` (e.g. `FlowServiceTest`).

2. **Unit tests** (no Spring context) — test a single service in complete isolation using an in-memory mock adapter (e.g. `InMemoryFlowRepository`). These live in `com.factstore.core` (e.g. `FlowServiceUnitTest`). This is the preferred style for new business-logic tests because they run instantly and require no database.

**Mock adapters** for driven ports live in `backend/src/test/kotlin/com/factstore/adapter/mock/`. To test a new service in isolation, create an `InMemory*Repository` that implements the corresponding `I*Repository` port, then instantiate the service directly in your test's `@BeforeEach`.

**Run all backend tests:**
```bash
cd backend
./gradlew test
```

**Run a single test class:**
```bash
./gradlew test --tests "com.factstore.FlowServiceTest"
```

**What to test** — focus tests at the Service layer where business rules live. Test Controllers only when HTTP-level concerns (status codes, request validation) need coverage. Repositories require testing only for custom query methods.

### Frontend Tests

End-to-end tests live in `frontend/e2e/` and are run with Playwright against a live dev server.

```bash
cd frontend
npm run test:e2e
```

The dev server (`npm run dev`) must be running before executing E2E tests; Playwright does not start it automatically in any environment (including CI), so any workflow that runs E2E tests must start the dev server explicitly before invoking `npm run test:e2e`.

---

## CI Pipeline

GitHub Actions runs two parallel jobs on every push to `main` or any `copilot/**` branch, and on every pull request targeting `main`:

- **Backend Build & Test** — `./gradlew build` (compiles + runs all JUnit tests).
- **Frontend Build** — `npm ci && npm run build` (installs deps + compiles TypeScript).

Both jobs must be green before a PR is merged.

---

## Key Rules

### The Dependency Rule

> **The Domain and Application layers must never import from `adapter.*` or any Spring/JPA-specific type.**

Enforced by package structure:
- `core/domain/` — no imports from `application`, `adapter`, Spring, or JPA
- `core/port/` — imports only from `core/domain/` and `dto/`
- `application/` — imports only from `core/`, `dto/`, and `exception/`
- `adapter/inbound/web/` — imports from `core/port/inbound/` and `dto/`
- `adapter/outbound/persistence/` — imports from `core/port/outbound/` and `core/domain/`

### Other Rules

- **All REST endpoints** are under the `/api/v1` base path.
- **Frontend proxies** API calls to the backend via Vite's dev server proxy (`/api` → `http://localhost:8080`).
- **UUIDs** are used as primary keys for all entities.
- **Timestamps** use `java.time.Instant` in the backend.

---

## Adding or Changing Code — Checklist

Before opening a pull request, verify each item:

### Dependency Rule compliance
- [ ] No class in `core/domain/` or `core/port/` imports from `application/`, `adapter/`, or any Spring/JPA type.
- [ ] No class in `application/` imports from `adapter/`.
- [ ] `adapter/inbound/web/` controllers depend on inbound port **interfaces** (`IFlowService`, etc.), not concrete service classes.
- [ ] `adapter/outbound/persistence/` adapters depend on JPA repositories and implement outbound port **interfaces** (`IFlowRepository`, etc.).

### Where to add a new feature
1. **Add the outbound port first** — define the new data-access method in `core/port/outbound/I*Repository.kt`.
2. **Implement the adapter** — implement the method in the corresponding `*RepositoryAdapter` and `*RepositoryJpa` in `adapter/outbound/persistence/`.
3. **Add the inbound port** — define the new use case in `core/port/inbound/I*Service.kt`.
4. **Implement the service** — implement the method in `application/*Service.kt` (injecting only port interfaces, never concrete adapters).
5. **Add the web adapter** — expose the endpoint in `adapter/inbound/web/*Controller.kt` (using the inbound port interface).
6. **Write the unit test first** — test the service in isolation using a mock adapter in `src/test/.../adapter/mock/`.
7. **Add an integration test** if HTTP-level or database behaviour needs verification.

### General checks
- [ ] A failing test was written **before** the production code (Red step completed).
- [ ] All new behaviour is covered by at least one test.
- [ ] All existing tests still pass (`./gradlew test` and `npm run build`).
- [ ] No business logic has been placed in Controllers or Repositories.
- [ ] New endpoints follow the existing REST conventions and return appropriate HTTP status codes.
- [ ] New domain exceptions follow the existing exception pattern and are handled by `GlobalExceptionHandler`.
- [ ] New TypeScript types are added to `frontend/src/types/index.ts`.
- [ ] CI is green on the pull request.

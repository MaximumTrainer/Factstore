# Contributing to OpenFactstore

Thank you for your interest in contributing! This document explains how to set up your development environment, run tests, and submit a pull request.

---

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Running Tests](#running-tests)
- [Code Style](#code-style)
- [Architecture Constraints](#architecture-constraints)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Commit Message Format](#commit-message-format)

---

## Development Environment Setup

### Prerequisites

- **Java 21** (Eclipse Temurin recommended)
- **Node.js 20** + npm
- **Docker & Docker Compose** (for PostgreSQL and Vault)

### Steps

```bash
# 1. Fork and clone the repository
git clone https://github.com/<your-fork>/OpenFactstore.git
cd OpenFactstore

# 2. Start dependent services
docker compose up -d postgres vault

# 3. Start the backend
cd backend
./gradlew bootRun
# Backend available at http://localhost:8080
# Swagger UI at http://localhost:8080/swagger-ui.html

# 4. In a new terminal, start the frontend
cd frontend
npm ci
npm run dev
# Frontend available at http://localhost:5173
```

---

## Running Tests

### Backend Unit Tests

```bash
cd backend
./gradlew test
```

Unit tests use an H2 in-memory database and require no running services. Test reports are written to `backend/build/reports/tests/test/`.

```bash
# Run a specific test class
./gradlew test --tests "com.factstore.application.FlowServiceTest"

# Run with verbose output
./gradlew test --info
```

### Frontend End-to-End Tests

```bash
cd frontend
npm run test:e2e
```

Playwright tests require the backend to be running.

### CI Pipeline

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs backend tests and frontend build in parallel on every push and pull request. All checks must pass before a PR can be merged.

---

## Code Style

### Kotlin (Backend)

- Follow standard **Kotlin idioms**: null safety (`?.`, `?:`), data classes, extension functions, `when` expressions.
- Use `LoggerFactory.getLogger(ClassName::class.java)` for logging (SLF4J).
- Name test functions with backtick syntax: `` `should create a flow successfully` ``.
- Throw custom exceptions (`NotFoundException`, `ConflictException`) — never return `null` for missing resources.
- DTOs go in `dto/Dtos.kt`. Add `toResponse()` extension functions on domain entities for conversion.
- Annotate services with `@Service` and `@Transactional`.
- Controllers use `@RestController` with `@RequestMapping("/api/v1/...")`.

### TypeScript / Vue (Frontend)

- Use Composition API with `<script setup>` syntax — no Options API.
- All components and API modules are TypeScript strict.
- Define shared types in `src/types/` to mirror backend DTOs.
- Use Pinia stores for shared state, Axios modules in `src/api/` for HTTP calls.

---

## Architecture Constraints

OpenFactstore uses **Hexagonal Architecture**. The **dependency rule** must never be violated:

```
Adapters → Ports → Domain
```

Specifically:
- `core/domain/` must not import from `application/`, `adapter/`, or any Spring annotation.
- `application/` may only import from `core/` (domain + ports). No Spring Web or JPA.
- `adapter/inbound/web/` (controllers) depend only on inbound port interfaces, never on `application/` directly (except via the interface).
- `adapter/outbound/persistence/` depends on outbound port interfaces and JPA — not on `application/`.

When adding a new feature:

1. Add the domain entity in `core/domain/`.
2. Define the repository interface in `core/port/outbound/`.
3. Define the service interface in `core/port/inbound/`.
4. Implement the service in `application/`.
5. Implement the JPA repository adapter in `adapter/outbound/persistence/`.
6. Implement the REST controller in `adapter/inbound/web/`.
7. Add DTOs to `dto/Dtos.kt`.

---

## Pull Request Guidelines

- Open PRs against the `main` branch.
- Ensure all CI checks pass before requesting review.
- Include a clear description of **what** changed and **why**.
- Reference the relevant issue number in the PR description: `Closes #<issue>`.
- Keep PRs focused — one feature or fix per PR.
- Add unit tests for any new business logic in `application/`.
- Do not modify committed Flyway migration scripts — add a new numbered migration instead.

---

## Commit Message Format

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <short description>

<optional body>

<optional footer>
Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

**Types:**

| Type | When to use |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `test` | Adding or updating tests |
| `refactor` | Code change that is not a feat or fix |
| `build` | Build system or dependency changes |
| `ci` | CI/CD configuration changes |
| `chore` | Maintenance tasks |

**Examples:**

```
feat: add pull request attestation endpoint (#42)
fix: return 409 when flow name already exists
docs: add CI/CD integration guide
test: add unit tests for AssertService
```

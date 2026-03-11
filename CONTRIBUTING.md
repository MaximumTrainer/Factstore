# Contributing to Factstore

Thank you for your interest in contributing to Factstore! This document provides guidelines for contributing to the project.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Architecture Overview](#architecture-overview)
- [Coding Conventions](#coding-conventions)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Documentation Updates](#documentation-updates)

---

## Getting Started

1. Fork the repository and clone your fork.
2. Follow the [User Guide — Setup & Installation](USER_GUIDE.md#1-setup--installation) to configure your environment.
3. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Prerequisites

- **Java 21** (Eclipse Temurin recommended)
- **Node.js 20** with npm

### Backend

```bash
cd backend
./gradlew build      # Build and run tests
./gradlew bootRun    # Start dev server on port 8080
```

### Frontend

```bash
cd frontend
npm ci               # Install dependencies
npm run dev          # Start dev server on port 5173
```

See the [User Guide](USER_GUIDE.md) for detailed setup instructions.

---

## Architecture Overview

Factstore uses **Hexagonal Architecture** (Ports and Adapters). Dependencies always point inward — adapters depend on ports, ports depend on the domain.

```
com.factstore/
├── core/domain/           # Business entities
├── core/port/inbound/     # Driving port interfaces
├── core/port/outbound/    # Driven port interfaces
├── application/           # Use-case implementations
├── adapter/inbound/web/   # REST controllers
├── adapter/outbound/persistence/  # JPA repository adapters
├── dto/                   # Request/response DTOs
├── exception/             # Custom exceptions and global error handler
└── config/                # CORS and OpenAPI configuration
```

> **Dependency Rule:** The domain and application layers must never import from `adapter` or any external framework. Adapters depend on port interfaces; port interfaces depend only on the domain and DTOs.

For a full architecture description, see the [README](README.md#architecture).

---

## Coding Conventions

### Backend (Kotlin)

- Use Kotlin idioms: null safety (`?.`, `?:`), data classes, extension functions.
- Service classes implement inbound port interfaces (e.g., `FlowService : IFlowService`).
- Annotate services with `@Service` and `@Transactional`.
- Controllers use `@RestController` with `@RequestMapping("/api/v1/...")`.
- Use SLF4J for logging via `LoggerFactory.getLogger`.
- Throw custom exceptions (`NotFoundException`, `ConflictException`) — never return nulls for missing resources.
- DTOs are defined in `Dtos.kt`; use `toResponse()` extension functions for entity-to-DTO conversion.

### Frontend (Vue 3 + TypeScript)

- Use Composition API with `<script setup>` syntax.
- TypeScript interfaces in `src/types/` must mirror backend DTOs.
- API client modules in `src/api/` use the shared Axios instance from `client.ts`.
- Use Tailwind CSS utility classes for styling.

### Test Naming

- **Backend:** Use backtick syntax — `` `should create a flow successfully` ``.
- **Frontend:** Use descriptive strings in Playwright test blocks.

---

## Testing Requirements

### Backend

All changes must include tests. Follow the **Red → Green → Refactor** cycle:

1. Write a failing test that describes the expected behavior.
2. Write the minimal code to make the test pass.
3. Refactor while keeping tests green.

```bash
cd backend
./gradlew test                              # Run all tests
./gradlew test --tests "com.factstore.*"    # Run tests matching a pattern
```

### Frontend

End-to-end tests use Playwright:

```bash
cd frontend
npm run test:e2e
```

---

## Pull Request Process

1. Ensure all backend tests pass: `cd backend && ./gradlew test`
2. Ensure the frontend builds: `cd frontend && npm run build`
3. Update documentation if your change affects:
   - API endpoints → update [API_REFERENCE.md](API_REFERENCE.md)
   - Setup steps → update [USER_GUIDE.md](USER_GUIDE.md)
   - Security practices → update [SECURITY.md](SECURITY.md)
4. Open a pull request against `main` with a clear description of your changes.
5. CI will run backend tests and frontend build automatically.

---

## Documentation Updates

When making changes, keep documentation synchronized:

| If you change... | Update... |
|---|---|
| REST API endpoints or DTOs | [API_REFERENCE.md](API_REFERENCE.md) |
| Setup steps, dependencies, or configuration | [USER_GUIDE.md](USER_GUIDE.md) and [README.md](README.md) |
| Security model or access control | [SECURITY.md](SECURITY.md) and [USER_GUIDE.md](USER_GUIDE.md#4-security--data-privacy) |
| Domain model or architecture | [README.md](README.md#architecture) |

See the [User Guide](USER_GUIDE.md) for a complete overview of all documentation.

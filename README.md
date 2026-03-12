# Factstore

Factstore is a software supply chain traceability platform. It gives your engineering organisation a tamper-evident record of every artefact you build, every quality gate you pass, and every deployment you make — so you can answer compliance questions with evidence rather than guesswork.

## Getting Started

The [Getting Started guide](./docs/getting-started/01-overview.md) walks you through every concept from first principles:

1. [Overview](./docs/getting-started/01-overview.md)
2. [Setup & Access](./docs/getting-started/02-setup.md)
3. [Authentication](./docs/getting-started/03-authentication.md) *(coming soon)*
4. [Flows](./docs/getting-started/04-flows.md)
5. [Trails](./docs/getting-started/05-trails.md)
6. [Artifacts](./docs/getting-started/06-artifacts.md)
7. [Attestations](./docs/getting-started/07-attestations.md)
8. [Environments](./docs/getting-started/08-environments.md) *(coming soon)*
9. [Policies](./docs/getting-started/09-policies.md) *(coming soon)*
10. [Approvals](./docs/getting-started/10-approvals.md) *(coming soon)*
11. [Next Steps & Roadmap](./docs/getting-started/11-next-steps.md)

## Tech Stack

- **Backend:** Kotlin + Spring Boot (Java 21)
- **Frontend:** Vue.js + TypeScript
- **Database:** H2 in-memory (PostgreSQL on the [roadmap](https://github.com/MaximumTrainer/Factstore/issues/12))
- **API:** REST — explore all endpoints at `http://localhost:8080/swagger-ui/index.html`

## Quick Start

```bash
git clone https://github.com/MaximumTrainer/Factstore.git
cd Factstore/backend
./gradlew bootRun
# API available at http://localhost:8080
# Swagger UI at  http://localhost:8080/swagger-ui/index.html
```

## Contributing

Pick an open [issue](https://github.com/MaximumTrainer/Factstore/issues) and open a pull request against `main`.

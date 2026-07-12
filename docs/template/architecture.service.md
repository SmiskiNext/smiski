# [Service Name] — Service Architecture

> **Per-service architecture template.** Copy this file into
> `docs/[service-name].architecture.md`, fill in every `[placeholder]`, and
> update it when the service changes. For hexagonal layering, DDD patterns,
> domain model details, and shared conventions see `services/AGENTS.md`.

## 1. Service Identity

- **Name:** [service-name]
- **Package:** `io.github.smiskinext.[name]`
- **Bounded Context:** [bounded context this service owns]
- **Responsibility:** [1-2 sentences describing what this service does and why
  it exists]

## 2. Context & Dependencies

- **Upstream (callers):** [services or external actors that call this service]
- **Downstream (dependencies):** [services, stores, or external systems this
  service calls]

```text
[Caller A] ──→ ([this service]) ──→ [Dependency X]
[Caller B] ──→ ([this service]) ──→ [Dependency Y]
```

> Replace the diagram above with actual callers and dependencies. Keep it
> text-based and simple.

## 3. API Surface

- **Endpoint groups:** [list primary REST/SSE groups, e.g. meetings CRUD,
  join-request flow, webhook receiver]
- **Full spec:** `services/[name]/openapi.yaml`

> The OpenAPI spec is generated — reference it directly instead of duplicating
> endpoint details here.

## 4. Events

### Published

| Topic        | Trigger                 | Payload summary |
| ------------ | ----------------------- | --------------- |
| [topic.name] | [what causes the event] | [key fields]    |
| [topic.name] | [what causes the event] | [key fields]    |

> Use "None" if this service does not publish events.

### Consumed

| Topic        | Source service | Action                              |
| ------------ | -------------- | ----------------------------------- |
| [topic.name] | [source]       | [what this service does on receipt] |
| [topic.name] | [source]       | [what this service does on receipt] |

> Use "None" if this service does not consume events.

## 5. Data Stores

- **Database:** [type, e.g. PostgreSQL] — `[db_name]`
- **Cache / other:** [e.g. Valkey — purpose: read models, pub/sub]

### Tables

| Table     | Purpose          | Notes                       |
| --------- | ---------------- | --------------------------- |
| [table_a] | [what it stores] | [partitioning / PK / index] |
| [table_b] | [what it stores] | [partitioning / PK / index] |
| [table_c] | [what it stores] | [partitioning / PK / index] |

### Schema

```mermaid
erDiagram
    TABLE_A ||--o{ TABLE_B : "[relationship]"
    TABLE_A {
        uuid id PK
        uuid tenant_id "[partition key]"
        string name
        timestamp created_at
    }
    TABLE_B {
        uuid id PK
        uuid tenant_id FK
        uuid table_a_id FK
        string status
    }
```

> Use "No database" if the service is stateless. List only stores this service
> owns or directly uses. Keep the ER diagram aligned with
> `services/[name]/src/main/resources/db/migration/`.

## 6. External Integrations

- **[Integration name]**
    - Purpose: [what it provides to this service]
    - Method: [REST API / gRPC / SDK / webhook]
- **[Integration name]**
    - Purpose: [what it provides to this service]
    - Method: [REST API / gRPC / SDK / webhook]

> Omit this section or write "None" if no external integrations exist.

## 7. Operations

- **Configuration**
    - `[notable config key from application.yaml]` — [purpose]
    - `[notable config key]` — [purpose]
- **Build & Test**
    - Build: `./services/gradlew -p services/[name] build`
    - Test: `./services/gradlew -p services/[name] test`
    - See `services/AGENTS.md` for full command reference.
- **Deployment**
    - Manifest: `services/k8s/[name]/`
    - Notes: [any deployment-specific concern, e.g. node isolation, replicas,
      resource limits]

## 8. Service-Specific Notes

### Notable Concerns

- [anything unique to this service: CQRS level, retention policy, no-DB design,
  CPU-bound paths, etc.]

### Glossary

- **[Term]:** [definition specific to this service]
- **[Term]:** [definition specific to this service]

### Last Updated

- [YYYY-MM-DD]

# AGENTS.md

This file gives coding agents the local map for working in this repository.

## Project Snapshot

DataFusion is a Java/Maven multi-module data integration platform. The backend is organized around metadata management, ingestion, development, asset governance, scheduling, datasource access, and plugin/agent execution pieces.

The root Maven project is `com.datafusion:datafusion-parent:1.0.0` and targets Java 21.

## Modules

- `datafusion-common`: shared lightweight utilities: cron parsing, date/time helpers, type conversion, SQL template rendering, DAG utilities, exceptions, options, UUIDs, and thread pools.
- `datafusion-common-data`: shared model/data objects intended to mirror module package paths.
- `datafusion-common-spring`: Spring/MyBatis supporting utilities such as type handlers.
- `datafusion-datasource`: datasource connector framework, JDBC/Cassandra executors, SQL mapper scanning/proxy support, and datasource managers.
- `datafusion-manager`: main Spring Boot management backend. Entry point: `com.datafusion.manager.ManagerApplication`.
- `datafusion-scheduler-master`: scheduling master domain, worker registry/storage, flow/action logic, scheduler exceptions and models.
- `datafusion-scheduler-worker`: worker module skeleton.
- `datafusion-agent`: agent module skeleton.
- `datafusion-plugin`: plugin parent module.
- `datafusion-plugin/datafusion-plugin-api`: plugin API child module.
- `datafusion-web`: frontend placeholder Maven module. Its `frontend-maven-plugin` React build is currently commented out.

## Important Source Areas

- `datafusion-manager/src/main/java/com/datafusion/manager/metadata`: metadata controllers, services, DAOs, PO/DTO objects, and database-specific support.
- `datafusion-manager/src/main/java/com/datafusion/manager/ingestion`: ingestion controllers, services, DAOs, tasks, and SQL/DataX helpers.
- `datafusion-manager/src/main/java/com/datafusion/manager/development`: development SQL and script task APIs/services.
- `datafusion-manager/src/main/java/com/datafusion/manager/asset`: asset resource, lineage, metrics, ETL process, and SkyWalking-related services.
- `datafusion-manager/src/main/java/com/datafusion/manager/scheduler`: manager-facing scheduler controllers, services, DAOs, config, and storage.
- `datafusion-common/src/test/java`: most current unit test coverage lives here.

## Build And Test

Use Maven from the repository root.

```bash
mvn test
```

For scoped work, prefer module-local verification with dependency build-up:

```bash
mvn -pl datafusion-common -am test
mvn -pl datafusion-datasource -am test
mvn -pl datafusion-manager -am test
```

Useful narrower test runs:

```bash
mvn -pl datafusion-common -Dtest=DateTimeStampTest test
mvn -pl datafusion-common -Dtest=JFinalSqlBuilderTest test
```

Notes:

- The project is configured for Java 21 via `maven.compiler.release/source/target`.
- This workspace may run a newer local JDK, but changes should stay Java 21 compatible.
- The root POM sets `skip.checkStyle` to `true`.
- `datafusion-manager/pom.xml` currently references artifacts named `datafusion-common-web` and `datafusion-scheduler`; verify local dependency availability before assuming a full reactor build succeeds.
- Build output under `target/` is generated and should not be hand-edited.

## Runtime Notes

The primary runnable backend application is:

```bash
mvn -pl datafusion-manager -am spring-boot:run
```

`ManagerApplication` scans `com.datafusion`, enables scheduling, sets Calcite charsets to UTF-8, registers custom `@SqlScan`, and configures MyBatis mapper scanning for asset, ingestion, metadata, scheduler, and development DAOs.

There are few checked-in runtime resources at the moment. Be careful not to assume local database, Nacos, Redis, OSS, or external service configuration exists unless a task provides it.

## Code Style

- Follow Alibaba Java Coding Guidelines and the local style notes in `style/codeStyle.md`.
- Keep source encoding as UTF-8.
- Use existing package and layer naming:
  - `controller`: API/controller layer.
  - `service`: business interfaces and implementations.
  - `dao`: database access interfaces, generally aligned with table structure.
  - `po`: persistence objects, generally aligned with database tables.
  - `dto`: transfer objects for frontend and inter-module boundaries.
  - `vo`: view objects, reserved/optional.
  - `constant`, `enums`, `model`: constants, enums, and framework/domain structures.
- In `datafusion-manager`, keep business domains under their current top-level packages: `metadata`, `ingestion`, `development`, `asset`, `scheduler`, `auth`, `config`, and `utils`.
- Prefer existing utilities from `datafusion-common` and existing Spring/MyBatis patterns before adding new abstractions.
- Use Lombok consistently where surrounding code already does.

## Testing Guidance

- Add or update JUnit tests when changing reusable logic in `datafusion-common`, datasource behavior, cron/date/type conversion, SQL rendering, graph logic, or service code with non-trivial branching.
- Existing tests use JUnit 5 in most active modules; some skeleton modules still depend on JUnit 4.
- For changes that touch Spring Boot wiring, MyBatis mappers, external services, or scheduler state, prefer focused unit tests with mocks unless integration configuration is explicitly available.
- If a full Maven build cannot run because of missing private/local artifacts or external configuration, run the narrowest meaningful module/test command and document the blocker.

## Dependency And Module Boundaries

- Keep shared, dependency-light helpers in `datafusion-common`.
- Keep shared DTO/model classes in `datafusion-common-data`.
- Keep Spring/MyBatis-specific shared code in `datafusion-common-spring`.
- Keep connector/executor/scanning infrastructure in `datafusion-datasource`.
- Keep user-facing backend API and business workflows in `datafusion-manager`.
- Avoid making low-level modules depend on `datafusion-manager`.
- When adding a new Maven dependency, prefer adding its version to root `dependencyManagement` unless the dependency is truly module-specific and already versioned locally by precedent.

## Agent Workflow

- Start by checking `git status --short --branch`; do not overwrite unrelated user changes.
- Use `rg`/`rg --files` for repository search.
- Read the relevant `pom.xml`, nearby code, and tests before editing.
- Keep changes scoped to the requested module and behavior.
- Do not edit `.idea/`, `target/`, generated class files, or local build artifacts.
- After edits, run the most relevant Maven test command and report exactly what was run and any blocker.
- If adding or changing public APIs, update nearby tests and any README/docs that describe the behavior.

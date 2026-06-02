---
name: sdd-spring-web
description: Data-structure-driven Spring Web workflow for the DataFusion repository. Use when adding or changing Spring Web, Controller, Service, DTO, MyBatis-Plus Mapper, Entity/PO, persistence, scheduler integration, or manager module backend features; also use when the user asks to create data definition/design docs before implementation.
---

# SDD Spring Web

Use this skill to design and implement DataFusion backend changes with a data-structure-driven workflow.

## Core Rule

Treat `{feature}-data-define.md` as the source of truth for fields, Java types, DB columns, validation, and layer mappings.
Treat `{feature}-design.md` as the source of truth for behavior, API contracts, file changes, service logic, integrations, and verification.

Do not implement fields, DTOs, SQL, or API shapes that are not reflected in the documents. If implementation needs to diverge, update the documents first.

## Required Context

Before editing docs or code, read:

1. `references/global-conventions.md`
2. Neighboring code in the target module/package
3. `AGENTS.md` only for repository facts, module status, build risks, and external runtime assumptions

For detailed templates, load only the needed file:

- `references/data-define.md` when writing the data structure document
- `references/design.md` when writing the design document

## Workflow

1. Identify the target module, package, feature name, and expected behavior.
2. Scan adjacent Controller, Service, DTO, Mapper, PO/Entity, XML, and tests before proposing new files.
3. Generate or update `docs/{module}/{feature}-data-define.md`.
4. Generate or update `docs/{module}/{feature}-design.md`.
5. Implement conservatively using DataFusion local patterns.
6. Verify with the smallest reliable module command, usually `mvn -DskipTests compile -pl <module> -am`.
7. Check Java style against `style/codeStyle.md` and `style/CheckStyle-13.0.0.xml`.

## Document Rules

- Use Chinese for prose and explanations.
- Use English technical names, class names, package names, field names, paths, and commands.
- Keep documents concise; omit sections that do not apply by writing `无`.
- DTOs are optional. Define only DTOs this feature actually creates or changes.
- Do not force CRUD shape. List only actual APIs and service methods.
- Record reused objects explicitly to avoid duplicate classes.

## Implementation Rules

- Keep Controller thin; business rules belong in Service.
- Prefer existing manager package layout: `controller`, `service`, `service.impl`, `dao`, `po`, `dto`, `vo`, `enums`, `model`.
- Prefer MyBatis-Plus `BaseMapper` patterns already used in the target package.
- Reuse `Result<T>`, `PageQuery<T>`, `PageResponse<T>`, `BaseEntity`, `BaseIdEntity`, `CommonException`, and `ErrorCodeEnum`.
- Do not add new dependencies unless the design document explains why.
- Do not suppress Checkstyle. Fix Javadoc, imports, spacing, naming, and indentation directly.

## Output Expectations

When design-only work is requested, create or update the two docs and summarize the key decisions.

When implementation is requested, complete code changes, update docs if contracts changed, run verification when possible, and report:

- Changed files
- Verification command and result
- Known build or environment limits

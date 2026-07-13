# Project Convention Discovery

> This reference defines how `sdd-java-web` initializes and loads project-specific conventions. Keep the skill generic; store project-specific rules inside the target project.

## Contents

- [1. Recommended Structure](#1-recommended-structure)
- [2. project-index.yml](#2-project-indexyml)
- [3. Index Schema](#3-index-schema)
- [4. Init Mode](#4-init-mode)
- [5. Document Layout](#5-document-layout)
- [6. Work Mode](#6-work-mode)
- [7. Project Conventions Boundary](#7-project-conventions-boundary)
- [8. Module Document Boundary](#8-module-document-boundary)
- [9. Lifecycle Convention Resolution](#9-lifecycle-convention-resolution)

## 1. Recommended Structure

```text
.codex/skills/sdd-java-web/
  SKILL.md
  references/
    data-define.md
    design.md
    convention-discovery.md

target project:
  docs/.sdd/project-index.yml
  docs/.sdd/project-conventions.md
```

Keep convention files few and stable:

- `docs/.sdd/project-conventions.md`: the only project-level convention document.
- `docs/{feature}-data-define.md`: single-module feature data definition.
- `docs/{feature}-design.md`: single-module feature design.
- `docs/{module}/{feature}-data-define.md`: multi-module feature data definition.
- `docs/{module}/{feature}-design.md`: multi-module feature design.

## 2. project-index.yml

`docs/.sdd/project-index.yml` is the project SDD index. It stores paths and lightweight switches, not long-form conventions.

Multi-module example:

```yaml
version: 1
project_conventions: docs/.sdd/project-conventions.md
modules:
  backend-module:
    feature_docs_root: docs/backend-module
    source_roots:
      - backend-module/src/main/java
      - backend-module/src/main/resources
    verify:
      - mvn -DskipTests compile -pl backend-module -am
  frontend-module:
    feature_docs_root: docs/frontend-module
    source_roots:
      - frontend-module/src
    verify:
      - npm run build
docs:
  feature_root: docs
  language: en
  empty_marker: N/A
```

Single-module example:

```yaml
version: 1
project_conventions: docs/.sdd/project-conventions.md
docs:
  feature_root: docs
  language: en
  empty_marker: N/A
```

Discovery rule:

- Read `docs/.sdd/project-index.yml` by default.
- If it does not exist, infer conventions from default paths and neighboring code.

## 3. Index Schema

| Field | Required | Description |
|-------|----------|-------------|
| `version` | Yes | Index schema version. Use `1` for this skill version. |
| `project_conventions` | Yes | Path to the project-level convention document. |
| `docs.feature_root` | Yes | Default root for feature documentation. |
| `docs.language` | No | Language for generated docs. If absent, follow the user's language. |
| `docs.empty_marker` | No | Marker for required-but-not-applicable sections. Default: `N/A`. |
| `docs.data_define_suffix` | No | Data definition suffix. Default: `data-define`. |
| `docs.design_suffix` | No | Design suffix. Default: `design`. |
| `modules` | No | Module map for multi-module projects. |
| `modules.*.feature_docs_root` | Yes when `modules.*` exists | Feature docs root for that module. |
| `modules.*.source_roots` | No | Source roots to inspect before editing. |
| `modules.*.verify` | No | Recommended verification commands for that module. |

## 4. Init Mode

When the user asks to initialize `sdd-java-web` conventions:

1. Read `AGENTS.md`, `README.md`, build files, package manifests, and existing `docs/`.
2. Detect tech stack, module layout, API wrappers, pagination models, persistence patterns, data-structure ownership and lifecycle patterns, frontend routing, build commands, and style rules.
3. Create `docs/.sdd/project-index.yml`.
4. Create `docs/.sdd/project-conventions.md`.
5. Do not write project-specific rules back into the skill.

## 5. Document Layout

Single-module projects generate:

```text
docs/{feature}-data-define.md
docs/{feature}-design.md
```

Multi-module projects generate:

```text
docs/{module}/{feature}-data-define.md
docs/{module}/{feature}-design.md
```

Complex features may split design documents:

```text
docs/{feature}-common-design.md
docs/{feature}-backend-design.md
docs/{feature}-frontend-design.md
```

or:

```text
docs/{module}/{feature}-common-design.md
docs/{module}/{feature}-backend-design.md
docs/{module}/{feature}-frontend-design.md
```

No matter how many design documents exist, keep only one data definition document.

## 6. Work Mode

When the user asks to design, implement, or review a feature:

1. Read `docs/.sdd/project-index.yml`.
2. Read the `project_conventions` file.
3. Read neighboring code, existing docs, and tests for the target module or feature.
4. If the index does not exist, fall back to `AGENTS.md`, `README.md`, build files, package manifests, and neighboring code; mention that the project is not initialized.
5. If feature docs live directly under `docs/`, use single-module mode.
6. If feature docs live under `docs/{module}/`, use multi-module mode and maintain the project index.

## 7. Project Conventions Boundary

`project-conventions.md` should record stable cross-feature facts:

- Tech stack and version requirements.
- Module map and directory conventions.
- API response, pagination, error, authentication, and tenant-context conventions.
- Database, ORM, migration, and DDL conventions.
- Data-structure ownership and lifecycle conventions across API, Controller, Service, persistence, frontend, and integration boundaries, including standard conversion points, mutation authority, and retention or invalidation rules.
- Frontend route, menu, API client, state management, and component conventions.
- Build, test, format, and lint commands.
- Known runtime dependencies and environment risks.

Do not put single-feature fields, API contracts, state machines, or page behavior in `project-conventions.md`; those belong in feature-level `data-define` and `design` docs.

## 8. Module Document Boundary

Do not generate module design documents by default. Prefer expressing module responsibilities, source roots, feature-doc roots, and verification commands through `project-index.yml` and `project-conventions.md`.

If a module needs a long-lived design document, put it in the real docs tree, such as `docs/{module}/{module}-design.md`, not under `docs/.sdd/`.

## 9. Lifecycle Convention Resolution

Resolve ownership and lifecycle boundaries in this order:

1. Use explicit feature decisions, existing feature design, and project conventions when they already define the boundary.
2. Inspect neighboring code with the same responsibility. Prefer the dominant consistent pattern over an isolated example.
3. When the code pattern is consistent and has no concrete design risk, follow it and record the supporting convention, path, or class in the lifecycle map. Do not ask the designer to repeat an established decision.
4. When code contains multiple patterns, list each relevant pattern and its evidence, explain the behavioral impact, and ask the designer which boundary to use.
5. When a consistent pattern has a concrete risk, describe the current pattern, the risk, and viable options before asking the designer. Do not rewrite the pattern without approval.
6. When there is insufficient evidence, ask a precise boundary question rather than making a silent assumption.

Concrete risks include layer leakage, unintended serialization of persistence or external models, unclear mutation ownership, retention without invalidation, request or transaction data escaping its scope, frontend state outliving its page or component, and security or privacy exposure. Personal stylistic preference is not a design risk.

Unresolved lifecycle questions may remain in a clearly marked draft, but they block finalized design and implementation.

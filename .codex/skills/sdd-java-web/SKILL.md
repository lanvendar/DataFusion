---
name: sdd-java-web
description: Structure-driven Java Web feature design and implementation skill. Use when defining data ownership and lifecycle boundaries; defining database, Java backend, and frontend data models; connecting them through design documents; initializing project conventions; or changing Web pages, API clients, Spring Web Controllers, Services, DTOs, Mapper/DAO, Entity/PO, persistence, integrations, or module features.
---

# SDD Java Web

Use this skill to design and implement Java Web features with a structure-driven workflow.

## Core Rule

Treat `{feature}-data-define.md` as the single source of truth for data structures across Database, Backend, and Frontend.
Treat design documents as the source of truth for behavior, data flow, API contracts, service logic, integrations, file changes, and verification.

Define an owner and lifecycle boundary for every data structure participating in the data flow. At object level, make its creation or source, valid scope, mutation authority, exit or conversion, and retention or invalidation explicit. Do not confuse this technical data-object lifecycle with business state transitions.

Resolve lifecycle boundaries from explicit feature decisions, project conventions and existing design documents, and consistent patterns in neighboring code. Follow a clear existing code pattern when it has no concrete design risk, and record the evidence. When existing patterns conflict, leave a boundary incomplete, or introduce a concrete risk, present the current patterns, their impact, and the available options to the designer. Ask the designer when evidence is insufficient. Never silently assume a lifecycle boundary or rewrite an established pattern based only on stylistic preference.

Do not implement fields, DTOs, SQL, or API shapes that are not reflected in the documents. If implementation needs to diverge, update the documents first.

Do not finalize design or begin implementation while a lifecycle decision is unresolved. A draft may record precise clarification questions, but unresolved entries are not completed design.

Load project conventions through `references/convention-discovery.md` before choosing document paths or implementation patterns.

## Required Context

Before editing docs or code, read:

1. `references/convention-discovery.md`
2. Project conventions and indexes discovered by that reference
3. Neighboring code in the target module/package
4. `AGENTS.md`, `README.md`, build files, and package manifests only for missing repository facts

For detailed templates, load only the needed file:

- `references/data-define.md` when writing the data structure document
- `references/design.md` when writing the design document
- `references/convention-discovery.md` when initializing or loading project conventions

## Workflow

1. Identify whether the request is project initialization, design-only work, implementation, or review.
2. Load project conventions according to `references/convention-discovery.md`.
3. Identify the target module, package, feature name, and expected behavior.
4. Scan adjacent frontend pages/components/API clients and backend Controller, Service, DTO, Mapper, PO/Entity, XML, and tests before proposing new files.
5. Inventory every participating data structure and resolve its owner and lifecycle boundary from explicit decisions, project conventions, existing design, and neighboring code. Escalate conflicting, risky, incomplete, or unsupported boundaries to the designer.
6. Generate or update the feature data definition document under `docs/` or `docs/{module}/` according to project index and module layout.
7. Generate or update the needed design document(s), verifying every data flow against the declared lifecycle boundaries.
8. Implement conservatively using project local patterns only after lifecycle blockers are resolved.
9. Verify with the smallest reliable command defined by project conventions.
10. Check style using the project-defined style and lint rules.

## Document Rules

- Use the language configured in `docs/.sdd/project-index.yml` under `docs.language`; if it is absent, follow the user's language.
- When generating docs, localize headings, prose, and empty markers according to `docs.language` and `docs.empty_marker`.
- Use English technical names, class names, package names, field names, paths, and commands.
- Use Simple Mode by default: keep only sections involved in the change, and write the configured empty marker only when a required section does not apply.
- Use Full Mode only when the user asks for complete design, or when the feature crosses database, Java backend, frontend, and external integrations.
- Data definitions are mandatory for changed structures and optional for unchanged reused structures.
- Include every participating structure in the ownership and lifecycle map. For unchanged reused structures governed by a stable project convention, reference that convention instead of duplicating it.
- Define lifecycle once per object rather than repeating it for every field. Describe business states separately in the State / Enum Model and business rules.
- Allow a structure to cross multiple layers only when its valid scope and reuse rationale are explicit; do not force one DTO per layer.
- In drafts, record unresolved lifecycle questions explicitly. Do not use the configured empty marker or an undocumented assumption to hide an unresolved boundary.
- Backend and Frontend models use required and conditional sections. Fill only the sections the feature actually needs.
- Do not force CRUD shape. List only actual APIs and service methods.
- Record reused objects explicitly to avoid duplicate classes.
- Design documents must not repeat full field tables from the data definition document.

## Implementation Rules

- Keep Controller thin; business rules belong in Service.
- Prefer package/module layout already used by the target project.
- Prefer persistence, mapper, validation, error, response, and pagination patterns already used in the target project.
- Frontend implementation should reuse existing module layout, API client style, route/menu conventions, table/form components, and state management patterns.
- Do not add new dependencies unless the design document explains why.
- Do not suppress style/lint rules without an explicit project convention or design reason.
- Do not pass, mutate, cache, serialize, or persist a data structure beyond its declared lifecycle boundary. Convert it at the declared boundary when another layer needs a different owned structure.

## Output Expectations

When design-only work is requested, create or update the data definition and design document(s), then summarize the key data structures, ownership and lifecycle decisions, data flow decisions, and any blockers that keep the design in draft state.

When implementation is requested, complete code changes, update docs if contracts changed, run verification when possible, and report:

- Changed files
- Verification command and result
- Known build or environment limits

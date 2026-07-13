# {feature} Data Definition

> This document is the single source of truth for the ownership, lifecycle boundaries, and field definitions of Database, Java Backend, and Frontend data structures. Design documents explain flow, behavior, and constraints without repeating full definitions.

> Simple Mode: keep only sections involved in the change; use the configured empty marker for required-but-not-applicable sections. Full Mode: expand Database, Backend, Frontend, Mapping, and Reused Structures.

## Contents

- [1. Structure Ownership and Lifecycle](#1-structure-ownership-and-lifecycle)
- [2. Database Data Model](#2-database-data-model)
- [3. Java Backend Data Model](#3-java-backend-data-model)
- [4. Frontend Data Model](#4-frontend-data-model)
- [5. Data Mapping Rules](#5-data-mapping-rules)
- [6. Reused Structures](#6-reused-structures)

## 1. Structure Ownership and Lifecycle

### 1.1 Lifecycle Map (required)

| Structure | Owner | Created at / from | Valid scope | Exit / conversion | Mutation / retention | Evidence / notes |
|-----------|-------|-------------------|-------------|-------------------|----------------------|------------------|

> Include every data structure participating in the feature data flow, including unchanged reused structures. A reused structure governed by a stable project convention may reference that convention rather than duplicate it.

> Treat lifecycle as an object-level technical boundary: where an instance originates, where it may travel, who may mutate it, where it must be converted or terminated, and whether it may be retained or invalidated. Describe business state transitions separately in the State / Enum Model and design business rules.

> Use explicit feature decisions, project conventions and existing design documents, and consistent neighboring code as evidence. Follow a consistent existing pattern when it has no concrete design risk, and record the relevant path, class, or convention. A structure may span multiple layers when its valid scope and reuse rationale are explicit; do not create layer-specific DTOs without a real boundary need.

> For database structures, describe the record lifecycle, such as creation, retention, archival, soft deletion, or hard deletion. Do not use schema migration history as the runtime data lifecycle.

### 1.2 Lifecycle Clarifications (draft only; remove or resolve before finalization)

| Structure | Existing pattern / evidence | Ambiguity or risk | Decision needed | Status |
|-----------|-----------------------------|-------------------|-----------------|--------|

> Use this section when existing patterns conflict, leave a boundary incomplete, introduce a concrete design risk, or provide insufficient evidence. State current patterns and their impact before asking the designer. Do not use `{empty_marker}` or a silent assumption for an unresolved lifecycle boundary. Unresolved entries block finalized design and implementation.

## 2. Database Data Model

### 2.1 Table Model (required; use `{empty_marker}` when no database change exists)

- Table name: `{table_name}`
- Operation: create / update / not involved
- Primary key (must be explicit and defined in DDL):
- Candidate key / unique business key (must be explicit; use `{empty_marker}` when absent; define as unique constraint or unique index in DDL when present):
- Partition strategy (must be explicit; use `{empty_marker}` when absent; define in DDL when present):
- Table comment (must be explicit and defined in DDL):
- Notes:

> Database models must explicitly define primary keys, candidate keys, partition strategy, and comments. When candidate keys or partitions do not apply, write the configured empty marker instead of omitting them.

### 2.2 DDL / Migration (conditionally required)

```sql
-- CREATE TABLE / ALTER TABLE / index change; use the configured empty marker when no database change exists.
-- CREATE TABLE DDL must directly define:
-- 1. PRIMARY KEY
-- 2. UNIQUE KEY / UNIQUE INDEX for candidate or unique business keys
-- 3. partition strategy, when applicable
-- 4. table COMMENT and column COMMENT
```

### 2.3 Field / Column Model (required; use `{empty_marker}` when no database model exists)

| DB column | DB type | Java field | Java type | Required | Default | Constraint / index | DB comment | Notes |
|-----------|---------|------------|-----------|----------|---------|--------------------|------------|-------|

> If audit fields are inherited from project base entities, do not duplicate them in the Entity/PO, but keep them explicit in DDL.
> Column comments must be explicit and defined in DDL; comments should describe business meaning.

### 2.4 Seed / Initial Data (conditionally required)

| Data | Scenario | Initialization method | Notes |
|------|----------|-----------------------|-------|

## 3. Java Backend Data Model

### 3.1 Persistence Model (required; use `{empty_marker}` when no persistence model exists)

| Java field | DB column | Java type | Annotation / type handler | Notes |
|------------|-----------|-----------|---------------------------|-------|

### 3.2 API Input Model (required; use `{empty_marker}` when no API input exists)

| Object | Type | Scenario | Field | Field type | Validation / query behavior | Notes |
|--------|------|----------|-------|------------|-----------------------------|-------|

Recommended type values:

- `Query`: query criteria
- `Create`: create request
- `Update`: update request
- `Action`: status, batch, trigger, or command request

### 3.3 API Output Model (required; use `{empty_marker}` when no API output exists)

| Object | Type | Scenario | Field | Field type | Source | Notes |
|--------|------|----------|-------|------------|--------|-------|

Recommended type values:

- `Page`: paginated response data
- `ListItem`: list row
- `Detail`: detail response
- `ActionResult`: operation result

### 3.4 Service Model (conditionally required)

| Object | Scenario | Field | Field type | Notes |
|--------|----------|-------|------------|-------|

> Define this section when the Service layer needs independent command objects, aggregate objects, or context objects. Record each object's lifecycle once in Section 1 instead of repeating it per field.

### 3.5 Integration Model (conditionally required)

| Object | Integration target | Direction | Field | Field type | Conversion rule | Notes |
|--------|--------------------|-----------|-------|------------|-----------------|-------|

> Define this section for external systems, cross-module calls, workers/agents/masters, files, or messages.

### 3.6 State / Enum Model (conditionally required)

| Field / enum | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|

## 4. Frontend Data Model

### 4.1 API Client Model (required; use `{empty_marker}` when no frontend change exists)

| API | Request object | Response object | Response wrapper | Query key / cache | Notes |
|-----|----------------|-----------------|------------------|-------------------|-------|

### 4.2 Page Query Model (required; use `{empty_marker}` when no query page exists)

| Field | Type | Default | Source | Maps to API | Notes |
|-------|------|---------|--------|-------------|-------|

### 4.3 Page Display Model (required; use `{empty_marker}` when no display page exists)

| Object | Scenario | Field | Type | Source | Formatting / display rule | Notes |
|--------|----------|-------|------|--------|---------------------------|-------|

### 4.4 Form Model (conditionally required)

| Form | Field | Type | Default | Validation rule | Maps to API | Notes |
|------|-------|------|---------|-----------------|-------------|-------|

> Define this section for create, edit, copy, import, or save workflows.

### 4.5 Action Model (conditionally required)

| Action | Trigger location | Payload | Success behavior | Failure behavior | Notes |
|--------|------------------|---------|------------------|------------------|-------|

> Define this section for row actions, batch actions, status actions, log actions, or detail actions.

### 4.6 State Model (conditionally required)

| State | Type | View / component | Default | Change source | Notes |
|-------|------|------------------|---------|---------------|-------|

> Define this section for tabs, expansion state, selection, modal/dialog state, drawers, canvases, polling, or cache state.

## 5. Data Mapping Rules

### 5.1 API Mapping

| API | Request object | Response data | Response wrapper | Notes |
|-----|----------------|---------------|------------------|-------|

### 5.2 Layer Conversion

| Direction | Conversion rule | Special handling |
|-----------|-----------------|------------------|

Example directions:

- Form Model -> API Request
- Request DTO -> Entity/PO
- Update DTO -> Existing Entity/PO
- Entity/PO -> Response DTO
- Response DTO -> Frontend Display Model
- External response -> Internal model

## 6. Reused Structures

| Object | Path | Reuse method | Notes |
|--------|------|--------------|-------|

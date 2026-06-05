# {feature} Data Definition

> This document is the single source of truth for Database, Java Backend, and Frontend data structures. Design documents explain flow, behavior, and constraints without repeating full field definitions.

> Simple Mode: keep only sections involved in the change; use the configured empty marker for required-but-not-applicable sections. Full Mode: expand Database, Backend, Frontend, Mapping, and Reused Structures.

## Contents

- [1. Database Data Model](#1-database-data-model)
- [2. Java Backend Data Model](#2-java-backend-data-model)
- [3. Frontend Data Model](#3-frontend-data-model)
- [4. Data Mapping Rules](#4-data-mapping-rules)
- [5. Reused Structures](#5-reused-structures)

## 1. Database Data Model

### 1.1 Table Model (required; use `{empty_marker}` when no database change exists)

- Table name: `{table_name}`
- Operation: create / update / not involved
- Primary key (must be explicit and defined in DDL):
- Candidate key / unique business key (must be explicit; use `{empty_marker}` when absent; define as unique constraint or unique index in DDL when present):
- Partition strategy (must be explicit; use `{empty_marker}` when absent; define in DDL when present):
- Table comment (must be explicit and defined in DDL):
- Notes:

> Database models must explicitly define primary keys, candidate keys, partition strategy, and comments. When candidate keys or partitions do not apply, write the configured empty marker instead of omitting them.

### 1.2 DDL / Migration (conditionally required)

```sql
-- CREATE TABLE / ALTER TABLE / index change; use the configured empty marker when no database change exists.
-- CREATE TABLE DDL must directly define:
-- 1. PRIMARY KEY
-- 2. UNIQUE KEY / UNIQUE INDEX for candidate or unique business keys
-- 3. partition strategy, when applicable
-- 4. table COMMENT and column COMMENT
```

### 1.3 Field / Column Model (required; use `{empty_marker}` when no database model exists)

| DB column | DB type | Java field | Java type | Required | Default | Constraint / index | DB comment | Notes |
|-----------|---------|------------|-----------|----------|---------|--------------------|------------|-------|

> If audit fields are inherited from project base entities, do not duplicate them in the Entity/PO, but keep them explicit in DDL.
> Column comments must be explicit and defined in DDL; comments should describe business meaning.

### 1.4 Seed / Initial Data (conditionally required)

| Data | Scenario | Initialization method | Notes |
|------|----------|-----------------------|-------|

## 2. Java Backend Data Model

### 2.1 Persistence Model (required; use `{empty_marker}` when no persistence model exists)

| Java field | DB column | Java type | Annotation / type handler | Notes |
|------------|-----------|-----------|---------------------------|-------|

### 2.2 API Input Model (required; use `{empty_marker}` when no API input exists)

| Object | Type | Scenario | Field | Field type | Validation / query behavior | Notes |
|--------|------|----------|-------|------------|-----------------------------|-------|

Recommended type values:

- `Query`: query criteria
- `Create`: create request
- `Update`: update request
- `Action`: status, batch, trigger, or command request

### 2.3 API Output Model (required; use `{empty_marker}` when no API output exists)

| Object | Type | Scenario | Field | Field type | Source | Notes |
|--------|------|----------|-------|------------|--------|-------|

Recommended type values:

- `Page`: paginated response data
- `ListItem`: list row
- `Detail`: detail response
- `ActionResult`: operation result

### 2.4 Service Model (conditionally required)

| Object | Scenario | Field | Field type | Lifecycle | Notes |
|--------|----------|-------|------------|-----------|-------|

> Define this section when the Service layer needs independent command objects, aggregate objects, or context objects.

### 2.5 Integration Model (conditionally required)

| Object | Integration target | Direction | Field | Field type | Conversion rule | Notes |
|--------|--------------------|-----------|-------|------------|-----------------|-------|

> Define this section for external systems, cross-module calls, workers/agents/masters, files, or messages.

### 2.6 State / Enum Model (conditionally required)

| Field / enum | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|

## 3. Frontend Data Model

### 3.1 API Client Model (required; use `{empty_marker}` when no frontend change exists)

| API | Request object | Response object | Response wrapper | Query key / cache | Notes |
|-----|----------------|-----------------|------------------|-------------------|-------|

### 3.2 Page Query Model (required; use `{empty_marker}` when no query page exists)

| Field | Type | Default | Source | Maps to API | Notes |
|-------|------|---------|--------|-------------|-------|

### 3.3 Page Display Model (required; use `{empty_marker}` when no display page exists)

| Object | Scenario | Field | Type | Source | Formatting / display rule | Notes |
|--------|----------|-------|------|--------|---------------------------|-------|

### 3.4 Form Model (conditionally required)

| Form | Field | Type | Default | Validation rule | Maps to API | Notes |
|------|-------|------|---------|-----------------|-------------|-------|

> Define this section for create, edit, copy, import, or save workflows.

### 3.5 Action Model (conditionally required)

| Action | Trigger location | Payload | Success behavior | Failure behavior | Notes |
|--------|------------------|---------|------------------|------------------|-------|

> Define this section for row actions, batch actions, status actions, log actions, or detail actions.

### 3.6 State Model (conditionally required)

| State | Type | View / component | Default | Change source | Notes |
|-------|------|------------------|---------|---------------|-------|

> Define this section for tabs, expansion state, selection, modal/dialog state, drawers, canvases, polling, or cache state.

## 4. Data Mapping Rules

### 4.1 API Mapping

| API | Request object | Response data | Response wrapper | Notes |
|-----|----------------|---------------|------------------|-------|

### 4.2 Layer Conversion

| Direction | Conversion rule | Special handling |
|-----------|-----------------|------------------|

Example directions:

- Form Model -> API Request
- Request DTO -> Entity/PO
- Update DTO -> Existing Entity/PO
- Entity/PO -> Response DTO
- Response DTO -> Frontend Display Model
- External response -> Internal model

## 5. Reused Structures

| Object | Path | Reuse method | Notes |
|--------|------|--------------|-------|

# {feature} Design

> Data structures, owners, and lifecycle boundaries are defined in [{feature}-data-define.md](./{feature}-data-define.md). Do not repeat full definitions here; describe how data flows, crosses declared boundaries, changes, validates, persists, displays, and gets operated on.

> Simple Mode: keep only sections involved in the change. Full Mode: expand this document when the feature crosses database, Java backend, frontend, and external integrations.

> Do not finalize this design or begin implementation while the data definition contains unresolved lifecycle clarifications.

## Contents

- [1. Capability](#1-capability)
- [2. Data Flow](#2-data-flow)
- [3. API Contract](#3-api-contract)
- [4. File Changes](#4-file-changes)
- [5. Java Backend Design](#5-java-backend-design)
- [6. Frontend Design](#6-frontend-design)
- [7. Integration](#7-integration)
- [8. Security and Context](#8-security-and-context)
- [9. Out of Scope](#9-out-of-scope)
- [10. Verification](#10-verification)

## 1. Capability

- Capability:
- Module:
- Java backend package:
- Frontend path:
- Route / API prefix:
- Call chain:

## 2. Data Flow

| Scenario | Source | Through | Target | Structure transitions | Boundary / lifecycle check | Notes |
|----------|--------|---------|--------|-----------------------|----------------------------|-------|

> Describe how data moves across page, API client, Controller, Service, Mapper/DAO, database, and external systems. Show every reuse or conversion at a layer boundary, for example `OrderForm -> CreateOrderRequest -> CreateOrderCommand -> OrderPO`. Reference the lifecycle map rather than redefining it. If a flow exceeds a declared valid scope, return to the data definition and resolve the boundary before finalizing the design.

## 3. API Contract

| HTTP method | Path | Request object | Response object | Notes |
|-------------|------|----------------|-----------------|-------|

> Use the configured empty marker when there is no API change.

## 4. File Changes

### 4.1 New Files

| File | Notes |
|------|-------|

### 4.2 Modified Files

| File | Notes |
|------|-------|

### 4.3 Reused Objects

| Object | Path | Notes |
|--------|------|-------|

## 5. Java Backend Design

### 5.1 Controller

| Method | Input | Output | Notes |
|--------|-------|--------|-------|

### 5.2 Service

| Method | Input | Output | Notes |
|--------|-------|--------|-------|

### 5.3 Business Rules

| Scenario | Rule | Error / return |
|----------|------|----------------|

### 5.4 Transaction Boundary

- Needs transaction:
- Transactional method:
- Rollback condition:

### 5.5 Mapper / DAO / SQL

| Method | Type | SQL source | Notes |
|--------|------|------------|-------|

> For user input, use parameter binding or prepared statements. In MyBatis projects, use `#{}` and never concatenate user input into SQL.

## 6. Frontend Design

### 6.1 Page Structure

| Page / component | Responsibility | Input data | Output event | Notes |
|------------------|----------------|------------|--------------|-------|

### 6.2 Interaction Behavior

| Scenario | Trigger | Data change | API call | Result handling |
|----------|---------|-------------|----------|-----------------|

### 6.3 Routing / Menu

| Route / menu | Path | Component | Permission / display rule | Notes |
|--------------|------|-----------|---------------------------|-------|

## 7. Integration

| Integration target | Method | Notes |
|--------------------|--------|-------|

> Define external systems, cross-module calls, schedulers, workers, caches, object storage, repositories, search engines, message queues, or data platforms when involved.

## 8. Security and Context

- Current user:
- Tenant / project / app context:
- Password / token handling:
- Permission boundary:

## 9. Out of Scope

-

## 10. Verification

- Unit tests:
- Compile / build command:
- Frontend verification:
- Style / lint:
- Manual check:

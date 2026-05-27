# metadata-datasource

Frontend module for `DataSourceInfoController` (`/api/metadata/datasource`).

This module is the reference shape for controller-aligned frontend modules. The
directory stays flat at `src/modules/metadata-datasource` so it can map directly
to the backend controller instead of nesting by broad domains first.

## Structure

```text
metadata-datasource/
  README.md
  api.ts
  dto.ts
  constants.ts
  index.tsx
  components/
    list-table/
      index.tsx
      columns.tsx
      filters.tsx
      toolbar.tsx
      pagination.ts
      use-list-query.ts
    form-drawer/
      index.tsx
      json-params.tsx
      use-submit.ts
    register-modal/
      index.tsx
      use-register.ts
```

## Naming

- Component folders use `purpose + presentation`, for example `list-table`, `form-drawer`, and `register-modal`.
- CRUD/query behavior is expressed inside hooks such as `use-list-query`, `use-submit`, and `use-register`.
- `index.tsx` is the page orchestration layer. It wires page-level state and module components together.
- `api.ts` maps the backend controller endpoints.
- `dto.ts` keeps request, response, row, enum, and page DTOs for this module.
- `constants.ts` keeps options, default filters, page sizes, and local demo data.

## Runtime Pattern

- List data is fetched with TanStack Query. Mutations invalidate the shared `METADATA_DATASOURCE_QUERY_KEY` instead of passing manual refresh props.
- Development-only demo fallback uses the project-level environment object (`env.DEV`) from `@/env`; module code should not read `import.meta.env` directly.
- Backend field names are preserved in DTOs so request and response contracts stay easy to compare with `DataSourceInfoController`.

## Extension Guide

When adding another controller-aligned module, prefer the same shape:

```text
src/modules/{controller-name}/
  api.ts
  dto.ts
  constants.ts
  index.tsx
  components/
    list-table/
    form-drawer/
```

Add extra component folders only when the interaction has a distinct UI form,
such as `register-modal`, `detail-drawer`, or `import-modal`.

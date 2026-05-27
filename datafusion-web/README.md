# DataFusion Web

This module is an open-source replacement scaffold for the private-package based `data-warehouse-view` frontend.

## Private Package Replacements

| Old private package | Open-source replacement in this module |
| --- | --- |
| `@gw/gw-scripts` | Vite |
| `@gw/gw-request` | Axios + TanStack Query |
| `@gw/web-basic-components` | Ant Design |
| `@gw/web-business-components` | Ant Design + local reusable pages/components |
| `@gw/css` | CSS variables/global CSS + Ant Design theme tokens |
| `@gw/gw-utils` | React Router APIs + local helpers |
| `@gw/hooks` | React hooks + TanStack Query |
| `@gw/odm-db` | `.env` files and Vite env variables |
| `@gw/types` | Local TypeScript types under `src/types` |
| `@gw/gw-commit` | Standard npm scripts/git workflow |
| `@gw/eslint-config-gw` | ESLint flat config with TypeScript ESLint |
| `@gw/stylelint-config-gw` | Plain CSS for now; add Stylelint later if needed |
| `@gw/gw-sentry` | Not wired yet; use `@sentry/react` if error reporting is required |

## Commands

```bash
npm install
npm run dev
npm run build
npm run preview
```

Use `.env.local` to point the development proxy at a backend:

```bash
VITE_API_TARGET=http://localhost:8080
```

## Migration Shape

The scaffold keeps the original business map from `data-warehouse-view`:

- metrics: unified metric, warehouse metric, metric registration, metric invocation
- metadata: datasource, table structure, table sync
- asset: table lineage, business lineage, resource import
- scheduler: trigger, variable, flow, task, event
- datastudio: data integration and data development

Most table-like pages use `DataTablePage` as a temporary migration shell. Move each original page in this order:

1. Copy `api.ts` endpoint definitions into a typed local API module using `src/api/http.ts`.
2. Copy DTOs into local page `types.ts` or `dto.ts`.
3. Replace `GWTableProPlus` configs with Ant Design `Table`, `Form`, `Input`, `Select`, `Drawer`, and `Modal`.
4. Replace graph/lineage views with `@xyflow/react`, ECharts, or AntV G6.
5. Replace `message`, `Modal`, and `Button` imports with Ant Design equivalents.

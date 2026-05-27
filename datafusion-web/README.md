# DataFusion Web

本模块是基于私有包的 `data-warehouse-view` 前端的开源替代脚手架。

## 私有包替换对照

| 原私有包 | 本模块中的开源替代 |
| --- | --- |
| `@gw/gw-scripts` | Vite |
| `@gw/gw-request` | Axios + TanStack Query |
| `@gw/web-basic-components` | Ant Design |
| `@gw/web-business-components` | Ant Design + 本地可复用页面/组件 |
| `@gw/css` | CSS 变量/全局 CSS + Ant Design 主题 Token |
| `@gw/gw-utils` | React Router API + 本地工具函数 |
| `@gw/hooks` | React Hooks + TanStack Query |
| `@gw/odm-db` | `.env` 文件和 Vite 环境变量 |
| `@gw/types` | `src/types` 下的本地 TypeScript 类型 |
| `@gw/gw-commit` | 标准 npm scripts / git 工作流 |
| `@gw/eslint-config-gw` | ESLint Flat Config + TypeScript ESLint |
| `@gw/stylelint-config-gw` | 暂用原生 CSS；如需要后续可添加 Stylelint |
| `@gw/gw-sentry` | 尚未接入；如需错误上报可使用 `@sentry/react` |

## 命令

```bash
npm install
npm run dev
npm run build
npm run preview
```

使用 `.env.local` 将开发代理指向后端服务：

```bash
VITE_API_TARGET=http://localhost:8080
```

## 业务模块

脚手架保留了 `data-warehouse-view` 原有的业务地图：

- **指标中心**（metrics）：统一指标、数仓指标维护、指标注册、指标调用
- **元数据管理**（metadata）：数据源管理、表结构管理、表结构同步
- **数据资产**（asset）：表级血缘、业务血缘、血缘资源导入
- **调度中心**（scheduler）：调度器配置、变量配置、流程管理、任务管理、事件管理
- **数据开发**（datastudio）：数据集成、数据开发

大多数表格类页面使用 `DataTablePage` 作为临时迁移壳。按以下顺序逐页迁移：

1. 将 `api.ts` 端点定义复制为基于 `src/api/http.ts` 的类型化本地 API 模块。
2. 将 DTO 复制到本地页面的 `types.ts` 或 `dto.ts`。
3. 用 Ant Design 的 `Table`、`Form`、`Input`、`Select`、`Drawer`、`Modal` 替换 `GWTableProPlus` 配置。
4. 用 `@xyflow/react`、ECharts 或 AntV G6 替换图谱/血缘视图。
5. 用 Ant Design 等价物替换 `message`、`Modal`、`Button` 导入。

## 模块开发规范

本节定义了与后端 Controller 对齐的前端模块的标准开发范式，以 `metadata-datasource` 模块为参考。

### 目录结构

模块目录保持扁平结构，位于 `src/modules/{controller-name}`，直接映射到后端 Controller，不按大的业务域做嵌套：

```text
src/modules/{controller-name}/
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
    register-modal/        # 仅当交互具有独立 UI 形式时才添加
      index.tsx
      use-register.ts
```

### 命名规范

- 组件文件夹采用 **用途 + 展示形式** 的命名方式，例如 `list-table`（列表表格）、`form-drawer`（表单抽屉）和 `register-modal`（登记弹窗）。
- CRUD/查询行为封装在自定义 Hook 中，如 `use-list-query`、`use-submit` 和 `use-register`。
- `index.tsx` 是页面的编排层，负责将页面级状态与模块内的组件组装在一起。
- `api.ts` 映射后端 Controller 的接口端点。
- `dto.ts` 存放本模块的请求、响应、行数据、枚举和分页等数据传输对象。
- `constants.ts` 存放选项配置、默认筛选条件、分页大小和本地演示数据。

### 运行时模式

- 列表数据通过 TanStack Query 获取。变更操作（Mutation）通过使共享的模块级 Query Key（如 `METADATA_DATASOURCE_QUERY_KEY`）失效来触发刷新，而非手动传递刷新回调。
- 仅用于开发的演示数据降级方案使用项目级环境对象（`env.DEV`，来自 `@/env`）；模块代码不应直接读取 `import.meta.env`。
- DTO 中保留后端的原始字段命名，使请求和响应契约与后端 Controller 保持一致，便于对照。

### 扩展指南

当新增与 Controller 对齐的模块时，采用相同的目录结构：

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

仅当交互具有独立的 UI 形式时才添加额外的组件文件夹，例如 `register-modal`、`detail-drawer` 或 `import-modal`。

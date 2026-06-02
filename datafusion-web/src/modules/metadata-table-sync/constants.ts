import type { TableSyncItem, TableSyncListOption } from "./dto";

export const METADATA_TABLE_SYNC_QUERY_KEY = "metadata-table-sync";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: TableSyncListOption = {
  sourceDataSourceName: "",
  sourceDatabaseName: "",
  targetDataSourceName: "",
  targetDatabaseName: "",
  operateType: undefined,
};

export const operateTypeOptions = [
  { label: "批量创建", value: 0 },
  { label: "批量同步", value: 1 },
];

export const demoTableSyncRows: TableSyncItem[] = [
  {
    id: "sync-1",
    sourceDataSourceName: "ods_mysql_prod",
    sourceDatabaseName: "ods",
    targetDataSourceName: "dw_starrocks",
    targetDatabaseName: "warehouse",
    operateType: 0,
    operateTime: "2026-05-25 14:00:00",
  },
];

import type { TableStructureItem, TableStructureListOption } from "./dto";

export const METADATA_TABLE_STRUCTURE_QUERY_KEY = "metadata-table-structure";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: TableStructureListOption = {
  databaseConnectName: "",
  databaseName: "",
  databaseType: undefined,
  schemaName: "",
  tableName: "",
  tableDesc: "",
  isModify: undefined,
  isView: undefined,
  isEqual: undefined,
};

export const booleanOptions = [
  { label: "是", value: true },
  { label: "否", value: false },
];

export const demoTableStructureRows: TableStructureItem[] = [
  {
    id: "table-1",
    datasourceId: "ds-2",
    databaseConnectName: "dw_starrocks",
    databaseName: "warehouse",
    databaseType: "STARROCKS",
    schemaName: "analytics",
    tableName: "dim_product",
    tableDesc: "产品维表",
    checkTime: "2026-05-24 10:30:00",
    isModify: true,
    isView: false,
    isEqual: true,
  },
  {
    id: "table-2",
    datasourceId: "ds-1",
    databaseConnectName: "ods_mysql_prod",
    databaseName: "ods",
    databaseType: "MYSQL",
    schemaName: "public",
    tableName: "ods_order",
    tableDesc: "订单原始表",
    checkTime: "2026-05-24 11:10:00",
    isModify: false,
    isView: false,
    isEqual: false,
  },
];

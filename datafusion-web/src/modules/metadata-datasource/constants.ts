import type { DatasourceItem, DatasourcePageOption } from "./dto";

export const METADATA_DATASOURCE_QUERY_KEY = "metadata-datasource";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: DatasourcePageOption = {
  name: "",
  databaseType: undefined,
  databaseName: "",
  schemaName: "",
};

export const databaseTypeOptions = [
  "MYSQL",
  "POSTGRES",
  "STARROCKS",
  "HIVE",
  "ORACLE",
  "SQLSERVER",
  "DM",
].map((value) => ({ label: value, value }));

export const demoDatasourceRows: DatasourceItem[] = [
  {
    id: "ds-1",
    name: "ods_mysql_prod",
    databaseName: "ods",
    databaseType: "MYSQL",
    schemaName: "public",
    host: "10.0.12.8",
    port: 3306,
    syncCount: 128,
    username: "reader",
  },
  {
    id: "ds-2",
    name: "dw_starrocks",
    databaseName: "warehouse",
    databaseType: "STARROCKS",
    schemaName: "analytics",
    host: "10.0.18.21",
    port: 9030,
    syncCount: 76,
    username: "etl",
  },
];

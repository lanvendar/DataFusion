export enum PageActionEnum {
  VIEW = "VIEW",
  BATCH_CREATE = "BATCH_CREATE",
  BATCH_COMPARE = "BATCH_COMPARE",
}

export type SyncMode = "create" | "compare";

export interface DatasourceOptionItem {
  id: string;
  name: string;
  databaseName?: string;
  databaseType?: string;
  schemaName?: string;
}

export interface TableInfoItem {
  tableName: string;
  name?: string;
  registered?: boolean;
}

export interface TableSyncItem {
  id: string;
  operateType: number;
  sourceDatasourceId?: string;
  targetDatasourceId?: string;
  sourceDatabaseName?: string;
  targetDatabaseName?: string;
  sourceSchemaName?: string;
  targetSchemaName?: string;
  sourceDataSourceName?: string;
  targetDataSourceName?: string;
  operateTime?: string;
  snapshotStep1?: Record<string, unknown>;
  snapshotStep2?: Record<string, unknown>;
  snapshotStep3?: Record<string, unknown>;
}

export interface TableSyncListOption {
  sourceDataSourceName?: string;
  sourceDatabaseName?: string;
  targetDataSourceName?: string;
  targetDatabaseName?: string;
  operateType?: number;
  operateTime?: string;
}

export interface TableSyncListReq {
  size?: number;
  current?: number;
  option?: TableSyncListOption;
}

export interface TableSyncListRes {
  dataList: TableSyncItem[];
  total: number;
  current?: number;
  size?: number;
}

export interface TableSyncSubmitReq {
  trackId?: string;
  sourceDatasourceId?: string;
  targetDatasourceId?: string;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
  tableNames?: string[];
  tableMapping?: Record<string, string>;
  defaultColumns?: string[];
}

export interface TableCompareResultItem {
  sourceDataSourceId?: string;
  sourceTableName?: string;
  mappingTableName?: string;
  targetDataSourceId?: string;
  targetTableName?: string;
  sourceTableColumnNums?: number;
  targetTableColumnNums?: number;
  compareResult?: string;
}

export interface DefaultColumnItem {
  key?: string;
  value?: string;
  label?: string;
  name?: string;
}

export interface SyncDrawerValues {
  sourceDatasourceId: string;
  targetDatasourceId: string;
  tableNames: string[];
  defaultColumns?: string[];
}

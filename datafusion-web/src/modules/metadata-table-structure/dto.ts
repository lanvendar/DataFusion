export enum PageActionEnum {
  ADD = "ADD",
  VIEW = "VIEW",
  EDIT = "EDIT",
  DELETE = "DELETE",
  UPDATE_STRUCTURE = "UPDATE_STRUCTURE",
  BATCH_UPDATE_STRUCTURE = "BATCH_UPDATE_STRUCTURE",
}

export interface DatasourceOptionItem {
  id: string;
  name: string;
  databaseName?: string;
  databaseType?: string;
  schemaName?: string;
}

export interface TableStructureItem {
  id: string;
  datasourceId?: string;
  databaseConnectName?: string;
  databaseName?: string;
  databaseType?: string;
  schemaName?: string;
  tableName: string;
  tableDesc?: string;
  indexKeys?: string;
  partitionKeys?: string;
  bucketKeys?: string;
  primaryKeys?: string;
  compressionRules?: string;
  tableProperties?: Record<string, unknown>;
  isModify?: boolean;
  isView?: boolean;
  viewDef?: string;
  tableType?: string;
  checkTime?: string;
  createTime?: string;
  updateTime?: string;
  isEqual?: boolean;
}

export interface TableStructureListOption {
  datasourceId?: string;
  schemaName?: string;
  databaseConnectName?: string;
  databaseName?: string;
  tableName?: string;
  tableDesc?: string;
  isModify?: boolean;
  isView?: boolean;
  isEqual?: boolean;
  databaseType?: string;
}

export interface TableStructureListReq {
  size?: number;
  current?: number;
  option?: TableStructureListOption;
}

export interface TableStructureListRes {
  dataList: TableStructureItem[];
  total: number;
  current?: number;
  size?: number;
}

export interface TableStructureSaveReq {
  id?: string;
  datasourceId?: string;
  databaseConnectName?: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  tableDesc?: string;
  tableDirectory?: string;
  tableProperties?: Record<string, unknown>;
  isModify?: boolean;
  isView?: boolean;
  viewDef?: string;
}

export interface TableStructureRegisterReq {
  datasourceId: string;
  tableNames: string[];
}

export interface ColumnInfoDto {
  id: string;
  tableId: string;
  tableName?: string;
  columnSerial?: number;
  columnName: string;
  columnDesc?: string;
  columnType?: string;
  columnLength?: number;
  columnPrecision?: number;
  columnScale?: number;
  isNotNull?: boolean;
  isPrimaryKey?: boolean;
  defaultValue?: string;
}

export interface ColumnInfoQueryDto {
  tableId: string;
  columnName?: string;
}

export interface MetaTableDataReq {
  tableId: string;
  queryConditions: string[];
  orderConditions: string[];
  limit: number;
}

export interface MetaTableDataHeader {
  title: string;
  field: string;
  columnName: string;
}

export interface MetaTableDataRes {
  header: MetaTableDataHeader[];
  data: Record<string, unknown>[];
}

export interface RowCountAndSizeRes {
  rowCount?: number;
  tableSize?: string;
  size?: string;
}

export type TableStructureFormMode = "add" | "edit";

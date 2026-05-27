export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
  COPY_ADD = "COPY_ADD",
  TABLE_REGISTER = "TABLE_REGISTER",
  REFRESH = "REFRESH",
}

export interface JsonParamItem {
  paramName: string;
  paramValue: string;
}

export interface DatasourceItem {
  id: string;
  name: string;
  databaseName: string;
  databaseType: string;
  schemaName: string;
  host: string;
  port: number;
  syncCount: number;
  username?: string;
  password?: string;
  extendParam?: Record<string, unknown> | string;
  jsonParams?: JsonParamItem[];
}

export interface DatasourcePageOption {
  name?: string;
  databaseType?: string;
  schemaName?: string;
  databaseName?: string;
}

export interface DatasourcePageReq {
  size?: number;
  current?: number;
  option?: DatasourcePageOption;
}

export interface DatasourcePageRes {
  dataList: DatasourceItem[];
  size: number;
  current: number;
  total: number;
}

export interface DatasourceSaveReq {
  id?: string;
  name: string;
  databaseName: string;
  databaseType: string;
  schemaName?: string;
  host: string;
  port: number;
  username?: string;
  password?: string;
  jsonParams?: JsonParamItem[];
}

export type DatasourceTestReq = DatasourceSaveReq;

export interface DatasourceTestRes {
  success?: boolean;
  message?: string;
}

export interface TableRegisterTableItem {
  tableName: string;
  registered?: boolean;
}

export type TableRegisterListRes = TableRegisterTableItem[];

export interface TableRegisterReq {
  datasourceId: string;
  tableNames: string[];
}

export type DatasourceFormMode = "add" | "edit" | "copy";

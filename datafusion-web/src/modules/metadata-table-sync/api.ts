import { request } from "@/api/http";
import type {
  DatasourceOptionItem,
  DefaultColumnItem,
  TableCompareResultItem,
  TableInfoItem,
  TableSyncItem,
  TableSyncListReq,
  TableSyncListRes,
  TableSyncSubmitReq,
} from "./dto";

const prefix = "/api/metadata";

export const tableSyncApi = {
  page(params: TableSyncListReq): Promise<TableSyncListRes> {
    return request<TableSyncListRes>({
      url: `${prefix}/page/operateLog`,
      method: "POST",
      data: params,
    });
  },

  detail(id: string): Promise<TableSyncItem> {
    return request<TableSyncItem>({ url: `${prefix}/operateLog/${id}`, method: "GET" });
  },

  datasourceList(option: Record<string, unknown> = {}): Promise<DatasourceOptionItem[]> {
    return request<DatasourceOptionItem[]>({
      url: `${prefix}/datasource/list`,
      method: "POST",
      data: option,
    });
  },

  getTableInfos(dataSourceId: string): Promise<TableInfoItem[]> {
    return request<TableInfoItem[]>({
      url: `${prefix}/getTableInfos/${dataSourceId}`,
      method: "GET",
    });
  },

  generateTrackId(operateType: number): Promise<string> {
    return request<string>({
      url: `${prefix}/generateTrackId/${operateType}`,
      method: "POST",
    });
  },

  defaultColumns(databaseType: string): Promise<DefaultColumnItem[]> {
    return request<DefaultColumnItem[]>({
      url: `${prefix}/defaultColumns/${databaseType}`,
      method: "GET",
    });
  },

  batchCreateTableCheck(params: TableSyncSubmitReq): Promise<TableCompareResultItem[]> {
    return request<TableCompareResultItem[]>({
      url: `${prefix}/batchCreateTableCheck`,
      method: "POST",
      data: params,
    });
  },

  batchCreateTableDdl(params: TableSyncSubmitReq): Promise<string> {
    return request<string>({ url: `${prefix}/batchCreateTableDdl`, method: "POST", data: params });
  },

  batchMetaCompare(params: {
    trackId?: string;
    source: { datasourceId: string; tableNames: string[] };
    target: { datasourceId: string; tableNames: string[] };
  }): Promise<TableCompareResultItem[]> {
    return request<TableCompareResultItem[]>({
      url: `${prefix}/batchMetaCompare`,
      method: "POST",
      data: params,
    });
  },

  generateTableCompareDdlSql(params: {
    trackId?: string;
    tableCompareResultList: TableCompareResultItem[];
  }): Promise<string> {
    return request<string>({
      url: `${prefix}/generateTableCompareDdlSql`,
      method: "POST",
      data: params,
    });
  },

  executeSql(params: { trackId?: string; sql: string }): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/executeSql`, method: "POST", data: params });
  },
};

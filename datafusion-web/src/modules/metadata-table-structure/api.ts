import { request } from "@/api/http";
import type {
  ColumnInfoDto,
  ColumnInfoQueryDto,
  DatasourceOptionItem,
  MetaTableDataReq,
  MetaTableDataRes,
  RowCountAndSizeRes,
  TableStructureItem,
  TableStructureListReq,
  TableStructureListRes,
  TableStructureRegisterReq,
  TableStructureSaveReq,
} from "./dto";

const prefix = "/api/metadata";

export const tableStructureApi = {
  page(params: TableStructureListReq): Promise<TableStructureListRes> {
    return request<TableStructureListRes>({
      url: `${prefix}/table/page`,
      method: "POST",
      data: params,
    });
  },

  add(params: TableStructureSaveReq): Promise<void> {
    return request<void>({ url: `${prefix}/table/add`, method: "POST", data: params });
  },

  update(params: TableStructureSaveReq): Promise<void> {
    return request<void>({ url: `${prefix}/table/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<TableStructureItem> {
    return request<TableStructureItem>({ url: `${prefix}/table/${id}`, method: "GET" });
  },

  delete(id: string): Promise<void> {
    return request<void>({ url: `${prefix}/table/${id}`, method: "DELETE" });
  },

  datasourceList(option: Record<string, unknown> = {}): Promise<DatasourceOptionItem[]> {
    return request<DatasourceOptionItem[]>({
      url: `${prefix}/datasource/list`,
      method: "POST",
      data: option,
    });
  },

  registerTables(params: TableStructureRegisterReq): Promise<void> {
    return request<void>({ url: `${prefix}/registerTables`, method: "POST", data: params });
  },

  columnList(params: ColumnInfoQueryDto): Promise<ColumnInfoDto[]> {
    return request<ColumnInfoDto[]>({ url: `${prefix}/column/list`, method: "POST", data: params });
  },

  getMetaTableData(params: MetaTableDataReq): Promise<MetaTableDataRes> {
    return request<MetaTableDataRes>({
      url: `${prefix}/getMetaTableData`,
      method: "POST",
      data: params,
    });
  },

  getRowCountAndSize(tableId: string): Promise<RowCountAndSizeRes> {
    return request<RowCountAndSizeRes>({
      url: `${prefix}/getRowCountAndSize/${tableId}`,
      method: "POST",
    });
  },
};

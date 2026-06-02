import { request } from "@/api/http";
import type {
  DatasourceItem,
  DatasourcePageReq,
  DatasourcePageRes,
  DatasourceSaveReq,
  DatasourceTestReq,
  DatasourceTestRes,
  TableRegisterListRes,
  TableRegisterReq,
} from "./dto";

const prefix = "/api/metadata";

export const datasourceApi = {
  async page(params: DatasourcePageReq): Promise<DatasourcePageRes> {
    return request<DatasourcePageRes>({
      url: `${prefix}/datasource/page`,
      method: "POST",
      data: params,
    });
  },

  async list(option: Record<string, unknown> = {}): Promise<DatasourceItem[]> {
    return request<DatasourceItem[]>({
      url: `${prefix}/datasource/list`,
      method: "POST",
      data: option,
    });
  },

  async add(params: DatasourceSaveReq): Promise<void> {
    await request({ url: `${prefix}/datasource/add`, method: "POST", data: params });
  },

  async update(params: DatasourceSaveReq): Promise<void> {
    await request({ url: `${prefix}/datasource/update`, method: "POST", data: params });
  },

  async testConnection(params: DatasourceTestReq): Promise<DatasourceTestRes> {
    return request<DatasourceTestRes>({
      url: `${prefix}/datasource/testConnect`,
      method: "POST",
      data: params,
    });
  },

  async delete(id: string): Promise<void> {
    await request({ url: `${prefix}/datasource/${id}`, method: "DELETE" });
  },

  async refresh(id: string): Promise<void> {
    await request({ url: `${prefix}/datasource/${id}`, method: "GET" });
  },

  async getTableInfos(dataSourceId: string): Promise<TableRegisterListRes> {
    return request<TableRegisterListRes>({
      url: `${prefix}/getTableInfos/${dataSourceId}`,
      method: "GET",
    });
  },

  async registerTables(params: TableRegisterReq): Promise<void> {
    await request({ url: `${prefix}/registerTables`, method: "POST", data: params });
  },
};

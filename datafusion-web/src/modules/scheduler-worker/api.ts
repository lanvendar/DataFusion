import { request } from "@/api/http";
import type {
  WorkerRegistryItem,
  WorkerRegistryPageReq,
  WorkerRegistryPageRes,
  WorkerRegistrySaveReq,
} from "./dto";

const prefix = "/api/scheduler/worker";

export const workerRegistryApi = {
  page(params: WorkerRegistryPageReq): Promise<WorkerRegistryPageRes> {
    return request<WorkerRegistryPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<WorkerRegistryItem[]> {
    return request<WorkerRegistryItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: WorkerRegistrySaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: WorkerRegistrySaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<WorkerRegistryItem> {
    return request<WorkerRegistryItem>({ url: `${prefix}/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/${id}`, method: "DELETE" });
  },
};

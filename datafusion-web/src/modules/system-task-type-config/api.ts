import { request } from "@/api/http";
import type {
  TaskTypeConfigItem,
  TaskTypeConfigPageReq,
  TaskTypeConfigPageRes,
  TaskTypeConfigSaveReq,
} from "./dto";

const prefix = "/api/system/task-type";

export const taskTypeConfigApi = {
  page(params: TaskTypeConfigPageReq): Promise<TaskTypeConfigPageRes> {
    return request<TaskTypeConfigPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<TaskTypeConfigItem[]> {
    return request<TaskTypeConfigItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: TaskTypeConfigSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: TaskTypeConfigSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<TaskTypeConfigItem> {
    return request<TaskTypeConfigItem>({ url: `${prefix}/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/${id}`, method: "DELETE" });
  },
};

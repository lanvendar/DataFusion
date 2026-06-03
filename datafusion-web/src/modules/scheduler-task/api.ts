import { request } from "@/api/http";
import type {
  TaskItem,
  TaskPageReq,
  TaskPageRes,
  TaskSaveReq,
} from "./dto";

const prefix = "/api/scheduler/task";

export const taskApi = {
  page(params: TaskPageReq): Promise<TaskPageRes> {
    return request<TaskPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<TaskItem[]> {
    return request<TaskItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: TaskSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: TaskSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<TaskItem> {
    return request<TaskItem>({ url: `${prefix}/detail/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/delete/${id}`, method: "POST" });
  },
};

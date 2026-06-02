import { request } from "@/api/http";
import type {
  VariableItem,
  VariablePageReq,
  VariablePageRes,
  VariableSaveReq,
} from "./dto";

const prefix = "/api/scheduler/variable";

export const variableApi = {
  page(params: VariablePageReq): Promise<VariablePageRes> {
    return request<VariablePageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<VariableItem[]> {
    return request<VariableItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: VariableSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: VariableSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<VariableItem> {
    return request<VariableItem>({ url: `${prefix}/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/${id}`, method: "DELETE" });
  },
};

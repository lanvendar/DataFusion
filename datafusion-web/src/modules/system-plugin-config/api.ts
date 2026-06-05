import { request } from "@/api/http";
import type {
  PluginConfigItem,
  PluginConfigPageReq,
  PluginConfigPageRes,
  PluginConfigSaveReq,
} from "./dto";

const prefix = "/api/system/plugin";

export const pluginConfigApi = {
  page(params: PluginConfigPageReq): Promise<PluginConfigPageRes> {
    return request<PluginConfigPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<PluginConfigItem[]> {
    return request<PluginConfigItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: PluginConfigSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  copy(params: PluginConfigSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/copy`, method: "POST", data: params });
  },

  update(params: PluginConfigSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<PluginConfigItem> {
    return request<PluginConfigItem>({ url: `${prefix}/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/${id}`, method: "DELETE" });
  },
};

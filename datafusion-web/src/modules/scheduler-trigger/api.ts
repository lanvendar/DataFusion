import { request } from "@/api/http";
import type {
  TriggerCronPreviewReq,
  TriggerCronPreviewRes,
  TriggerItem,
  TriggerPageReq,
  TriggerPageRes,
  TriggerSaveReq,
} from "./dto";

const prefix = "/api/scheduler/trigger";

export const triggerApi = {
  page(params: TriggerPageReq): Promise<TriggerPageRes> {
    return request<TriggerPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<TriggerItem[]> {
    return request<TriggerItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: TriggerSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: TriggerSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<TriggerItem> {
    return request<TriggerItem>({ url: `${prefix}/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/${id}`, method: "DELETE" });
  },

  previewCron(params: TriggerCronPreviewReq): Promise<TriggerCronPreviewRes> {
    return request<TriggerCronPreviewRes>({
      url: `${prefix}/cron/preview`,
      method: "POST",
      data: params,
    });
  },
};

import { request } from "@/api/http";
import type {
  EventItem,
  EventPageReq,
  EventPageRes,
  EventSaveReq,
} from "./dto";

const prefix = "/api/scheduler/event";

export const eventApi = {
  page(params: EventPageReq): Promise<EventPageRes> {
    return request<EventPageRes>({
      url: `${prefix}/page`,
      method: "POST",
      data: params,
    });
  },

  list(option: Record<string, unknown> = {}): Promise<EventItem[]> {
    return request<EventItem[]>({
      url: `${prefix}/list`,
      method: "POST",
      data: option,
    });
  },

  add(params: EventSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: EventSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<EventItem> {
    return request<EventItem>({ url: `${prefix}/detail/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/delete/${id}`, method: "POST" });
  },
};

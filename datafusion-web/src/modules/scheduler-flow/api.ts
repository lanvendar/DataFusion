import { request } from "@/api/http";
import type {
  EventListItem,
  FlowDagDto,
  FlowDagSaveReq,
  FlowItem,
  FlowPageReq,
  FlowPageRes,
  FlowPublishReq,
  FlowSaveReq,
  TaskListItem,
  TriggerListItem,
} from "./dto";

const prefix = "/api/scheduler/flow";

export const flowApi = {
  page(params: FlowPageReq): Promise<FlowPageRes> {
    return request<FlowPageRes>({ url: `${prefix}/page`, method: "POST", data: params });
  },

  list(option: Record<string, unknown> = {}): Promise<FlowItem[]> {
    return request<FlowItem[]>({ url: `${prefix}/list`, method: "POST", data: option });
  },

  add(params: FlowSaveReq): Promise<string> {
    return request<string>({ url: `${prefix}/add`, method: "POST", data: params });
  },

  update(params: FlowSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/update`, method: "POST", data: params });
  },

  detail(id: string): Promise<FlowItem> {
    return request<FlowItem>({ url: `${prefix}/detail/${id}`, method: "GET" });
  },

  delete(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/delete/${id}`, method: "POST" });
  },

  publish(params: FlowPublishReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/publish`, method: "POST", data: params });
  },

  unpublish(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/unpublish/${id}`, method: "POST" });
  },

  enable(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/enable/${id}`, method: "POST" });
  },

  disable(id: string): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/disable/${id}`, method: "POST" });
  },

  dagDetail(id: string): Promise<FlowDagDto> {
    return request<FlowDagDto>({ url: `${prefix}/dag/detail/${id}`, method: "GET" });
  },

  saveDag(params: FlowDagSaveReq): Promise<boolean> {
    return request<boolean>({ url: `${prefix}/dag/save`, method: "POST", data: params });
  },
};

export const schedulerFlowRelationApi = {
  triggers(): Promise<TriggerListItem[]> {
    return request<TriggerListItem[]>({ url: "/api/scheduler/trigger/list", method: "POST", data: {} });
  },

  events(): Promise<EventListItem[]> {
    return request<EventListItem[]>({ url: "/api/scheduler/event/list", method: "POST", data: {} });
  },

  tasks(option: Record<string, unknown> = {}): Promise<TaskListItem[]> {
    return request<TaskListItem[]>({ url: "/api/scheduler/task/list", method: "POST", data: option });
  },
};

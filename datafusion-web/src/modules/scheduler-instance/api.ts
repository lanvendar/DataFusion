import { request } from "@/api/http";
import type {
  EventInstanceItem,
  EventInstanceQueryOption,
  FlowInstanceItem,
  FlowInstanceTaskQuery,
  PageQuery,
  PageResponse,
  SchedulerInstanceQueryOption,
  TaskInstanceItem,
  TaskInstanceLogContent,
  TaskInstanceLogQuery,
} from "./dto";

const flowPrefix = "/api/scheduler/flow/instance";
const taskPrefix = "/api/scheduler/task/instance";
const eventPrefix = "/api/scheduler/event/instance";

export const flowInstanceApi = {
  page(params: PageQuery<SchedulerInstanceQueryOption>): Promise<PageResponse<FlowInstanceItem>> {
    return request<PageResponse<FlowInstanceItem>>({
      url: `${flowPrefix}/page`,
      method: "POST",
      data: params,
    });
  },

  detail(id: string): Promise<FlowInstanceItem> {
    return request<FlowInstanceItem>({ url: `${flowPrefix}/${id}`, method: "GET" });
  },
};

export const taskInstanceApi = {
  page(params: PageQuery<SchedulerInstanceQueryOption>): Promise<PageResponse<TaskInstanceItem>> {
    return request<PageResponse<TaskInstanceItem>>({
      url: `${taskPrefix}/page`,
      method: "POST",
      data: params,
    });
  },

  listByFlowInstance(params: FlowInstanceTaskQuery): Promise<TaskInstanceItem[]> {
    return request<TaskInstanceItem[]>({
      url: `${taskPrefix}/listByFlowInstance`,
      method: "POST",
      data: params,
    });
  },

  detail(id: string): Promise<TaskInstanceItem> {
    return request<TaskInstanceItem>({ url: `${taskPrefix}/${id}`, method: "GET" });
  },

  logContent(params: TaskInstanceLogQuery): Promise<TaskInstanceLogContent> {
    return request<TaskInstanceLogContent>({
      url: `${taskPrefix}/log/content`,
      method: "POST",
      data: params,
    });
  },
};

export const eventInstanceApi = {
  page(params: PageQuery<EventInstanceQueryOption>): Promise<PageResponse<EventInstanceItem>> {
    return request<PageResponse<EventInstanceItem>>({
      url: `${eventPrefix}/page`,
      method: "POST",
      data: params,
    });
  },

  detail(id: string): Promise<EventInstanceItem> {
    return request<EventInstanceItem>({ url: `${eventPrefix}/${id}`, method: "GET" });
  },
};

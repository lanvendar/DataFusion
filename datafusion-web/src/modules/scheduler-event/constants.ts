import type { EventItem, EventPageOption } from "./dto";

export const SCHEDULER_EVENT_QUERY_KEY = "scheduler-event";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: EventPageOption = {
  eventName: "",
  eventType: undefined,
  flowId: "",
  taskId: "",
};

export const eventTypeOptions = [
  { label: "TASK", value: "1" },
  { label: "FLOW", value: "2" },
];

export const demoEventRows: EventItem[] = [
  {
    id: "event-1",
    eventName: "订单同步完成",
    eventType: "1",
    taskId: "task-1",
    updater: "scheduler",
    updateTime: "2026-05-25 12:30:00",
  },
];

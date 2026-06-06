export type SchedulerInstanceViewType = "REALTIME" | "HISTORY";

export type TaskInstanceLogType = "LOG" | "ERROR" | "STATUS";

export type SchedulerInstanceActionType =
  | "SUBMIT"
  | "STOP"
  | "KILL"
  | "RESTART"
  | "ENFORCE_SUCCESS";

export interface SchedulerInstanceQueryOption {
  flowKeyword?: string;
  taskKeyword?: string;
  status?: string;
  viewType?: SchedulerInstanceViewType;
  scheduleStartTime?: number;
  scheduleEndTime?: number;
  startTime?: number;
  endTime?: number;
  finishStartTime?: number;
  finishEndTime?: number;
}

export interface FlowInstanceTaskQuery {
  flowInstanceId: string;
  viewType?: SchedulerInstanceViewType;
}

export interface SchedulerInstanceAvailableAction {
  actionType: SchedulerInstanceActionType;
  label: string;
  confirmRequired?: boolean;
}

export interface SchedulerInstanceActionRequest {
  flowInstanceId?: string;
  taskInstanceId?: string;
  actionType: SchedulerInstanceActionType;
}

export interface EventInstanceQueryOption {
  eventKeyword?: string;
  eventType?: string;
  flowInstanceId?: string;
  taskInstanceId?: string;
  effectStartTime?: number;
  effectEndTime?: number;
}

export interface PageQuery<T> {
  current?: number;
  size?: number;
  option?: T;
}

export interface PageResponse<T> {
  dataList?: T[];
  records?: T[];
  list?: T[];
  size: number;
  current: number;
  total: number;
}

export interface FlowInstanceItem {
  id: string;
  flowId: string;
  flowName: string;
  flowCode?: string;
  flowType?: string;
  status?: string;
  triggerId?: string;
  publishVersion?: number;
  scheduleTime?: number;
  startTime?: number;
  endTime?: number;
  duration?: number;
  flowDagSnapshot?: unknown;
  availableActions?: SchedulerInstanceAvailableAction[];
}

export interface TaskInstanceItem {
  id: string;
  flowInstanceId: string;
  taskId: string;
  taskType?: string;
  taskName: string;
  taskCode?: string;
  status?: string;
  startTime?: number;
  endTime?: number;
  costTime?: number;
  lastInstanceId?: string;
  nextInstanceId?: string;
  workerId?: string;
  workerResult?: unknown;
  workerResultText?: string;
  logPath?: string;
  availableActions?: SchedulerInstanceAvailableAction[];
}

export interface EventInstanceItem {
  id: string;
  eventId: string;
  eventName: string;
  eventType?: string;
  flowInstanceId?: string;
  taskInstanceId?: string;
  effectTime?: number;
  effectBeginTime?: number;
  effectEndTime?: number;
}

export interface TaskInstanceLogQuery {
  flowInstanceId: string;
  taskInstanceId: string;
  logType: TaskInstanceLogType;
  offset?: number;
  limit?: number;
}

export interface TaskInstanceLogContent {
  logType: TaskInstanceLogType;
  path?: string;
  content?: string;
  nextOffset?: number;
  hasMore?: boolean;
}

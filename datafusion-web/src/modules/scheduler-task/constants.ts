import type { TaskItem, TaskPageOption } from "./dto";

export const SCHEDULER_TASK_QUERY_KEY = "scheduler-task";

export const DEFAULT_PAGE_SIZE = 10;

export const EMPTY_PLACEHOLDER = "-";

export const defaultFilter: TaskPageOption = {
  taskName: "",
  taskCode: "",
  taskType: undefined,
  isBound: undefined,
  enabled: undefined,
  syncFlag: undefined,
};

export const taskTypeOptions = [
  { label: "DataX 任务", value: "DATAX" },
  { label: "Shell 任务", value: "SHELL" },
  { label: "SQL 任务", value: "SQL" },
];

export const boundOptions = [
  { label: "已绑定", value: true },
  { label: "未绑定", value: false },
];

export const enabledOptions = [
  { label: "已启用", value: true },
  { label: "未启用", value: false },
];

export const syncFlagOptions = [
  { label: "已同步", value: true },
  { label: "未同步", value: false },
];

export const taskTypeColorMap: Record<string, string> = {
  DATAX: "blue",
  SHELL: "green",
  SQL: "gold",
};

export const demoTaskRows: TaskItem[] = [
  {
    id: "task-1",
    taskName: "同步订单数据",
    taskCode: "sync_order_ods",
    taskTypeId: "DATAX",
    taskType: "DATAX",
    taskParam: "{\"vars\":{\"source\":{\"name\":\"source\",\"type\":\"IN\",\"value\":\"ods_order\"}}}",
    definition: "{\"writer\":\"dwd_order\"}",
    creator: "scheduler",
    updater: "scheduler",
    createTime: "2026-05-25 12:00:00",
    updateTime: "2026-05-25 12:00:00",
  },
];

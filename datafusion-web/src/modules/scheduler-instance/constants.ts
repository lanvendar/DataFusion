import type { SchedulerInstanceQueryOption } from "./dto";

export const SCHEDULER_INSTANCE_FLOW_QUERY_KEY = "scheduler-instance-flow";
export const SCHEDULER_INSTANCE_TASK_QUERY_KEY = "scheduler-instance-task";
export const SCHEDULER_INSTANCE_LOG_QUERY_KEY = "scheduler-instance-log";
export const DEFAULT_PAGE_SIZE = 10;
export const DEFAULT_LOG_LIMIT = 64 * 1024;
export const EMPTY_PLACEHOLDER = "-";

export const EXPAND_COLUMN_WIDTH = 56;
export const INSTANCE_COLUMN_WIDTH = 360;
export const TYPE_COLUMN_WIDTH = 120;
export const STATUS_COLUMN_WIDTH = 120;
export const MIDDLE_COLUMN_WIDTH = 220;
export const TIME_COLUMN_WIDTH = 220;
export const ACTION_COLUMN_WIDTH = 280;
export const TABLE_SCROLL_X = EXPAND_COLUMN_WIDTH
  + INSTANCE_COLUMN_WIDTH
  + TYPE_COLUMN_WIDTH
  + STATUS_COLUMN_WIDTH
  + MIDDLE_COLUMN_WIDTH
  + TIME_COLUMN_WIDTH
  + ACTION_COLUMN_WIDTH;
export const CHILD_GRID_TEMPLATE = `${INSTANCE_COLUMN_WIDTH}px ${TYPE_COLUMN_WIDTH}px ${STATUS_COLUMN_WIDTH}px `
  + `${MIDDLE_COLUMN_WIDTH}px ${TIME_COLUMN_WIDTH}px ${ACTION_COLUMN_WIDTH}px`;
export const EXPANDED_TASK_GRID_TEMPLATE = `${EXPAND_COLUMN_WIDTH}px ${CHILD_GRID_TEMPLATE}`;

export const defaultFilter: SchedulerInstanceQueryOption = {
  flowKeyword: undefined,
  taskKeyword: undefined,
  status: undefined,
  viewType: "REALTIME",
  scheduleStartTime: undefined,
  scheduleEndTime: undefined,
  startTime: undefined,
  endTime: undefined,
  finishStartTime: undefined,
  finishEndTime: undefined,
};

export const viewTypeOptions = [
  { label: "实时", value: "REALTIME" },
  { label: "历史", value: "HISTORY" },
];

export const statusOptions = [
  { label: "初始化中", value: "00" },
  { label: "初始化成功", value: "01" },
  { label: "初始化失败", value: "02" },
  { label: "等待依赖", value: "11" },
  { label: "等待资源", value: "14" },
  { label: "提交中", value: "20" },
  { label: "提交成功", value: "21" },
  { label: "提交失败", value: "22" },
  { label: "运行中", value: "30" },
  { label: "运行成功", value: "31" },
  { label: "运行失败", value: "32" },
  { label: "强制成功", value: "33" },
  { label: "强制成功中", value: "34" },
  { label: "停止中", value: "40" },
  { label: "停止成功", value: "41" },
  { label: "停止失败", value: "42" },
  { label: "强制停止中", value: "43" },
  { label: "已强制停止", value: "44" },
  { label: "重启中", value: "50" },
  { label: "失败转移", value: "61" },
  { label: "重新加载中", value: "70" },
  { label: "未知", value: "99" },
];

export const statusColorMap: Record<string, string> = {
  "00": "default",
  "01": "default",
  "02": "red",
  "11": "gold",
  "14": "gold",
  "20": "processing",
  "21": "blue",
  "22": "red",
  "30": "processing",
  "31": "success",
  "32": "red",
  "33": "green",
  "34": "processing",
  "40": "orange",
  "41": "orange",
  "42": "red",
  "43": "orange",
  "44": "red",
  "50": "processing",
  "61": "orange",
  "70": "processing",
  "99": "default",
};

export const logTypeOptions = [
  { label: "标准日志", value: "LOG" },
  { label: "错误日志", value: "ERROR" },
  { label: "状态日志", value: "STATUS" },
  { label: "插件日志", value: "PLUGIN" },
];

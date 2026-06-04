import type { FlowItem, FlowPageOption } from "./dto";

export const SCHEDULER_FLOW_QUERY_KEY = "scheduler-flow";
export const DEFAULT_PAGE_SIZE = 10;
export const EMPTY_PLACEHOLDER = "-";

export const flowTypeOptions = [
  { label: "Stream 流任务", value: "1" },
  { label: "Batch 批任务", value: "2" },
];

export const flowTypeLabelMap: Record<string, string> = {
  "1": "Stream 流任务",
  "2": "Batch 批任务",
};

export const flowTypeColorMap: Record<string, string> = {
  "1": "green",
  "2": "blue",
};

export const enabledOptions = [
  { label: "调度中", value: true },
  { label: "未调度", value: false },
];

export const publishStateOptions = [
  { label: "已发布", value: true },
  { label: "未发布", value: false },
];

export const defaultFilter: FlowPageOption = {
  flowName: undefined,
  flowType: undefined,
  enabled: undefined,
  publishState: undefined,
};

export const demoRows: FlowItem[] = [
  {
    id: "flow-demo-1",
    flowName: "每日订单加工",
    flowCode: "daily_order_dw",
    flowType: "2",
    triggerName: "每日凌晨调度",
    enabled: true,
    publishState: true,
    description: "ODS 到 DWD 的订单加工链路",
    updateTime: "2026-05-25 13:00:00",
  },
];

import type { TriggerItem, TriggerPageOption } from "./dto";

export const SCHEDULER_TRIGGER_QUERY_KEY = "scheduler-trigger";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: TriggerPageOption = {
  name: "",
  type: undefined,
  policy: undefined,
};

export const triggerTypeOptions = [
  { label: "CRON", value: "CRON" },
  { label: "INTERVAL", value: "INTERVAL" },
];

export const policyOptions = [
  { label: "执行一次", value: "EXECUTE_ONCE" },
  { label: "顺序执行", value: "SERIAL_WAIT" },
  { label: "重复执行", value: "PARALLEL" },
  { label: "丢弃最新", value: "DISCARD_NEW" },
  { label: "覆盖执行", value: "DISCARD_OLD" },
];

export const demoTriggerRows: TriggerItem[] = [
  {
    id: "trigger-1",
    name: "每日凌晨调度",
    type: "CRON",
    policy: "SERIAL_WAIT",
    cron: "0 0 2 * * ?",
    creator: "platform",
    updateTime: "2026-05-25 10:00:00",
  },
];

import { Tag } from "antd";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface TriggerRow extends Record<string, unknown> {
  id: string;
  name: string;
  type: string;
  policy: string;
  cron?: string;
  interval?: number;
  creator?: string;
  updateTime?: string;
}

const policyOptions = [
  { label: "执行一次", value: "EXECUTE_ONCE" },
  { label: "顺序执行", value: "SERIAL_WAIT" },
  { label: "重复执行", value: "PARALLEL" },
  { label: "丢弃最新", value: "DISCARD_NEW" },
  { label: "覆盖执行", value: "DISCARD_OLD" },
];

const fields: ResourceField<TriggerRow>[] = [
  { key: "name", label: "触发器名称", required: true, width: 180 },
  {
    key: "type",
    label: "触发器类型",
    type: "select",
    required: true,
    width: 130,
    options: [
      { label: "CRON", value: "CRON" },
      { label: "INTERVAL", value: "INTERVAL" },
    ],
    render: (value) => <Tag color={value === "CRON" ? "blue" : "green"}>{String(value || "-")}</Tag>,
  },
  {
    key: "policy",
    label: "调度策略",
    type: "select",
    required: true,
    width: 140,
    options: policyOptions,
    render: (value) => policyOptions.find((item) => item.value === value)?.label || String(value || "-"),
  },
  { key: "cron", label: "CRON 表达式", width: 180 },
  { key: "interval", label: "周期间隔（分钟）", type: "number", width: 150 },
  { key: "creator", label: "创建人", hiddenInForm: true, width: 120 },
  { key: "updateTime", label: "更新时间", hiddenInForm: true, width: 180 },
];

const demoRows: TriggerRow[] = [
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

export default function SchedulerTriggerPage() {
  return (
    <ResourcePage<TriggerRow>
      title="调度器配置"
      description="维护 cron、固定间隔和调度策略配置。"
      entityName="触发器"
      endpoints={{
        list: "/api/scheduler/trigger/page",
        add: "/api/scheduler/trigger/add",
        update: "/api/scheduler/trigger/update",
        delete: "/api/scheduler/trigger/{id}",
      }}
      fields={fields}
      demoRows={demoRows}
      mapSearch={(keyword) => ({ name: keyword })}
    />
  );
}

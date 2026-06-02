import { Tag } from "antd";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface TaskRow extends Record<string, unknown> {
  id: string;
  taskName: string;
  taskCode: string;
  taskType: string;
  isBound: boolean;
  flowName?: string;
  pluginId?: string;
  pluginName?: string;
  enabled: boolean;
  updateTime?: string;
}

const fields: ResourceField<TaskRow>[] = [
  { key: "taskName", label: "任务名称", required: true, width: 180 },
  { key: "taskCode", label: "任务编码", required: true, width: 160 },
  {
    key: "taskType",
    label: "任务类型",
    type: "select",
    required: true,
    width: 120,
    options: ["DATAX", "SHELL", "SQL"].map((value) => ({ label: value, value })),
    render: (value) => <Tag color={value === "DATAX" ? "blue" : value === "SHELL" ? "green" : "gold"}>{String(value || "-")}</Tag>,
  },
  {
    key: "isBound",
    label: "是否绑定流程",
    type: "boolean",
    width: 130,
    render: (value) => <Tag color={value ? "green" : "default"}>{value ? "已绑定" : "未绑定"}</Tag>,
  },
  { key: "flowName", label: "所属流程", width: 160 },
  { key: "pluginName", label: "执行组件", width: 160 },
  {
    key: "enabled",
    label: "是否启用",
    type: "boolean",
    width: 110,
    render: (value) => <Tag color={value ? "green" : "default"}>{value ? "启用" : "禁用"}</Tag>,
  },
  { key: "updateTime", label: "更新时间", hiddenInForm: true, width: 180 },
];

const demoRows: TaskRow[] = [
  {
    id: "task-1",
    taskName: "同步订单数据",
    taskCode: "sync_order_ods",
    taskType: "DATAX",
    isBound: true,
    flowName: "每日订单加工",
    pluginName: "DataX",
    enabled: true,
    updateTime: "2026-05-25 12:00:00",
  },
];

export default function SchedulerTaskPage() {
  return (
    <ResourcePage<TaskRow>
      title="任务管理"
      description="维护可被流程编排的任务定义、任务类型、执行组件和启用状态。"
      breadcrumb={["调度中心", "任务管理"]}
      entityName="任务"
      endpoints={{
        list: "/api/scheduler/task/page",
        add: "/api/scheduler/task/add",
        update: "/api/scheduler/task/update",
        delete: "/api/scheduler/task/delete",
      }}
      fields={fields}
      demoRows={demoRows}
      mapSearch={(keyword) => ({ taskName: keyword })}
    />
  );
}

import { Tag } from "antd";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface VariableRow extends Record<string, unknown> {
  id: string;
  code: string;
  name?: string;
  type: string;
  valueType: string;
  value?: string;
  updater?: string;
  updateTime?: string;
}

const fields: ResourceField<VariableRow>[] = [
  { key: "code", label: "变量编码", required: true, width: 180 },
  { key: "name", label: "变量名称", width: 180 },
  {
    key: "type",
    label: "变量类型",
    type: "select",
    required: true,
    width: 120,
    options: [
      { label: "CUSTOM", value: "CUSTOM" },
      { label: "SYSTEM", value: "SYSTEM" },
    ],
    render: (value) => <Tag color={value === "SYSTEM" ? "gold" : "blue"}>{String(value || "-")}</Tag>,
  },
  {
    key: "valueType",
    label: "值类型",
    type: "select",
    required: true,
    width: 120,
    options: ["STRING", "NUMBER", "BOOLEAN", "DATE"].map((value) => ({ label: value, value })),
  },
  { key: "value", label: "默认值", width: 180 },
  { key: "updater", label: "更新人", hiddenInForm: true, width: 120 },
  { key: "updateTime", label: "更新时间", hiddenInForm: true, width: 180 },
];

const demoRows: VariableRow[] = [
  {
    id: "var-1",
    code: "biz_date",
    name: "业务日期",
    type: "SYSTEM",
    valueType: "DATE",
    value: "${yyyyMMdd-1}",
    updater: "scheduler",
    updateTime: "2026-05-25 09:30:00",
  },
];

export default function SchedulerVariablePage() {
  return (
    <ResourcePage<VariableRow>
      title="变量配置"
      description="维护调度流程可复用变量、变量类型和值类型。"
      entityName="变量"
      endpoints={{
        list: "/api/datafusion-manager/api/scheduler/variable/page",
        add: "/api/datafusion-manager/api/scheduler/variable/add",
        update: "/api/datafusion-manager/api/scheduler/variable/update",
        delete: "/api/datafusion-manager/api/scheduler/variable/{id}",
      }}
      fields={fields}
      demoRows={demoRows}
      mapSearch={(keyword) => ({ code: keyword })}
    />
  );
}

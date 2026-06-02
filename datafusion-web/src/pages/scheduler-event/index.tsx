import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface EventRow extends Record<string, unknown> {
  id: string;
  eventName: string;
  eventType: string;
  flowId?: string;
  taskId?: string;
  updater?: string;
  updateTime?: string;
}

const fields: ResourceField<EventRow>[] = [
  { key: "eventName", label: "事件名称", required: true, width: 180 },
  {
    key: "eventType",
    label: "事件类型",
    type: "select",
    required: true,
    width: 140,
    options: ["FLOW_START", "FLOW_END", "TASK_SUCCESS", "TASK_FAILED"].map((value) => ({
      label: value,
      value,
    })),
  },
  { key: "flowId", label: "流程ID", width: 180 },
  { key: "taskId", label: "任务ID", width: 180 },
  { key: "updater", label: "更新人", hiddenInForm: true, width: 120 },
  { key: "updateTime", label: "更新时间", hiddenInForm: true, width: 180 },
];

const demoRows: EventRow[] = [
  {
    id: "event-1",
    eventName: "订单同步完成",
    eventType: "TASK_SUCCESS",
    flowId: "flow-1",
    taskId: "task-1",
    updater: "scheduler",
    updateTime: "2026-05-25 12:30:00",
  },
];

export default function SchedulerEventPage() {
  return (
    <ResourcePage<EventRow>
      title="事件管理"
      description="维护调度事件、事件类型以及流程/任务联动关系。"
      entityName="事件"
      endpoints={{
        list: "/api/scheduler/event/page",
        add: "/api/scheduler/event/add",
        update: "/api/scheduler/event/update",
        delete: "/api/scheduler/event/delete/{id}",
      }}
      fields={fields}
      demoRows={demoRows}
      mapSearch={(keyword) => ({ eventName: keyword })}
    />
  );
}

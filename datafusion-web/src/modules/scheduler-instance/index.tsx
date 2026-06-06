import { Space } from "antd";
import { useState } from "react";
import { PageHeader } from "@/components/page-layout";
import {
  SchedulerInstanceListTable,
  TaskDependencyDrawer,
  TaskLogDrawer,
  type DependencyState,
} from "./components";
import type { TaskInstanceItem } from "./dto";

export default function SchedulerInstancePage() {
  const [dependency, setDependency] = useState<DependencyState>();
  const [logTask, setLogTask] = useState<TaskInstanceItem>();

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader breadcrumb={[{ label: "调度中心" }, { label: "实例查询" }]} title="实例查询" />

      <SchedulerInstanceListTable
        onOpenDependency={(flow, task) => setDependency({ flow, task })}
        onOpenLog={setLogTask}
      />

      <TaskDependencyDrawer dependency={dependency} onClose={() => setDependency(undefined)} />

      <TaskLogDrawer open={Boolean(logTask)} task={logTask} onClose={() => setLogTask(undefined)} />
    </Space>
  );
}

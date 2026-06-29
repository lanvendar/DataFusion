import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { taskApi } from "./api";
import { TaskDetail, TaskForm, TaskListTable } from "./components";
import { SCHEDULER_TASK_QUERY_KEY } from "./constants";
import { PageActionEnum, type TaskFormMode, type TaskItem } from "./dto";

export default function SchedulerTaskPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [formMode, setFormMode] = useState<TaskFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<TaskItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SCHEDULER_TASK_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => taskApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const copyMutation = useMutation({
    mutationFn: (sourceId: string) => taskApi.copy({ sourceId }),
    onSuccess: () => {
      message.success("复制成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: TaskFormMode, record?: TaskItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: TaskItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.VIEW:
          setCurrentRecord(record);
          setDetailOpen(true);
          break;
        case PageActionEnum.DELETE:
          if (record?.isBound) {
            message.warning("该任务已绑定流程，请先解绑后再删除");
            return;
          }
          if (record?.id) deleteMutation.mutate(record.id);
          break;
        case PageActionEnum.COPY:
          if (record?.id) copyMutation.mutate(record.id);
          break;
        default:
          break;
      }
    },
    [copyMutation, deleteMutation, message, openForm],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "调度中心" }, { label: "任务管理" }]}
        title="任务管理"
        description="维护任务名称、编码、类型、参数和定义内容；流程绑定、执行插件和事件依赖在流程编排中配置。"
      />

      <TaskListTable loading={deleteMutation.isPending || copyMutation.isPending} onAction={onAction} />

      <TaskForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />

      <TaskDetail
        open={detailOpen}
        taskId={currentRecord?.id}
        onClose={() => setDetailOpen(false)}
      />
    </Space>
  );
}

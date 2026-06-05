import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { taskTypeConfigApi } from "./api";
import { TaskTypeConfigForm, TaskTypeConfigListTable } from "./components";
import { SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY } from "./constants";
import {
  PageActionEnum,
  type TaskTypeConfigFormMode,
  type TaskTypeConfigItem,
} from "./dto";

export default function SystemTaskTypeConfigPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<TaskTypeConfigFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<TaskTypeConfigItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => taskTypeConfigApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: TaskTypeConfigFormMode, record?: TaskTypeConfigItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: TaskTypeConfigItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.DELETE:
          if (record?.id) deleteMutation.mutate(record.id);
          break;
        default:
          break;
      }
    },
    [deleteMutation, openForm],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "系统配置" }, { label: "任务类型配置" }]}
        title="任务类型配置"
        description="维护任务类型与默认执行插件的绑定关系。"
      />

      <TaskTypeConfigListTable loading={deleteMutation.isPending} onAction={onAction} />

      <TaskTypeConfigForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

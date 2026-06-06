import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { workerRegistryApi } from "./api";
import { WorkerRegistryForm, WorkerRegistryListTable } from "./components";
import { SCHEDULER_WORKER_QUERY_KEY } from "./constants";
import {
  PageActionEnum,
  type WorkerRegistryFormMode,
  type WorkerRegistryItem,
} from "./dto";

export default function SchedulerWorkerPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<WorkerRegistryFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<WorkerRegistryItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SCHEDULER_WORKER_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => workerRegistryApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: WorkerRegistryFormMode, record?: WorkerRegistryItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: WorkerRegistryItem) => {
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
        breadcrumb={[{ label: "调度中心" }, { label: "Worker 管理" }]}
        title="Worker 管理"
        description="查看和维护调度 worker 注册状态、心跳时间、插件能力和运行元信息。"
      />

      <WorkerRegistryListTable loading={deleteMutation.isPending} onAction={onAction} />

      <WorkerRegistryForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

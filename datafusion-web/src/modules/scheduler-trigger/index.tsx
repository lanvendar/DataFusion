import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-header";
import { triggerApi } from "./api";
import { TriggerForm, TriggerListTable } from "./components";
import { SCHEDULER_TRIGGER_QUERY_KEY } from "./constants";
import { PageActionEnum, type TriggerFormMode, type TriggerItem } from "./dto";

export default function SchedulerTriggerPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<TriggerFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<TriggerItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SCHEDULER_TRIGGER_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => triggerApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: TriggerFormMode, record?: TriggerItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: TriggerItem) => {
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
        breadcrumb={[{ label: "调度中心" }, { label: "调度器配置" }]}
        title="调度器配置"
        description="维护 CRON、固定间隔和调度策略配置。"
      />

      <TriggerListTable loading={deleteMutation.isPending} onAction={onAction} />

      <TriggerForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

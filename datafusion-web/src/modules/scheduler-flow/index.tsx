import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { PageHeader } from "@/components/page-layout";
import { flowApi } from "./api";
import { FlowDetail, FlowForm, FlowListTable, FlowScheduleModal } from "./components";
import { DagEditor } from "./dag-editor";
import { SCHEDULER_FLOW_QUERY_KEY } from "./constants";
import { PageActionEnum, type FlowFormMode, type FlowItem } from "./dto";

export default function SchedulerFlowPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [formOpen, setFormOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [dagOpen, setDagOpen] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [formMode, setFormMode] = useState<FlowFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<FlowItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SCHEDULER_FLOW_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => flowApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: FlowFormMode, record?: FlowItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const confirmDisable = useCallback(
    async (record: FlowItem) => {
      try {
        await flowApi.disable(record.id);
        message.success("取消调度成功");
        refreshList();
      } catch (error) {
        message.error(error instanceof Error ? error.message : "取消调度失败");
      }
    },
    [message, refreshList],
  );

  const publishFlow = useCallback(
    async (record: FlowItem) => {
      try {
        await flowApi.publish(record.id);
        message.success("发布成功");
        refreshList();
      } catch (error) {
        message.error(error instanceof Error ? error.message : "发布失败");
      }
    },
    [message, refreshList],
  );

  const confirmUnpublish = useCallback(
    async (record: FlowItem) => {
      try {
        await flowApi.unpublish(record.id);
        message.success("取消发布成功");
        refreshList();
      } catch (error) {
        message.error(error instanceof Error ? error.message : "取消发布失败");
      }
    },
    [message, refreshList],
  );

  const onAction = useCallback(
    (action: PageActionEnum, record?: FlowItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.EDIT:
          if (record?.publishState || record?.enabled) {
            message.warning("已发布或调度中的流程只能查看，不能编辑");
            return;
          }
          openForm("edit", record);
          break;
        case PageActionEnum.VIEW:
          setCurrentRecord(record);
          setDetailOpen(true);
          break;
        case PageActionEnum.DAG_EDIT:
          setCurrentRecord(record);
          setDagOpen(true);
          break;
        case PageActionEnum.VIEW_INSTANCE:
          if (record?.flowName) {
            navigate(`/scheduler-instance?flowKeyword=${encodeURIComponent(record.flowName)}`);
          }
          break;
        case PageActionEnum.DELETE:
          if (!record?.id) return;
          if (record.enabled) {
            message.warning("调度中的流程不可删除");
            return;
          }
          if (record.publishState) {
            message.warning("已发布流程不可删除");
            return;
          }
          deleteMutation.mutate(record.id);
          break;
        case PageActionEnum.PUBLISH:
          if (record?.id) void publishFlow(record);
          break;
        case PageActionEnum.UNPUBLISH:
          if (record?.id) void confirmUnpublish(record);
          break;
        case PageActionEnum.ENABLE:
          if (record?.id) {
            setCurrentRecord(record);
            setScheduleOpen(true);
          }
          break;
        case PageActionEnum.DISABLE:
          if (record?.id) void confirmDisable(record);
          break;
        default:
          break;
      }
    },
    [
      confirmDisable,
      confirmUnpublish,
      deleteMutation,
      message,
      navigate,
      openForm,
      publishFlow,
    ],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "调度中心" }, { label: "流程管理" }]}
        title="流程管理"
        description="维护流程基础定义、发布状态与调度启停，并通过 DAG 画布编排任务依赖关系。"
      />

      <FlowListTable loading={deleteMutation.isPending} onAction={onAction} />

      <FlowForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />

      <FlowDetail
        open={detailOpen}
        flowId={currentRecord?.id}
        onClose={() => setDetailOpen(false)}
      />

      <DagEditor
        open={dagOpen}
        currentRecord={currentRecord}
        onClose={() => setDagOpen(false)}
        onSubmitSuccess={refreshList}
      />

      <FlowScheduleModal
        open={scheduleOpen}
        currentRecord={currentRecord}
        onClose={() => setScheduleOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

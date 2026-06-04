import { App, Checkbox, Modal, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { flowApi } from "./api";
import { FlowDetail, FlowForm, FlowListTable } from "./components";
import { DagEditor } from "./dag-editor";
import { SCHEDULER_FLOW_QUERY_KEY } from "./constants";
import { PageActionEnum, type FlowFormMode, type FlowItem } from "./dto";
import { runSequentialActions } from "./utils";

export default function SchedulerFlowPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [dagOpen, setDagOpen] = useState(false);
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

  const confirmEnable = useCallback(
    (record: FlowItem) => {
      Modal.confirm({
        title: "确认启用调度",
        content: `确认启用流程「${record.flowName}」的调度吗？`,
        onOk: async () => {
          await flowApi.enable(record.id);
          message.success("启用成功");
          refreshList();
        },
      });
    },
    [message, refreshList],
  );

  const confirmDisable = useCallback(
    (record: FlowItem) => {
      Modal.confirm({
        title: "确认停用调度",
        content: `确认停用流程「${record.flowName}」的调度吗？`,
        onOk: async () => {
          await flowApi.disable(record.id);
          message.success("停用成功");
          refreshList();
        },
      });
    },
    [message, refreshList],
  );

  const confirmPublish = useCallback(
    (record: FlowItem) => {
      let enableSchedule = false;

      Modal.confirm({
        title: "确认发布流程",
        content: (
          <Space direction="vertical" size={12}>
            <span>{`确认发布流程「${record.flowName}」吗？`}</span>
            <Checkbox onChange={(event) => { enableSchedule = event.target.checked; }}>
              同时启用调度
            </Checkbox>
          </Space>
        ),
        onOk: async () => {
          await flowApi.publish({ id: record.id, enableSchedule });
          message.success(enableSchedule ? "发布并启用成功" : "发布成功");
          refreshList();
        },
      });
    },
    [message, refreshList],
  );

  const confirmUnpublish = useCallback(
    (record: FlowItem) => {
      Modal.confirm({
        title: "确认取消发布",
        content: record.enabled
          ? "该流程当前仍在调度中，将先停用调度再取消发布，是否继续？"
          : `确认取消发布流程「${record.flowName}」吗？`,
        onOk: async () => {
          await runSequentialActions([
            ...(record.enabled ? [() => flowApi.disable(record.id)] : []),
            () => flowApi.unpublish(record.id),
          ]);
          message.success(record.enabled ? "已停用调度并取消发布" : "取消发布成功");
          refreshList();
        },
      });
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
          if (record?.id) confirmPublish(record);
          break;
        case PageActionEnum.UNPUBLISH:
          if (record?.id) confirmUnpublish(record);
          break;
        case PageActionEnum.ENABLE:
          if (record?.id) {
            if (!record.publishState) {
              message.warning("流程未发布，无法启用调度");
              return;
            }
            confirmEnable(record);
          }
          break;
        case PageActionEnum.DISABLE:
          if (record?.id) confirmDisable(record);
          break;
        default:
          break;
      }
    },
    [
      confirmDisable,
      confirmEnable,
      confirmPublish,
      confirmUnpublish,
      deleteMutation,
      message,
      openForm,
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
    </Space>
  );
}

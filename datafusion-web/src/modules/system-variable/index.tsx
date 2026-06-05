import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { variableApi } from "./api";
import { VariableForm, VariableListTable } from "./components";
import { SYSTEM_VARIABLE_QUERY_KEY } from "./constants";
import { PageActionEnum, type VariableFormMode, type VariableItem } from "./dto";

export default function SystemVariablePage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<VariableFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<VariableItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SYSTEM_VARIABLE_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => variableApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: VariableFormMode, record?: VariableItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: VariableItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.DELETE:
          if (record?.id && record.type !== "SYSTEM") deleteMutation.mutate(record.id);
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
        breadcrumb={[{ label: "系统配置" }, { label: "变量配置" }]}
        title="变量配置"
        description="维护系统级可复用变量、变量类型和值类型。"
      />

      <VariableListTable loading={deleteMutation.isPending} onAction={onAction} />

      <VariableForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

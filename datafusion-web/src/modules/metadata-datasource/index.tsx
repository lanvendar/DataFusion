import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-header";
import { datasourceApi } from "./api";
import { DatasourceForm, DatasourceListTable, TableRegister } from "./components";
import { METADATA_DATASOURCE_QUERY_KEY } from "./constants";
import {
  PageActionEnum,
  type DatasourceFormMode,
  type DatasourceItem,
} from "./dto";

export default function MetadataDatasourcePage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<DatasourceFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<DatasourceItem>();
  const [registerOpen, setRegisterOpen] = useState(false);
  const [registerRecord, setRegisterRecord] = useState<DatasourceItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [METADATA_DATASOURCE_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => datasourceApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const refreshMutation = useMutation({
    mutationFn: (id: string) => datasourceApi.refresh(id),
    onSuccess: () => {
      message.success("刷新成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: DatasourceFormMode, record?: DatasourceItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: DatasourceItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.COPY_ADD:
          openForm("copy", record);
          break;
        case PageActionEnum.DELETE:
          if (record?.id) deleteMutation.mutate(record.id);
          break;
        case PageActionEnum.REFRESH:
          if (record?.id) refreshMutation.mutate(record.id);
          break;
        case PageActionEnum.TABLE_REGISTER:
          setRegisterRecord(record);
          setRegisterOpen(true);
          break;
        default:
          break;
      }
    },
    [deleteMutation, openForm, refreshMutation],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "元数据管理" }, { label: "数据源管理" }]}
        title="数据源管理"
        description="集中管理数据源连接，支持新增、编辑、复制、刷新和表登记。"
      />

      <DatasourceListTable
        loading={deleteMutation.isPending || refreshMutation.isPending}
        onAction={onAction}
      />

      <DatasourceForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />

      <TableRegister
        open={registerOpen}
        currentRecord={registerRecord}
        onClose={() => setRegisterOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

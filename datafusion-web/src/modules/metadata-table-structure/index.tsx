import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { PageHeader } from "@/components/page-layout";
import { tableStructureApi } from "./api";
import { TableStructureForm, TableStructureListTable } from "./components";
import { METADATA_TABLE_STRUCTURE_QUERY_KEY } from "./constants";
import {
  PageActionEnum,
  type TableStructureFormMode,
  type TableStructureItem,
} from "./dto";

export default function MetadataTableStructurePage() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<TableStructureFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<TableStructureItem>();
  const [selectedRows, setSelectedRows] = useState<TableStructureItem[]>([]);

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [METADATA_TABLE_STRUCTURE_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => tableStructureApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const updateStructure = useCallback(
    async (record: TableStructureItem) => {
      if (!record.datasourceId || !record.tableName) {
        message.error("缺少数据源或表名，无法更新表结构");
        return;
      }
      await tableStructureApi.delete(record.id);
      await tableStructureApi.registerTables({
        datasourceId: record.datasourceId,
        tableNames: [record.tableName],
      });
    },
    [message],
  );

  const updateStructureMutation = useMutation({
    mutationFn: updateStructure,
    onSuccess: () => {
      message.success("表结构已更新");
      refreshList();
    },
    onError: () => {
      message.error("表结构更新失败，请检查数据源和表登记状态");
    },
  });

  const batchUpdateMutation = useMutation({
    mutationFn: async (rows: TableStructureItem[]) => {
      let successCount = 0;
      const failed: string[] = [];
      for (const row of rows) {
        try {
          await updateStructure(row);
          successCount += 1;
        } catch {
          failed.push(row.tableName || row.id);
        }
      }
      return { successCount, failed };
    },
    onSuccess: ({ successCount, failed }) => {
      if (failed.length) {
        message.warning(`更新完成：成功 ${successCount} 张，失败 ${failed.length} 张`);
      } else {
        message.success(`已更新 ${successCount} 张表`);
      }
      setSelectedRows([]);
      refreshList();
    },
  });

  const openForm = useCallback((mode: TableStructureFormMode, record?: TableStructureItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: TableStructureItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.VIEW:
          if (record?.id) navigate(`/metadata-table-structure/${record.id}`);
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.DELETE:
          if (record?.id) deleteMutation.mutate(record.id);
          break;
        case PageActionEnum.UPDATE_STRUCTURE:
          if (record) updateStructureMutation.mutate(record);
          break;
        case PageActionEnum.BATCH_UPDATE_STRUCTURE:
          if (!selectedRows.length) {
            message.warning("请先勾选需要更新的表");
            break;
          }
          modal.confirm({
            title: "确认批量更新表结构？",
            content: `将对选中的 ${selectedRows.length} 张表依次删除元数据并重新登记。`,
            onOk: () => batchUpdateMutation.mutate(selectedRows),
          });
          break;
        default:
          break;
      }
    },
    [
      batchUpdateMutation,
      deleteMutation,
      message,
      modal,
      navigate,
      openForm,
      selectedRows,
      updateStructureMutation,
    ],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[
          { label: "元数据管理" },
          { label: "表结构管理" },
        ]}
        title="表结构管理"
        description="查看和维护元数据表结构，支持详情预览、编辑、删除以及重新登记同步。"
      />

      <TableStructureListTable
        loading={
          deleteMutation.isPending ||
          updateStructureMutation.isPending ||
          batchUpdateMutation.isPending
        }
        selectedRowKeys={selectedRows.map((row) => row.id)}
        onSelectedRowsChange={setSelectedRows}
        onAction={onAction}
      />

      <TableStructureForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

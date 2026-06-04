import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { tableSyncApi } from "./api";
import { TableSyncDetail, TableSyncDrawer, TableSyncListTable } from "./components";
import { METADATA_TABLE_SYNC_QUERY_KEY } from "./constants";
import { PageActionEnum, type SyncDrawerValues, type SyncMode, type TableSyncItem } from "./dto";

function normalizeDefaultColumns(value?: string[] | string) {
  const list = Array.isArray(value) ? value : (value || "").split(",");
  return list
    .map((item) => item.trim())
    .filter(Boolean);
}

export default function MetadataTableSyncPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRecord, setDetailRecord] = useState<TableSyncItem>();
  const [syncMode, setSyncMode] = useState<SyncMode>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [METADATA_TABLE_SYNC_QUERY_KEY] });
  }, [queryClient]);

  const syncMutation = useMutation({
    mutationFn: async ({ mode, values }: { mode: SyncMode; values: SyncDrawerValues }) => {
      const trackId = await tableSyncApi.generateTrackId(mode === "create" ? 0 : 1).catch(() => undefined);
      const tableMapping = Object.fromEntries(values.tableNames.map((tableName) => [tableName, tableName]));
      if (mode === "create") {
        const payload = {
          trackId,
          sourceDatasourceId: values.sourceDatasourceId,
          targetDatasourceId: values.targetDatasourceId,
          sourceDataSourceId: values.sourceDatasourceId,
          targetDataSourceId: values.targetDatasourceId,
          tableNames: values.tableNames,
          tableMapping,
          defaultColumns: normalizeDefaultColumns(values.defaultColumns),
        };
        await tableSyncApi.batchCreateTableCheck(payload);
        const sql = await tableSyncApi.batchCreateTableDdl(payload);
        await tableSyncApi.executeSql({ trackId, sql });
        return;
      }
      const compareResult = await tableSyncApi.batchMetaCompare({
        trackId,
        source: { datasourceId: values.sourceDatasourceId, tableNames: values.tableNames },
        target: { datasourceId: values.targetDatasourceId, tableNames: values.tableNames },
      });
      const sql = await tableSyncApi.generateTableCompareDdlSql({
        trackId,
        tableCompareResultList: compareResult,
      });
      await tableSyncApi.executeSql({ trackId, sql });
    },
    onSuccess: () => {
      message.success("同步任务已提交");
      refreshList();
    },
  });

  const onAction = useCallback((action: PageActionEnum, record?: TableSyncItem) => {
    switch (action) {
      case PageActionEnum.VIEW:
        setDetailRecord(record);
        setDetailOpen(true);
        break;
      case PageActionEnum.BATCH_CREATE:
        setSyncMode("create");
        break;
      case PageActionEnum.BATCH_COMPARE:
        setSyncMode("compare");
        break;
      default:
        break;
    }
  }, []);

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "元数据管理" }, { label: "表结构同步" }]}
        title="表结构同步"
        description="查看表结构同步操作日志，并发起批量创建或批量对比同步。"
      />

      <TableSyncListTable loading={syncMutation.isPending} onAction={onAction} />

      <TableSyncDetail
        open={detailOpen}
        record={detailRecord}
        onClose={() => setDetailOpen(false)}
      />

      {syncMode ? (
        <TableSyncDrawer
          open={!!syncMode}
          mode={syncMode}
          onClose={() => setSyncMode(undefined)}
          onSubmit={(mode, values) => syncMutation.mutateAsync({ mode, values })}
        />
      ) : null}
    </Space>
  );
}

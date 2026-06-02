import { Descriptions, Drawer, Empty, Spin, Tabs } from "antd";
import { useQuery } from "@tanstack/react-query";
import { tableSyncApi } from "../../api";
import type { TableSyncItem } from "../../dto";

interface TableSyncDetailProps {
  open: boolean;
  record?: TableSyncItem;
  onClose: () => void;
}

function JsonBlock({ value }: { value?: Record<string, unknown> }) {
  if (!value) return <Empty description="暂无快照" />;
  return <pre className="json-block">{JSON.stringify(value, null, 2)}</pre>;
}

export function TableSyncDetail({ open, record, onClose }: TableSyncDetailProps) {
  const detailQuery = useQuery({
    queryKey: ["metadata-table-sync-detail", record?.id],
    enabled: open && !!record?.id,
    queryFn: () => tableSyncApi.detail(String(record?.id)),
  });
  const detail = detailQuery.data || record;

  return (
    <Drawer title={`表结构同步详情${record ? `：${record.id}` : ""}`} open={open} width={920} onClose={onClose}>
      <Spin spinning={detailQuery.isFetching}>
        {detail ? (
          <Tabs
            items={[
              {
                key: "basic",
                label: "基础信息",
                children: (
                  <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label="源数据源">{detail.sourceDataSourceName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="源数据库">{detail.sourceDatabaseName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="源表空间">{detail.sourceSchemaName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="目标数据源">{detail.targetDataSourceName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="目标数据库">{detail.targetDatabaseName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="目标表空间">{detail.targetSchemaName || "-"}</Descriptions.Item>
                    <Descriptions.Item label="同步类型">{Number(detail.operateType) === 0 ? "批量创建" : "批量同步"}</Descriptions.Item>
                    <Descriptions.Item label="操作时间">{detail.operateTime || "-"}</Descriptions.Item>
                  </Descriptions>
                ),
              },
              { key: "step1", label: "步骤一快照", children: <JsonBlock value={detail.snapshotStep1} /> },
              { key: "step2", label: "步骤二快照", children: <JsonBlock value={detail.snapshotStep2} /> },
              { key: "step3", label: "SQL 快照", children: <JsonBlock value={detail.snapshotStep3} /> },
            ]}
          />
        ) : (
          <Empty description="请选择同步记录" />
        )}
      </Spin>
    </Drawer>
  );
}

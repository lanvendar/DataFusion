import { Button, Card, Drawer, Space, Steps, Tag, Typography } from "antd";
import { useState } from "react";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface TableSyncRow extends Record<string, unknown> {
  id: string;
  sourceDataSourceName: string;
  sourceDatabaseName: string;
  targetDataSourceName: string;
  targetDatabaseName: string;
  operateType: number;
  operateTime: string;
}

const fields: ResourceField<TableSyncRow>[] = [
  { key: "sourceDataSourceName", label: "源数据库", width: 180 },
  { key: "sourceDatabaseName", label: "源数据库名称", width: 140 },
  { key: "targetDataSourceName", label: "目标数据库", width: 180 },
  { key: "targetDatabaseName", label: "目标数据库名称", width: 140 },
  {
    key: "operateType",
    label: "同步类型",
    type: "select",
    width: 120,
    options: [
      { label: "批量创建", value: 0 },
      { label: "批量同步", value: 1 },
    ],
    render: (value) =>
      Number(value) === 0 ? <Tag color="green">批量创建</Tag> : <Tag color="orange">批量同步</Tag>,
  },
  { key: "operateTime", label: "操作时间", width: 180 },
];

const demoRows: TableSyncRow[] = [
  {
    id: "sync-1",
    sourceDataSourceName: "ods_mysql_prod",
    sourceDatabaseName: "ods",
    targetDataSourceName: "dw_starrocks",
    targetDatabaseName: "warehouse",
    operateType: 0,
    operateTime: "2026-05-25 14:00:00",
  },
];

function SyncWizard({ title, open, onClose }: { title: string; open: boolean; onClose: () => void }) {
  return (
    <Drawer title={title} open={open} width={760} onClose={onClose}>
      <Space direction="vertical" size={20} className="page-stack">
        <Steps
          current={1}
          items={[
            { title: "选择数据源" },
            { title: "确认表映射" },
            { title: "生成并执行 SQL" },
          ]}
        />
        <Card>
          <Typography.Paragraph>
            这里已替换为开源 Ant Design 向导骨架。后续迁移原项目 `BatchCreate` / `BatchCompare`
            时，可把数据源选择、表对比和 SQL 执行步骤填入当前 Drawer。
          </Typography.Paragraph>
          <Button type="primary" onClick={onClose}>
            完成
          </Button>
        </Card>
      </Space>
    </Drawer>
  );
}

export default function MetadataTableSyncPage() {
  const [wizard, setWizard] = useState<"create" | "compare" | undefined>();

  return (
    <>
      <ResourcePage<TableSyncRow>
        title="表结构同步"
        description="查看表结构同步操作记录，并发起批量创建或批量对比同步。"
        entityName="同步记录"
        endpoints={{
          list: "/api/datafusion-manager/api/metadata/page/operateLog",
        }}
        fields={fields}
        demoRows={demoRows}
        mapSearch={(keyword) => ({ sourceDataSourceName: keyword })}
        extraActions={[
          {
            key: "detail",
            label: "轨迹",
            onClick: () => undefined,
          },
        ]}
      />
      <div className="floating-actions">
        <Button type="primary" onClick={() => setWizard("create")}>
          批量创建
        </Button>
        <Button onClick={() => setWizard("compare")}>批量对比</Button>
      </div>
      <SyncWizard title="批量创建表结构" open={wizard === "create"} onClose={() => setWizard(undefined)} />
      <SyncWizard title="批量对比表结构" open={wizard === "compare"} onClose={() => setWizard(undefined)} />
    </>
  );
}

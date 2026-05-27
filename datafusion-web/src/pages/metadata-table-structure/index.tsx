import { Tag } from "antd";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface TableStructureRow extends Record<string, unknown> {
  id: string;
  databaseConnectName: string;
  databaseName: string;
  databaseType: string;
  schemaName: string;
  tableName: string;
  tableDesc?: string;
  checkTime?: string;
  isModify: boolean;
  isView: boolean;
}

const fields: ResourceField<TableStructureRow>[] = [
  { key: "databaseConnectName", label: "数据源链接名称", width: 180 },
  { key: "databaseName", label: "数据库名称", width: 140 },
  { key: "databaseType", label: "数据库类型", width: 120 },
  { key: "schemaName", label: "表空间名称", width: 130 },
  { key: "tableName", label: "表名称", required: true, width: 180 },
  { key: "tableDesc", label: "表注释", type: "textarea", width: 180 },
  { key: "checkTime", label: "检查时间", hiddenInForm: true, width: 180 },
  {
    key: "isModify",
    label: "是否同步",
    type: "boolean",
    width: 110,
    render: (value) => <Tag color={value ? "green" : "default"}>{value ? "已同步" : "未同步"}</Tag>,
  },
  {
    key: "isView",
    label: "是否视图",
    type: "boolean",
    width: 100,
  },
];

const demoRows: TableStructureRow[] = [
  {
    id: "table-1",
    databaseConnectName: "dw_starrocks",
    databaseName: "warehouse",
    databaseType: "STARROCKS",
    schemaName: "analytics",
    tableName: "dim_product",
    tableDesc: "产品维表",
    checkTime: "2026-05-24 10:30:00",
    isModify: true,
    isView: false,
  },
  {
    id: "table-2",
    databaseConnectName: "ods_mysql_prod",
    databaseName: "ods",
    databaseType: "MYSQL",
    schemaName: "public",
    tableName: "ods_order",
    tableDesc: "订单原始表",
    checkTime: "2026-05-24 11:10:00",
    isModify: false,
    isView: false,
  },
];

export default function MetadataTableStructurePage() {
  return (
    <ResourcePage<TableStructureRow>
      title="表结构管理"
      description="查看和维护元数据表、字段同步状态、表注释和视图信息。"
      entityName="表结构"
      endpoints={{
        list: "/api/datafusion-manager/api/metadata/table/page",
        add: "/api/datafusion-manager/api/metadata/table/add",
        update: "/api/datafusion-manager/api/metadata/table/update",
        delete: "/api/datafusion-manager/api/metadata/table/{id}",
      }}
      fields={fields}
      demoRows={demoRows}
      mapSearch={(keyword) => ({ tableName: keyword })}
      extraActions={[
        {
          key: "sync",
          label: "更新结构",
          onClick: (_, { refresh }) => refresh(),
        },
      ]}
    />
  );
}

import { EyeOutlined } from "@ant-design/icons";
import { Button, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageActionEnum, type TableSyncItem } from "../../dto";

function formatTime(value: unknown) {
  if (value == null || value === "") return "-";
  const text = String(value);
  if (!text.includes("T")) return text;
  return text.replace("T", " ").slice(0, 19);
}

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TableSyncItem) => void;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<TableSyncItem> {
  return [
    { title: "源数据库", dataIndex: "sourceDataSourceName", key: "sourceDataSourceName", width: 180, ellipsis: true },
    { title: "源数据库名称", dataIndex: "sourceDatabaseName", key: "sourceDatabaseName", width: 140 },
    { title: "目标数据库", dataIndex: "targetDataSourceName", key: "targetDataSourceName", width: 180, ellipsis: true },
    { title: "目标数据库名称", dataIndex: "targetDatabaseName", key: "targetDatabaseName", width: 140 },
    { title: "操作时间", dataIndex: "operateTime", key: "operateTime", width: 180, render: formatTime },
    {
      title: "同步类型",
      dataIndex: "operateType",
      key: "operateType",
      width: 120,
      render: (value) =>
        Number(value) === 0 ? <Tag color="green">批量创建</Tag> : <Tag color="orange">批量同步</Tag>,
    },
    {
      title: "操作",
      key: "action",
      width: 100,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => onAction(PageActionEnum.VIEW, record)}>
            详情
          </Button>
        </Space>
      ),
    },
  ];
}

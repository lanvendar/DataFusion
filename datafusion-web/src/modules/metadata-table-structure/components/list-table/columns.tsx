import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageActionEnum, type TableStructureItem } from "../../dto";

function formatTime(value: unknown) {
  if (value == null || value === "") return "-";
  const text = String(value);
  if (!text.includes("T")) return text;
  return text.replace("T", " ").slice(0, 19);
}

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TableStructureItem) => void;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<TableStructureItem> {
  return [
    { title: "数据源链接名称", dataIndex: "databaseConnectName", key: "databaseConnectName", width: 180, ellipsis: true },
    { title: "数据源名称", dataIndex: "databaseName", key: "databaseName", width: 140 },
    { title: "数据库类型", dataIndex: "databaseType", key: "databaseType", width: 120 },
    { title: "表空间名称", dataIndex: "schemaName", key: "schemaName", width: 130 },
    { title: "表名称", dataIndex: "tableName", key: "tableName", width: 180, ellipsis: true },
    { title: "表注释", dataIndex: "tableDesc", key: "tableDesc", width: 180, ellipsis: true },
    {
      title: "检查时间",
      dataIndex: "checkTime",
      key: "checkTime",
      width: 170,
      render: formatTime,
    },
    {
      title: "是否同步",
      dataIndex: "isModify",
      key: "isModify",
      width: 110,
      render: (value) => <Tag color={value ? "green" : "default"}>{value ? "已同步" : "未同步"}</Tag>,
    },
    {
      title: "是否视图",
      dataIndex: "isView",
      key: "isView",
      width: 100,
      render: (value) => <Tag color={value ? "blue" : "default"}>{value ? "视图" : "表"}</Tag>,
    },
    {
      title: "操作",
      key: "action",
      width: 260,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => onAction(PageActionEnum.VIEW, record)}>
            详情
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认更新表结构？"
            description="将删除当前表结构元数据，再通过数据源重新登记该表。"
            onConfirm={() => onAction(PageActionEnum.UPDATE_STRUCTURE, record)}
          >
            <Button type="link" icon={<SyncOutlined />}>
              更新
            </Button>
          </Popconfirm>
          <Popconfirm title="确认删除该表结构吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

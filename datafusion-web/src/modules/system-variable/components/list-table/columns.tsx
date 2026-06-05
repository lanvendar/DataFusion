import { DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageActionEnum, type VariableItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: VariableItem) => void;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<VariableItem> {
  return [
    { title: "变量编码", dataIndex: "code", key: "code", width: 180, ellipsis: true },
    { title: "变量名称", dataIndex: "name", key: "name", width: 180, ellipsis: true },
    {
      title: "变量类型",
      dataIndex: "type",
      key: "type",
      width: 120,
      render: (value: string) => (
        <Tag color={value === "SYSTEM" ? "gold" : "blue"}>{value || "-"}</Tag>
      ),
    },
    { title: "值类型", dataIndex: "valueType", key: "valueType", width: 130 },
    { title: "变量值", dataIndex: "value", key: "value", width: 220, ellipsis: true },
    { title: "更新人", dataIndex: "updater", key: "updater", width: 120 },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180 },
    {
      title: "操作",
      key: "action",
      width: 160,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该变量吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />} disabled={record.type === "SYSTEM"}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

import { DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { policyOptions } from "../../constants";
import { PageActionEnum, type TriggerItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TriggerItem) => void;
}

function renderPolicy(value?: string) {
  return policyOptions.find((item) => item.value === value)?.label || value || "-";
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<TriggerItem> {
  return [
    { title: "触发器名称", dataIndex: "name", key: "name", width: 180, ellipsis: true },
    {
      title: "触发器类型",
      dataIndex: "type",
      key: "type",
      width: 130,
      render: (value: string) => (
        <Tag color={value === "CRON" ? "blue" : "green"}>{value || "-"}</Tag>
      ),
    },
    {
      title: "调度策略",
      dataIndex: "policy",
      key: "policy",
      width: 140,
      render: renderPolicy,
    },
    { title: "CRON 表达式", dataIndex: "cron", key: "cron", width: 180, ellipsis: true },
    { title: "周期间隔（分钟）", dataIndex: "interval", key: "interval", width: 150 },
    { title: "创建人", dataIndex: "creator", key: "creator", width: 120 },
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
          <Popconfirm title="确认删除该触发器吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

import { DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { eventTypeOptions } from "../../constants";
import { PageActionEnum, type EventItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: EventItem) => void;
}

function renderEventType(value?: string) {
  const label = eventTypeOptions.find((item) => item.value === value)?.label || value || "-";
  return <Tag color={value === "2" ? "purple" : "blue"}>{label}</Tag>;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<EventItem> {
  return [
    { title: "事件名称", dataIndex: "eventName", key: "eventName", width: 180, ellipsis: true },
    {
      title: "事件类型",
      dataIndex: "eventType",
      key: "eventType",
      width: 120,
      render: renderEventType,
    },
    { title: "关联流程ID", dataIndex: "flowId", key: "flowId", width: 220, ellipsis: true },
    { title: "关联任务ID", dataIndex: "taskId", key: "taskId", width: 220, ellipsis: true },
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
          <Popconfirm title="确认删除该事件吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

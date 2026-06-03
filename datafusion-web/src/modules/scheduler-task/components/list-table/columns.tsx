import { DeleteOutlined, EditOutlined, EyeOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EMPTY_PLACEHOLDER, taskTypeColorMap } from "../../constants";
import { PageActionEnum, type TaskItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TaskItem) => void;
}

function renderTaskType(value?: string) {
  return <Tag color={value ? taskTypeColorMap[value] || "default" : "default"}>{value || EMPTY_PLACEHOLDER}</Tag>;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<TaskItem> {
  return [
    { title: "任务名称", dataIndex: "taskName", key: "taskName", width: 180, ellipsis: true },
    { title: "任务编码", dataIndex: "taskCode", key: "taskCode", width: 180, ellipsis: true },
    {
      title: "任务类型",
      dataIndex: "taskType",
      key: "taskType",
      width: 120,
      render: renderTaskType,
    },
    { title: "任务描述", dataIndex: "description", key: "description", width: 220, ellipsis: true },
    { title: "创建人", dataIndex: "creator", key: "creator", width: 120 },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180 },
    {
      title: "操作",
      key: "action",
      width: 220,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => onAction(PageActionEnum.VIEW, record)}>
            查看
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
            编辑
          </Button>
          <Popconfirm
            title={record.isBound ? "该任务已绑定流程，请先解绑后再删除" : "确认删除该任务吗？"}
            onConfirm={() => onAction(PageActionEnum.DELETE, record)}
            okButtonProps={{ disabled: record.isBound }}
          >
            <Button type="link" danger icon={<DeleteOutlined />} disabled={record.isBound}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

import { CopyOutlined, DeleteOutlined, EditOutlined, EyeOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EMPTY_PLACEHOLDER } from "../../constants";
import { PageActionEnum, type TaskItem } from "../../dto";
import { getTaskTypeColor } from "../../utils";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TaskItem) => void;
}

function renderTaskType(value?: string) {
  return <Tag color={getTaskTypeColor(value)}>{value || EMPTY_PLACEHOLDER}</Tag>;
}

function renderBound(value?: boolean) {
  return <Tag color={value ? "blue" : "default"}>{value ? "已绑定" : "未绑定"}</Tag>;
}

function renderEnabled(value?: boolean) {
  return <Tag color={value ? "green" : "default"}>{value ? "已启用" : "未启用"}</Tag>;
}

function renderSyncFlag(value?: boolean) {
  return <Tag color={value ? "success" : "warning"}>{value ? "已同步" : "未同步"}</Tag>;
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
    { title: "是否绑定流程", dataIndex: "isBound", key: "isBound", width: 130, render: renderBound },
    { title: "是否启用", dataIndex: "enabled", key: "enabled", width: 110, render: renderEnabled },
    { title: "是否同步", dataIndex: "syncFlag", key: "syncFlag", width: 110, render: renderSyncFlag },
    { title: "创建人", dataIndex: "creator", key: "creator", width: 120 },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180 },
    {
      title: "操作",
      key: "action",
      width: 280,
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
            title="确认复制该任务吗？"
            onConfirm={() => onAction(PageActionEnum.COPY, record)}
          >
            <Button type="link" icon={<CopyOutlined />}>
              复制
            </Button>
          </Popconfirm>
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

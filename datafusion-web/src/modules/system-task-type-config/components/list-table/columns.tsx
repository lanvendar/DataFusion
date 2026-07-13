import { DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageActionEnum, type TaskTypeConfigItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: TaskTypeConfigItem) => void;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<TaskTypeConfigItem> {
  return [
    {
      title: "任务类型",
      dataIndex: "taskType",
      key: "taskType",
      width: 160,
      render: (value: string) => <Tag color="blue">{value || "-"}</Tag>,
    },
    {
      title: "默认插件",
      dataIndex: "defaultPluginName",
      key: "defaultPluginName",
      width: 300,
      render: (value: string) => (
        <Typography.Text ellipsis={{ tooltip: value }}>
          {value || "-"}
        </Typography.Text>
      ),
    },
    {
      title: "插件类型",
      dataIndex: "pluginType",
      key: "pluginType",
      width: 140,
      render: (value: string) => <Tag>{value || "-"}</Tag>,
    },
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
          <Popconfirm title="确认删除该任务类型配置吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

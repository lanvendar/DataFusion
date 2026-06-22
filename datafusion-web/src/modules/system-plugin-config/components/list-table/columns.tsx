import { CopyOutlined, DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageActionEnum, type PluginConfigItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: PluginConfigItem) => void;
}

function renderPluginParam(value: unknown) {
  if (!value) return "-";
  return (
    <Typography.Text code ellipsis>
      {typeof value === "string" ? value : JSON.stringify(value)}
    </Typography.Text>
  );
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<PluginConfigItem> {
  return [
    { title: "插件名称", dataIndex: "pluginName", key: "pluginName", width: 180, ellipsis: true },
    {
      title: "插件类型",
      dataIndex: "pluginType",
      key: "pluginType",
      width: 130,
      render: (value: string) => <Tag color="blue">{value || "-"}</Tag>,
    },
    {
      title: "运行模式",
      dataIndex: "runMode",
      key: "runMode",
      width: 120,
      render: (value: string) => <Tag>{value || "-"}</Tag>,
    },
    {
      title: "模板数据",
      dataIndex: "isTemplate",
      key: "isTemplate",
      width: 110,
      render: (value: boolean) => (
        <Tag color={value ? "gold" : "default"}>{value ? "是" : "否"}</Tag>
      ),
    },
    { title: "描述", dataIndex: "description", key: "description", width: 220, ellipsis: true },
    { title: "配置", dataIndex: "pluginParam", key: "pluginParam", width: 260, render: renderPluginParam },
    { title: "更新人", dataIndex: "updater", key: "updater", width: 120 },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180 },
    {
      title: "操作",
      key: "action",
      width: 230,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<CopyOutlined />} onClick={() => onAction(PageActionEnum.COPY, record)}>
            复制
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该插件配置吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

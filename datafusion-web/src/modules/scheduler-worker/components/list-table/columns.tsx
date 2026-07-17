import { CheckCircleOutlined, DeleteOutlined, EditOutlined, StopOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { activeOptions, workerStatusOptions } from "../../constants";
import { PageActionEnum, type WorkerRegistryItem } from "../../dto";

interface UseColumnsProps {
  activeLoading?: boolean;
  activeWorkerId?: string;
  onAction: (action: PageActionEnum, record?: WorkerRegistryItem) => void;
}

function renderStatus(value?: number) {
  const label = workerStatusOptions.find((item) => item.value === value)?.label || "-";
  const colorMap: Record<number, string> = {
    0: "default",
    1: "green",
    2: "red",
  };
  return <Tag color={value === undefined ? "default" : colorMap[value]}>{label}</Tag>;
}

function renderActive(value?: number) {
  const label = activeOptions.find((item) => item.value === value)?.label || "-";
  return <Tag color={value === 1 ? "blue" : "default"}>{label}</Tag>;
}

function renderPlugins(value?: string) {
  if (!value) return "-";
  return (
    <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 240 }}>
      {value}
    </Typography.Text>
  );
}

function renderTime(value?: string) {
  if (!value) return "-";
  const time = dayjs(value);
  if (!time.isValid()) return value;
  return time.format("YYYY-MM-DD HH:mm:ss");
}

export function useColumns({
  activeLoading,
  activeWorkerId,
  onAction,
}: UseColumnsProps): ColumnsType<WorkerRegistryItem> {
  return [
    { title: "节点编码", dataIndex: "workerCode", key: "workerCode", width: 180, ellipsis: true },
    { title: "主机名称", dataIndex: "hostName", key: "hostName", width: 160, ellipsis: true },
    { title: "IP 地址", dataIndex: "host", key: "host", width: 150, ellipsis: true },
    { title: "端口", dataIndex: "port", key: "port", width: 90 },
    { title: "在线状态", dataIndex: "status", key: "status", width: 110, render: renderStatus },
    { title: "调度状态", dataIndex: "isActive", key: "isActive", width: 110, render: renderActive },
    { title: "区域", dataIndex: "zone", key: "zone", width: 120, ellipsis: true },
    { title: "插件", dataIndex: "plugins", key: "plugins", width: 260, render: renderPlugins },
    { title: "日志目录", dataIndex: "workerLogDir", key: "workerLogDir", width: 280, ellipsis: true },
    { title: "注册时间", dataIndex: "registerTime", key: "registerTime", width: 180, render: renderTime },
    { title: "最近心跳", dataIndex: "lastHeartbeatTime", key: "lastHeartbeatTime", width: 180, render: renderTime },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180, render: renderTime },
    {
      title: "操作",
      key: "action",
      width: 240,
      fixed: "right",
      render: (_, record) => {
        const isEnabled = record.isActive === 1;
        const isCurrentActionPending = activeLoading && activeWorkerId === record.id;
        return (
          <Space>
            <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
              编辑
            </Button>
            <Popconfirm
              title={`确认${isEnabled ? "禁用" : "启用"}该执行节点吗？`}
              description={isEnabled ? "仅阻止新任务调度，不影响正在运行的任务。" : undefined}
              onConfirm={() => onAction(PageActionEnum.ACTIVE, record)}
            >
              <Button
                type="link"
                danger={isEnabled}
                disabled={activeLoading}
                loading={isCurrentActionPending}
                icon={isEnabled ? <StopOutlined /> : <CheckCircleOutlined />}
              >
                {isEnabled ? "禁用" : "启用"}
              </Button>
            </Popconfirm>
            <Popconfirm title="确认删除该执行节点吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];
}

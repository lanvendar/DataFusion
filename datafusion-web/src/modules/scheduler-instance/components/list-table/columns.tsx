import { ReloadOutlined } from "@ant-design/icons";
import { Button, Space, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ACTION_COLUMN_WIDTH,
  INSTANCE_COLUMN_WIDTH,
  MIDDLE_COLUMN_WIDTH,
  STATUS_COLUMN_WIDTH,
  TIME_COLUMN_WIDTH,
  TYPE_COLUMN_WIDTH,
} from "../../constants";
import type { FlowInstanceItem } from "../../dto";
import {
  formatTime,
  renderCopyableId,
  renderStatus,
  renderTimeBlock,
  renderType,
} from "../../utils";

interface UseColumnsProps {
  onRefresh: () => void;
}

export function useColumns({ onRefresh }: UseColumnsProps): ColumnsType<FlowInstanceItem> {
  return [
    {
      title: "流程实例",
      dataIndex: "flowName",
      key: "flowName",
      width: INSTANCE_COLUMN_WIDTH,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ maxWidth: "100%" }}>
          <Typography.Text ellipsis={{ tooltip: record.flowName }} strong style={{ maxWidth: "100%" }}>
            {record.flowName || "-"}
          </Typography.Text>
          {renderCopyableId(record.id)}
        </Space>
      ),
    },
    {
      title: "流程类型",
      dataIndex: "flowType",
      key: "flowType",
      width: TYPE_COLUMN_WIDTH,
      render: renderType,
    },
    {
      title: "实例状态",
      dataIndex: "status",
      key: "status",
      width: STATUS_COLUMN_WIDTH,
      render: renderStatus,
    },
    {
      title: "调度时间",
      dataIndex: "scheduleTime",
      key: "scheduleTime",
      width: MIDDLE_COLUMN_WIDTH,
      render: formatTime,
    },
    {
      title: "起止时间",
      key: "time",
      width: TIME_COLUMN_WIDTH,
      render: (_, record) => renderTimeBlock(record.startTime, record.endTime, record.duration),
    },
    {
      title: "操作",
      key: "action",
      width: ACTION_COLUMN_WIDTH,
      render: () => (
        <Button type="link" icon={<ReloadOutlined />} onClick={onRefresh}>
          刷新
        </Button>
      ),
    },
  ];
}

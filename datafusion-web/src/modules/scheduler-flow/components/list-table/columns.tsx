import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  RocketOutlined,
  ShareAltOutlined,
  StopOutlined,
} from "@ant-design/icons";
import { Button, Popconfirm, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  EMPTY_PLACEHOLDER,
  flowTypeColorMap,
  flowTypeLabelMap,
} from "../../constants";
import { PageActionEnum, type FlowItem } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: FlowItem) => void;
}

function renderFlowType(value?: string) {
  return (
    <Tag color={value ? flowTypeColorMap[value] || "default" : "default"}>
      {value ? flowTypeLabelMap[value] || value : EMPTY_PLACEHOLDER}
    </Tag>
  );
}

function renderPublishState(value?: boolean) {
  return <Tag color={value ? "blue" : "default"}>{value ? "已发布" : "未发布"}</Tag>;
}

function renderEnabled(value?: boolean) {
  return <Tag color={value ? "green" : "default"}>{value ? "调度中" : "未调度"}</Tag>;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<FlowItem> {
  return [
    { title: "流程名称", dataIndex: "flowName", key: "flowName", width: 180, ellipsis: true },
    { title: "流程编码", dataIndex: "flowCode", key: "flowCode", width: 180, ellipsis: true },
    {
      title: "流程类型",
      dataIndex: "flowType",
      key: "flowType",
      width: 140,
      render: renderFlowType,
    },
    { title: "触发器", dataIndex: "triggerName", key: "triggerName", width: 160, ellipsis: true },
    {
      title: "发布状态",
      dataIndex: "publishState",
      key: "publishState",
      width: 110,
      render: renderPublishState,
    },
    {
      title: "调度状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 110,
      render: renderEnabled,
    },
    { title: "描述", dataIndex: "description", key: "description", width: 220, ellipsis: true },
    { title: "更新时间", dataIndex: "updateTime", key: "updateTime", width: 180 },
    {
      title: "操作",
      key: "action",
      width: 420,
      fixed: "right",
      render: (_, record) => {
        const published = Boolean(record.publishState);
        const enabled = Boolean(record.enabled);
        const readOnly = published || enabled;

        return (
          <Space>
            <Button type="link" icon={<EyeOutlined />} onClick={() => onAction(PageActionEnum.VIEW, record)}>
              查看
            </Button>
            <Button
              type="link"
              icon={<EditOutlined />}
              disabled={readOnly}
              onClick={() => onAction(PageActionEnum.EDIT, record)}
            >
              编辑
            </Button>
            <Button type="link" icon={<ShareAltOutlined />} onClick={() => onAction(PageActionEnum.DAG_EDIT, record)}>
              {readOnly ? "查看编排" : "编排"}
            </Button>
            {published ? (
              <Button type="link" icon={<StopOutlined />} onClick={() => onAction(PageActionEnum.UNPUBLISH, record)}>
                取消发布
              </Button>
            ) : (
              <Button type="link" icon={<RocketOutlined />} onClick={() => onAction(PageActionEnum.PUBLISH, record)}>
                发布
              </Button>
            )}
            {enabled ? (
              <Button type="link" icon={<PauseCircleOutlined />} onClick={() => onAction(PageActionEnum.DISABLE, record)}>
                取消调度
              </Button>
            ) : (
              <Button type="link" icon={<PlayCircleOutlined />} disabled={!published} onClick={() => onAction(PageActionEnum.ENABLE, record)}>
                开始调度
              </Button>
            )}
            <Popconfirm
              title={readOnly ? "流程已发布或调度中，无法删除" : "确认删除该流程吗？"}
              onConfirm={() => onAction(PageActionEnum.DELETE, record)}
              okButtonProps={{ disabled: readOnly }}
            >
              <Button type="link" danger icon={<DeleteOutlined />} disabled={readOnly}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];
}

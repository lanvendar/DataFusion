import { Button, Descriptions, Drawer, Empty, Space, Spin, Tag } from "antd";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { flowApi } from "../../api";
import {
  EMPTY_PLACEHOLDER,
  flowTypeColorMap,
  flowTypeLabelMap,
} from "../../constants";
import type { FlowItem } from "../../dto";
import { formatJsonText, normalizeStringArray } from "../../utils";
import { JsonEditor } from "../json-editor";

interface FlowDetailProps {
  open: boolean;
  flowId?: string;
  onClose: () => void;
}

function renderBooleanTag(value?: boolean, enabledText = "是", disabledText = "否") {
  return <Tag color={value ? "green" : "default"}>{value ? enabledText : disabledText}</Tag>;
}

function formatScheduleTime(value?: number | string) {
  if (!value) return EMPTY_PLACEHOLDER;
  return dayjs(Number(value)).format("YYYY-MM-DD HH:mm:ss");
}

export function FlowDetail({ open, flowId, onClose }: FlowDetailProps) {
  const [detailData, setDetailData] = useState<FlowItem>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !flowId) return;

    const fetchDetail = async () => {
      setLoading(true);
      try {
        setDetailData(await flowApi.detail(flowId));
      } finally {
        setLoading(false);
      }
    };

    void fetchDetail();
  }, [flowId, open]);

  const flowParamText = useMemo(
    () => formatJsonText(detailData?.flowParam),
    [detailData?.flowParam],
  );
  const depEventIdsText = normalizeStringArray(detailData?.depEventIds).join(", ") || EMPTY_PLACEHOLDER;

  const handleClose = () => {
    setDetailData(undefined);
    onClose();
  };

  return (
    <Drawer
      title="流程详情"
      open={open}
      width={780}
      onClose={handleClose}
      extra={
        <Space>
          <Button onClick={handleClose}>关闭</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {!detailData ? (
          <Empty description="暂无流程详情" />
        ) : (
          <Space direction="vertical" size={16} className="full-input">
            <Descriptions title="基础信息" column={2} size="small" bordered>
              <Descriptions.Item label="流程名称">
                {detailData.flowName || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="流程编码">
                {detailData.flowCode || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="流程类型">
                <Tag color={flowTypeColorMap[detailData.flowType] || "default"}>
                  {flowTypeLabelMap[detailData.flowType] || detailData.flowType || EMPTY_PLACEHOLDER}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="触发器">
                {detailData.triggerName || detailData.triggerId || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="发布状态">
                {renderBooleanTag(detailData.publishState, "已发布", "未发布")}
              </Descriptions.Item>
              <Descriptions.Item label="调度状态">
                {renderBooleanTag(detailData.enabled, "调度中", "未调度")}
              </Descriptions.Item>
              <Descriptions.Item label="发布版本">
                {detailData.publishVersion || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="流程分组">
                {detailData.groupId || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="调度开始时间">
                {formatScheduleTime(detailData.startTime)}
              </Descriptions.Item>
              <Descriptions.Item label="调度结束时间">
                {formatScheduleTime(detailData.endTime)}
              </Descriptions.Item>
              <Descriptions.Item label="依赖事件" span={2}>
                {depEventIdsText}
              </Descriptions.Item>
              <Descriptions.Item label="产出事件" span={2}>
                {detailData.eventId || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {detailData.description || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
            </Descriptions>

            <Descriptions title="审计信息" column={2} size="small" bordered>
              <Descriptions.Item label="创建人">
                {detailData.creator || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="更新人">
                {detailData.updater || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {detailData.createTime || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {detailData.updateTime || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
            </Descriptions>

            <JsonEditor
              title="流程变量参数 JSON"
              value={flowParamText}
              disabled
              rows={10}
              placeholder="暂无流程变量参数"
            />
          </Space>
        )}
      </Spin>
    </Drawer>
  );
}

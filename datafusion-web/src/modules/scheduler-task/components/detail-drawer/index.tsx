import { Button, Descriptions, Drawer, Empty, Space, Spin, Tag } from "antd";
import { useEffect, useMemo, useState } from "react";
import { taskApi } from "../../api";
import { EMPTY_PLACEHOLDER, taskTypeColorMap } from "../../constants";
import type { TaskItem } from "../../dto";
import { formatJsonText } from "../../utils";
import { JsonEditor } from "../json-editor";

interface TaskDetailProps {
  open: boolean;
  taskId?: string;
  onClose: () => void;
}

export function TaskDetail({ open, taskId, onClose }: TaskDetailProps) {
  const [detailData, setDetailData] = useState<TaskItem>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !taskId) return;

    const fetchDetail = async () => {
      setLoading(true);
      try {
        setDetailData(await taskApi.detail(taskId));
      } finally {
        setLoading(false);
      }
    };

    void fetchDetail();
  }, [open, taskId]);

  const taskParamText = useMemo(
    () => formatJsonText(detailData?.taskParam),
    [detailData?.taskParam],
  );
  const definitionText = useMemo(
    () => formatJsonText(detailData?.definition),
    [detailData?.definition],
  );

  const handleClose = () => {
    setDetailData(undefined);
    onClose();
  };

  return (
    <Drawer
      title="任务详情"
      open={open}
      width={760}
      onClose={handleClose}
      extra={
        <Space>
          <Button onClick={handleClose}>关闭</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {!detailData ? (
          <Empty description="暂无任务详情" />
        ) : (
          <Space direction="vertical" size={16} className="full-input">
            <Descriptions title="基础信息" column={2} size="small" bordered>
              <Descriptions.Item label="任务名称">
                {detailData.taskName || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="任务编码">
                {detailData.taskCode || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
              <Descriptions.Item label="任务类型">
                <Tag color={taskTypeColorMap[detailData.taskType] || "default"}>
                  {detailData.taskType || EMPTY_PLACEHOLDER}
                </Tag>
              </Descriptions.Item>
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
              <Descriptions.Item label="任务描述" span={2}>
                {detailData.description || EMPTY_PLACEHOLDER}
              </Descriptions.Item>
            </Descriptions>

            <JsonEditor
              title="任务参数 JSON"
              value={taskParamText}
              disabled
              placeholder="暂无任务参数"
            />
            <JsonEditor
              title="任务定义 JSON"
              value={definitionText}
              disabled
              rows={10}
              placeholder="暂无任务定义"
            />
          </Space>
        )}
      </Spin>
    </Drawer>
  );
}

import { FileTextOutlined } from "@ant-design/icons";
import { App, Button, Drawer, Segmented, Space, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { taskInstanceApi } from "../../api";
import {
  DEFAULT_LOG_LIMIT,
  EMPTY_PLACEHOLDER,
  logTypeOptions,
} from "../../constants";
import type {
  TaskInstanceItem,
  TaskInstanceLogContent,
  TaskInstanceLogQuery,
  TaskInstanceLogType,
} from "../../dto";

interface TaskLogDrawerProps {
  open: boolean;
  task?: TaskInstanceItem;
  onClose: () => void;
}

export function TaskLogDrawer({ open, task, onClose }: TaskLogDrawerProps) {
  const { message } = App.useApp();
  const [logType, setLogType] = useState<TaskInstanceLogType>("LOG");
  const [logContent, setLogContent] = useState("");
  const [logMeta, setLogMeta] = useState<TaskInstanceLogContent>();
  const [logLoading, setLogLoading] = useState(false);

  const createQuery = useCallback(
    (offset = 0): TaskInstanceLogQuery | undefined => {
      if (!task) return undefined;
      return {
        flowInstanceId: task.flowInstanceId,
        taskInstanceId: task.id,
        logType,
        offset,
        limit: DEFAULT_LOG_LIMIT,
      };
    },
    [logType, task],
  );

  const fetchLog = useCallback(
    async (params: TaskInstanceLogQuery, append: boolean) => {
      setLogLoading(true);
      try {
        const result = await taskInstanceApi.logContent(params);
        setLogMeta(result);
        setLogContent((previous) => (append ? `${previous}${result.content || ""}` : result.content || ""));
      } catch (error) {
        message.error(error instanceof Error ? error.message : "读取日志失败");
      } finally {
        setLogLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (!open || !task) return;
    setLogContent("");
    setLogMeta(undefined);
    const query = createQuery(0);
    if (query) void fetchLog(query, false);
  }, [createQuery, fetchLog, open, task]);

  const loadMoreLog = useCallback(() => {
    if (logMeta?.nextOffset === undefined) return;
    const query = createQuery(logMeta.nextOffset);
    if (query) void fetchLog(query, true);
  }, [createQuery, fetchLog, logMeta?.nextOffset]);

  const handleClose = () => {
    setLogType("LOG");
    setLogContent("");
    setLogMeta(undefined);
    onClose();
  };

  return (
    <Drawer
      title="任务日志"
      open={open}
      width={820}
      onClose={handleClose}
      extra={
        <Segmented
          options={logTypeOptions}
          value={logType}
          onChange={(value) => setLogType(value as TaskInstanceLogType)}
        />
      }
    >
      <Space direction="vertical" size={12} style={{ width: "100%" }}>
        {logMeta?.path ? <Typography.Text type="secondary">{logMeta.path}</Typography.Text> : null}
        <pre
          style={{
            minHeight: 360,
            margin: 0,
            padding: 12,
            background: "#111827",
            color: "#e5e7eb",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
          }}
        >
          {logContent || EMPTY_PLACEHOLDER}
        </pre>
        <Button
          icon={<FileTextOutlined />}
          loading={logLoading}
          disabled={!logMeta?.hasMore}
          onClick={loadMoreLog}
        >
          加载更多
        </Button>
      </Space>
    </Drawer>
  );
}

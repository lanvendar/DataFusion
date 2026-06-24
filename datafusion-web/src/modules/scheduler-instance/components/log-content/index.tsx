import { FileTextOutlined } from "@ant-design/icons";
import { App, Button, Space, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { taskInstanceApi } from "../../api";
import {
  DEFAULT_LOG_LIMIT,
  EMPTY_PLACEHOLDER,
} from "../../constants";
import type {
  TaskInstanceLogContent,
  TaskInstanceLogQuery,
  TaskInstanceLogType,
} from "../../dto";

interface TaskLogContentProps {
  flowInstanceId?: string;
  taskInstanceId?: string;
  logType: TaskInstanceLogType;
}

export function TaskLogContent({
  flowInstanceId,
  taskInstanceId,
  logType,
}: TaskLogContentProps) {
  const { message } = App.useApp();
  const [logContent, setLogContent] = useState("");
  const [logMeta, setLogMeta] = useState<TaskInstanceLogContent>();
  const [logLoading, setLogLoading] = useState(false);

  const createQuery = useCallback(
    (offset = 0): TaskInstanceLogQuery | undefined => {
      if (!flowInstanceId || !taskInstanceId) return undefined;
      return {
        flowInstanceId,
        taskInstanceId,
        logType,
        offset,
        limit: DEFAULT_LOG_LIMIT,
      };
    },
    [flowInstanceId, logType, taskInstanceId],
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
    setLogContent("");
    setLogMeta(undefined);
    const query = createQuery(0);
    if (query) void fetchLog(query, false);
  }, [createQuery, fetchLog]);

  const loadMoreLog = useCallback(() => {
    if (logMeta?.nextOffset === undefined) return;
    const query = createQuery(logMeta.nextOffset);
    if (query) void fetchLog(query, true);
  }, [createQuery, fetchLog, logMeta?.nextOffset]);

  return (
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
  );
}

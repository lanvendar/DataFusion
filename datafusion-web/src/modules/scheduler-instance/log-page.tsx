import { ReloadOutlined } from "@ant-design/icons";
import { Button, Card, Space, Typography } from "antd";
import { useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { PageHeader } from "@/components/page-layout";
import { logTypeOptions } from "./constants";
import type { TaskInstanceLogType } from "./dto";
import { TaskLogContent, type TaskLogContentRef } from "./components";

export default function SchedulerTaskLogPage() {
  const [searchParams] = useSearchParams();
  const [refreshing, setRefreshing] = useState(false);
  const logContentRef = useRef<TaskLogContentRef>(null);
  const flowInstanceId = searchParams.get("flowInstanceId") || undefined;
  const taskInstanceId = searchParams.get("taskInstanceId") || undefined;
  const taskName = searchParams.get("taskName") || "任务日志";
  const logTypeOption = logTypeOptions.find((item) => item.value === searchParams.get("logType"))
    || logTypeOptions[0];
  const logType = logTypeOption.value as TaskInstanceLogType;

  const refreshLog = async () => {
    setRefreshing(true);
    try {
      await logContentRef.current?.refresh();
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "调度中心" }, { label: "实例查询" }, { label: logTypeOption.label }]}
        title={
          <Space>
            <span>{logTypeOption.label}</span>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              loading={refreshing}
              disabled={!flowInstanceId || !taskInstanceId}
              onClick={() => void refreshLog()}
            >
              刷新
            </Button>
          </Space>
        }
        description={taskName}
      />
      <Card>
        {flowInstanceId && taskInstanceId ? (
          <TaskLogContent
            ref={logContentRef}
            flowInstanceId={flowInstanceId}
            taskInstanceId={taskInstanceId}
            logType={logType}
          />
        ) : (
          <Typography.Text type="secondary">缺少任务实例参数</Typography.Text>
        )}
      </Card>
    </Space>
  );
}

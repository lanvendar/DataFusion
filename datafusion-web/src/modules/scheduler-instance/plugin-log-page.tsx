import { Card, Space, Typography } from "antd";
import { useSearchParams } from "react-router-dom";
import { PageHeader } from "@/components/page-layout";
import { TaskLogContent } from "./components";

export default function SchedulerPluginLogPage() {
  const [searchParams] = useSearchParams();
  const flowInstanceId = searchParams.get("flowInstanceId") || undefined;
  const taskInstanceId = searchParams.get("taskInstanceId") || undefined;
  const taskName = searchParams.get("taskName") || "插件日志";

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "调度中心" }, { label: "实例查询" }, { label: "插件日志" }]}
        title="插件日志"
        description={taskName}
      />
      <Card>
        {flowInstanceId && taskInstanceId ? (
          <TaskLogContent flowInstanceId={flowInstanceId} taskInstanceId={taskInstanceId} logType="PLUGIN" />
        ) : (
          <Typography.Text type="secondary">缺少任务实例参数</Typography.Text>
        )}
      </Card>
    </Space>
  );
}

import { Descriptions, Drawer, Space, Typography } from "antd";
import { EMPTY_PLACEHOLDER } from "../../constants";
import type { FlowInstanceItem, TaskInstanceItem } from "../../dto";
import { formatJson } from "../../utils";

export interface DependencyState {
  flow: FlowInstanceItem;
  task: TaskInstanceItem;
}

interface TaskDependencyDrawerProps {
  dependency?: DependencyState;
  onClose: () => void;
}

export function TaskDependencyDrawer({ dependency, onClose }: TaskDependencyDrawerProps) {
  return (
    <Drawer
      title="任务依赖图"
      open={Boolean(dependency)}
      width={720}
      onClose={onClose}
    >
      {dependency ? (
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="流程实例ID">{dependency.flow.id}</Descriptions.Item>
            <Descriptions.Item label="任务实例ID">{dependency.task.id}</Descriptions.Item>
            <Descriptions.Item label="上游任务实例">
              {dependency.task.lastInstanceId || EMPTY_PLACEHOLDER}
            </Descriptions.Item>
            <Descriptions.Item label="下游任务实例">
              {dependency.task.nextInstanceId || EMPTY_PLACEHOLDER}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text strong>DAG Snapshot</Typography.Text>
          <pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
            {formatJson(dependency.flow.flowDagSnapshot)}
          </pre>
        </Space>
      ) : null}
    </Drawer>
  );
}

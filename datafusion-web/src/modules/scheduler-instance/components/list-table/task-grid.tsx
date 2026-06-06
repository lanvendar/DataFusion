import { Button, Space, Typography } from "antd";
import { useQuery } from "@tanstack/react-query";
import { taskInstanceApi } from "../../api";
import {
  CHILD_GRID_TEMPLATE,
  CHILD_TABLE_WIDTH,
  EMPTY_PLACEHOLDER,
  SCHEDULER_INSTANCE_TASK_QUERY_KEY,
} from "../../constants";
import type {
  FlowInstanceItem,
  SchedulerInstanceViewType,
  TaskInstanceItem,
} from "../../dto";
import {
  renderCopyableId,
  renderStatus,
  renderTimeBlock,
  renderType,
} from "../../utils";

interface TaskInstanceGridProps {
  flow: FlowInstanceItem;
  viewType: SchedulerInstanceViewType;
  onOpenDependency: (flow: FlowInstanceItem, task: TaskInstanceItem) => void;
  onOpenLog: (task: TaskInstanceItem) => void;
}

export function TaskInstanceGrid({
  flow,
  viewType,
  onOpenDependency,
  onOpenLog,
}: TaskInstanceGridProps) {
  const query = useQuery({
    queryKey: [SCHEDULER_INSTANCE_TASK_QUERY_KEY, flow.id, viewType],
    queryFn: () => taskInstanceApi.listByFlowInstance({ flowInstanceId: flow.id, viewType }),
  });

  const tasks = query.data || [];

  return (
    <div className="scheduler-instance-expanded-content" style={{ width: CHILD_TABLE_WIDTH }}>
      <div className="scheduler-instance-task-grid">
        <div className="scheduler-instance-task-grid-header" style={{ gridTemplateColumns: CHILD_GRID_TEMPLATE }}>
          <div>任务实例</div>
          <div>任务类型</div>
          <div>任务状态</div>
          <div>返回结果</div>
          <div>起止时间</div>
          <div>操作</div>
        </div>
        {query.isFetching && tasks.length === 0 ? (
          <div className="scheduler-instance-task-grid-empty">加载中...</div>
        ) : null}
        {!query.isFetching && tasks.length === 0 ? (
          <div className="scheduler-instance-task-grid-empty">暂无任务实例</div>
        ) : null}
        {tasks.map((record) => (
          <div
            className="scheduler-instance-task-grid-row"
            key={record.id}
            style={{ gridTemplateColumns: CHILD_GRID_TEMPLATE }}
          >
            <div className="scheduler-instance-task-grid-cell">
              <Space direction="vertical" size={2} style={{ maxWidth: "100%" }}>
                <Typography.Link
                  onClick={() => onOpenLog(record)}
                  style={{
                    display: "block",
                    maxWidth: "100%",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                  title={record.taskName}
                >
                  {record.taskName || EMPTY_PLACEHOLDER}
                </Typography.Link>
                {renderCopyableId(record.id)}
              </Space>
            </div>
            <div className="scheduler-instance-task-grid-cell">{renderType(record.taskType)}</div>
            <div className="scheduler-instance-task-grid-cell">{renderStatus(record.status)}</div>
            <div className="scheduler-instance-task-grid-cell">
              <Typography.Text ellipsis={{ tooltip: record.workerResultText || record.logPath }}>
                {record.workerResultText || record.logPath || EMPTY_PLACEHOLDER}
              </Typography.Text>
            </div>
            <div className="scheduler-instance-task-grid-cell">
              {renderTimeBlock(record.startTime, record.endTime, record.costTime)}
            </div>
            <div className="scheduler-instance-task-grid-cell">
              <Space size={0}>
                <Button type="link" onClick={() => onOpenDependency(flow, record)}>
                  查看依赖图
                </Button>
                <Button type="link" onClick={() => onOpenLog(record)}>
                  查看日志
                </Button>
              </Space>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

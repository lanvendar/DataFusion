import { ReloadOutlined } from "@ant-design/icons";
import { App, Button, Space, Typography } from "antd";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { taskInstanceApi } from "../../api";
import {
  EMPTY_PLACEHOLDER,
  EXPANDED_TASK_GRID_TEMPLATE,
  SCHEDULER_INSTANCE_TASK_QUERY_KEY,
} from "../../constants";
import type {
  FlowInstanceItem,
  SchedulerInstanceViewType,
  TaskInstanceItem,
} from "../../dto";
import {
  getTaskPluginLogUri,
  getTaskWorkerResultSummary,
  getTaskWorkDirPath,
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
  onTaskAction: (flow: FlowInstanceItem, task: TaskInstanceItem, actionType: string) => void;
}

export function TaskInstanceGrid({
  flow,
  viewType,
  onOpenDependency,
  onOpenLog,
  onTaskAction,
}: TaskInstanceGridProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: [SCHEDULER_INSTANCE_TASK_QUERY_KEY, flow.id, viewType],
    queryFn: () => taskInstanceApi.listByFlowInstance({ flowInstanceId: flow.id, viewType }),
  });

  const tasks = query.data || [];

  const refreshTask = async (record: TaskInstanceItem) => {
    const task = await taskInstanceApi.detail(record.id);
    queryClient.setQueryData<TaskInstanceItem[]>(
      [SCHEDULER_INSTANCE_TASK_QUERY_KEY, flow.id, viewType],
      (items) => items?.map((item) => (item.id === task.id ? { ...item, ...task } : item)) || [task],
    );
  };

  const openPluginLog = (record: TaskInstanceItem) => {
    const params = new URLSearchParams({
      flowInstanceId: record.flowInstanceId,
      taskInstanceId: record.id,
      taskName: record.taskName || record.id,
    });
    window.open(`/scheduler-instance/plugin-log?${params.toString()}`, "_blank", "noopener,noreferrer");
  };

  const openWorkDir = (record: TaskInstanceItem) => {
    window.open(`/api/scheduler/task/instance/log/filebrowser/${record.id}`, "_blank");
  };

  return (
    <div className="scheduler-instance-expanded-content">
      <div className="scheduler-instance-task-grid">
        {/* TODO: 后续可将父表和子表统一改为同一套 CSS Grid 渲染，彻底避免 AntD Table + 子 grid 在不同分辨率下列宽漂移。 */}
        <div className="scheduler-instance-task-grid-header" style={{ gridTemplateColumns: EXPANDED_TASK_GRID_TEMPLATE }}>
          <div className="scheduler-instance-task-grid-placeholder" />
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
        {tasks.map((record) => {
          const pluginLogUri = getTaskPluginLogUri(record);
          const workDirPath = getTaskWorkDirPath(record);
          const resultSummary = getTaskWorkerResultSummary(record);

          return (
            <div
              className="scheduler-instance-task-grid-row"
              key={record.id}
              style={{ gridTemplateColumns: EXPANDED_TASK_GRID_TEMPLATE }}
            >
            <div className="scheduler-instance-task-grid-cell scheduler-instance-task-grid-placeholder" />
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
              <Space direction="vertical" size={2} style={{ maxWidth: "100%" }}>
                <Typography.Text ellipsis={{ tooltip: resultSummary }} style={{ maxWidth: "100%" }}>
                  {resultSummary}
                </Typography.Text>
                {workDirPath || pluginLogUri ? (
                  <Space size={8}>
                    {workDirPath ? (
                      <Typography.Link onClick={() => openWorkDir(record)}>
                        打开目录
                      </Typography.Link>
                    ) : null}
                    {pluginLogUri ? (
                      <Typography.Link onClick={() => openPluginLog(record)}>
                        插件日志
                      </Typography.Link>
                    ) : null}
                  </Space>
                ) : null}
              </Space>
            </div>
            <div className="scheduler-instance-task-grid-cell">
              {renderTimeBlock(record.startTime, record.endTime, record.costTime)}
            </div>
            <div className="scheduler-instance-task-grid-cell scheduler-instance-task-grid-action-cell">
              <Space className="scheduler-instance-actions" size={[4, 4]} wrap>
                <Button
                  type="link"
                  icon={<ReloadOutlined />}
                  onClick={() => void refreshTask(record).catch((error) => {
                    message.error(error instanceof Error ? error.message : "刷新失败");
                  })}
                >
                  刷新
                </Button>
                <Button type="link" onClick={() => onOpenDependency(flow, record)}>
                  查看依赖图
                </Button>
                <Button type="link" onClick={() => onOpenLog(record)}>
                  查看日志
                </Button>
                {(record.availableActions || []).map((action) => (
                  <Button
                    key={action.actionType}
                    type="link"
                    onClick={() => onTaskAction(flow, record, action.actionType)}
                  >
                    {action.label}
                  </Button>
                ))}
              </Space>
            </div>
          </div>
          );
        })}
      </div>
    </div>
  );
}

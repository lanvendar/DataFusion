import { App, Card, Segmented, Space, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { flowInstanceApi, taskInstanceApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  EXPAND_COLUMN_WIDTH,
  SCHEDULER_INSTANCE_TASK_QUERY_KEY,
  TABLE_SCROLL_X,
  viewTypeOptions,
} from "../../constants";
import type {
  FlowInstanceItem,
  PageResponse,
  SchedulerInstanceActionType,
  SchedulerInstanceViewType,
  TaskInstanceItem,
} from "../../dto";
import { getRows } from "../../utils";
import { useColumns } from "./columns";
import { ListToolbar } from "./toolbar";
import { getPagination } from "./pagination";
import { TaskInstanceGrid } from "./task-grid";
import { useSchedulerInstanceListQuery } from "./use-list-query";

interface SchedulerInstanceListTableProps {
  onOpenDependency: (flow: FlowInstanceItem, task: TaskInstanceItem) => void;
  onOpenLog: (task: TaskInstanceItem) => void;
}

function replaceFlowInstance(
  page: PageResponse<FlowInstanceItem> | undefined,
  flow: FlowInstanceItem,
) {
  if (!page) {
    return page;
  }
  const replaceRows = (rows?: FlowInstanceItem[]) => rows?.map((item) => (
    item.id === flow.id ? { ...item, ...flow } : item
  ));
  return {
    ...page,
    dataList: replaceRows(page.dataList),
    records: replaceRows(page.records),
    list: replaceRows(page.list),
  };
}

export function SchedulerInstanceListTable({
  onOpenDependency,
  onOpenLog,
}: SchedulerInstanceListTableProps) {
  const { message, modal } = App.useApp();
  const {
    filter,
    setFilter,
    viewType,
    setViewType,
    query,
    search,
    reset,
    setCurrent,
    setPageSize,
    queryKey,
  } = useSchedulerInstanceListQuery();
  const queryClient = useQueryClient();
  const flowActionMutation = useMutation({
    mutationFn: flowInstanceApi.action,
    onSuccess: async () => {
      message.success("操作已提交");
      await query.refetch();
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : "操作提交失败");
    },
  });
  const taskActionMutation = useMutation({
    mutationFn: taskInstanceApi.action,
    onSuccess: async (_, variables) => {
      message.success("操作已提交");
      await Promise.all([
        query.refetch(),
        queryClient.invalidateQueries({
          queryKey: [SCHEDULER_INSTANCE_TASK_QUERY_KEY, variables.flowInstanceId, viewType],
        }),
      ]);
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : "操作提交失败");
    },
  });

  const refreshFlowInstance = async (record: FlowInstanceItem) => {
    const [flow] = await Promise.all([
      flowInstanceApi.detail(record.id),
      queryClient.fetchQuery({
        queryKey: [SCHEDULER_INSTANCE_TASK_QUERY_KEY, record.id, viewType],
        queryFn: () => taskInstanceApi.listByFlowInstance({
          flowInstanceId: record.id,
          viewType,
        }),
      }),
    ]);
    queryClient.setQueryData<PageResponse<FlowInstanceItem>>(queryKey, (page) => (
      replaceFlowInstance(page, flow)
    ));
  };

  const handleFlowAction = (record: FlowInstanceItem, actionType: string) => {
    const action = record.availableActions?.find((item) => item.actionType === actionType);
    modal.confirm({
      title: `确认${action?.label || "执行操作"}?`,
      content: record.flowName || record.id,
      okText: "确认",
      cancelText: "取消",
      onOk: () => flowActionMutation.mutateAsync({
        flowInstanceId: record.id,
        actionType: actionType as SchedulerInstanceActionType,
      }),
    });
  };

  const handleTaskAction = (flow: FlowInstanceItem, task: TaskInstanceItem, actionType: string) => {
    const action = task.availableActions?.find((item) => item.actionType === actionType);
    modal.confirm({
      title: `确认${action?.label || "执行操作"}?`,
      content: task.taskName || task.id,
      okText: "确认",
      cancelText: "取消",
      onOk: () => taskActionMutation.mutateAsync({
        flowInstanceId: flow.id,
        taskInstanceId: task.id,
        actionType: actionType as SchedulerInstanceActionType,
      }),
    });
  };

  const columns = useColumns({
    onRefresh: (record) => void refreshFlowInstance(record).catch((error) => {
      message.error(error instanceof Error ? error.message : "刷新失败");
    }),
    onFlowAction: handleFlowAction,
  });
  const rows = getRows(query.data);
  const pagination = getPagination(query.data);

  const handleTableChange = (next: TablePaginationConfig) => {
    setCurrent(next.current || 1);
    setPageSize(next.pageSize || DEFAULT_PAGE_SIZE);
  };

  return (
    <Card
      title="实例列表"
      extra={
        <Segmented
          options={viewTypeOptions}
          value={viewType}
          onChange={(value) => setViewType(value as SchedulerInstanceViewType)}
        />
      }
    >
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <ListToolbar
          filter={filter}
          onFilterChange={setFilter}
          onSearch={search}
          onReset={reset}
          onRefresh={() => void query.refetch()}
        />

        <Table<FlowInstanceItem>
          rowKey="id"
          className="scheduler-instance-table"
          loading={query.isFetching}
          columns={columns}
          dataSource={rows}
          pagination={pagination}
          onChange={handleTableChange}
          expandable={{
            expandedRowRender: (record) => (
              <TaskInstanceGrid
                flow={record}
                viewType={viewType}
                onOpenDependency={onOpenDependency}
                onOpenLog={onOpenLog}
                onTaskAction={handleTaskAction}
              />
            ),
            columnWidth: EXPAND_COLUMN_WIDTH,
          }}
          scroll={{ x: TABLE_SCROLL_X }}
          tableLayout="fixed"
        />
      </Space>
    </Card>
  );
}

import { Card, Segmented, Space, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import {
  DEFAULT_PAGE_SIZE,
  EXPAND_COLUMN_WIDTH,
  TABLE_SCROLL_X,
  viewTypeOptions,
} from "../../constants";
import type {
  FlowInstanceItem,
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

export function SchedulerInstanceListTable({
  onOpenDependency,
  onOpenLog,
}: SchedulerInstanceListTableProps) {
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
  } = useSchedulerInstanceListQuery();
  const columns = useColumns({ onRefresh: () => void query.refetch() });
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

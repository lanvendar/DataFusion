import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type TaskItem, type TaskTypeFilterOption } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useTaskListQuery } from "./use-list-query";

interface TaskListTableProps {
  loading?: boolean;
  taskTypeOptions: TaskTypeFilterOption[];
  taskTypeLoading?: boolean;
  onAction: (action: PageActionEnum, record?: TaskItem) => void;
}

export function TaskListTable({
  loading,
  taskTypeOptions,
  taskTypeLoading,
  onAction,
}: TaskListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } = useTaskListQuery();
  const columns = useColumns({ onAction });
  const pagination = getPagination(query.data);

  const handleTableChange = (next: TablePaginationConfig) => {
    setCurrent(next.current || 1);
    setPageSize(next.pageSize || DEFAULT_PAGE_SIZE);
  };

  return (
    <Card>
      <ListToolbar
        filter={filter}
        taskTypeOptions={taskTypeOptions}
        taskTypeLoading={taskTypeLoading}
        onFilterChange={setFilter}
        onSearch={search}
        onReset={reset}
        onAction={onAction}
      />
      <Table<TaskItem>
        rowKey="id"
        loading={loading || query.isFetching}
        columns={columns}
        dataSource={query.data?.dataList || query.data?.records || query.data?.list || []}
        pagination={pagination}
        onChange={handleTableChange}
        scroll={{ x: "max-content" }}
      />
    </Card>
  );
}

import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type TaskTypeConfigItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useTaskTypeConfigListQuery } from "./use-list-query";

interface TaskTypeConfigListTableProps {
  loading?: boolean;
  onAction: (action: PageActionEnum, record?: TaskTypeConfigItem) => void;
}

export function TaskTypeConfigListTable({ loading, onAction }: TaskTypeConfigListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } =
    useTaskTypeConfigListQuery();
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
        onFilterChange={setFilter}
        onSearch={search}
        onReset={reset}
        onAction={onAction}
      />
      <Table<TaskTypeConfigItem>
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

import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type WorkerRegistryItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useWorkerRegistryListQuery } from "./use-list-query";

interface WorkerRegistryListTableProps {
  loading?: boolean;
  activeLoading?: boolean;
  activeWorkerId?: string;
  onAction: (action: PageActionEnum, record?: WorkerRegistryItem) => void;
}

export function WorkerRegistryListTable({
  loading,
  activeLoading,
  activeWorkerId,
  onAction,
}: WorkerRegistryListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } =
    useWorkerRegistryListQuery();
  const columns = useColumns({ activeLoading, activeWorkerId, onAction });
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
      <Table<WorkerRegistryItem>
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

import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type TableSyncItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useTableSyncListQuery } from "./use-list-query";

interface TableSyncListTableProps {
  loading?: boolean;
  onAction: (action: PageActionEnum, record?: TableSyncItem) => void;
}

export function TableSyncListTable({ loading, onAction }: TableSyncListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } = useTableSyncListQuery();
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
      <Table<TableSyncItem>
        rowKey="id"
        loading={loading || query.isFetching}
        columns={columns}
        dataSource={query.data?.dataList || []}
        pagination={pagination}
        onChange={handleTableChange}
        scroll={{ x: "max-content" }}
      />
    </Card>
  );
}

import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type DatasourceItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useDatasourceListQuery } from "./use-list-query";

interface DatasourceListTableProps {
  loading?: boolean;
  onAction: (action: PageActionEnum, record?: DatasourceItem) => void;
}

export function DatasourceListTable({ loading, onAction }: DatasourceListTableProps) {
  const {
    filter,
    setFilter,
    query,
    search,
    setCurrent,
    setPageSize,
  } = useDatasourceListQuery();
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
        onAction={onAction}
      />
      <Table<DatasourceItem>
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

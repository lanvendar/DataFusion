import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE, demoRows } from "../../constants";
import { PageActionEnum, type FlowItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useFlowListQuery } from "./use-list-query";

interface FlowListTableProps {
  loading?: boolean;
  onAction: (action: PageActionEnum, record?: FlowItem) => void;
}

export function FlowListTable({ loading, onAction }: FlowListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } = useFlowListQuery();
  const columns = useColumns({ onAction });
  const pagination = getPagination(query.data);
  const rows = query.data?.dataList || query.data?.records || query.data?.list || [];
  const dataSource = rows.length ? rows : query.isError ? demoRows : rows;

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
      <Table<FlowItem>
        rowKey="id"
        loading={loading || query.isFetching}
        columns={columns}
        dataSource={dataSource}
        pagination={pagination}
        onChange={handleTableChange}
        scroll={{ x: "max-content" }}
      />
    </Card>
  );
}

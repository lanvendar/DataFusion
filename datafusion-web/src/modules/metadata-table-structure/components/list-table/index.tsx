import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type TableStructureItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useTableStructureListQuery } from "./use-list-query";

interface TableStructureListTableProps {
  loading?: boolean;
  selectedRowKeys: string[];
  onSelectedRowsChange: (rows: TableStructureItem[]) => void;
  onAction: (action: PageActionEnum, record?: TableStructureItem) => void;
}

export function TableStructureListTable({
  loading,
  selectedRowKeys,
  onSelectedRowsChange,
  onAction,
}: TableStructureListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } = useTableStructureListQuery();
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
        selectedCount={selectedRowKeys.length}
      />
      <Table<TableStructureItem>
        rowKey="id"
        loading={loading || query.isFetching}
        columns={columns}
        dataSource={query.data?.dataList || []}
        pagination={pagination}
        onChange={handleTableChange}
        rowSelection={{
          selectedRowKeys,
          preserveSelectedRowKeys: true,
          onChange: (_, selectedRows) => onSelectedRowsChange(selectedRows),
        }}
        scroll={{ x: "max-content" }}
      />
    </Card>
  );
}

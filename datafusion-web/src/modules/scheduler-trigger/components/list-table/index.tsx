import { Card, Table } from "antd";
import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import { PageActionEnum, type TriggerItem } from "../../dto";
import { useColumns } from "./columns";
import { getPagination } from "./pagination";
import { ListToolbar } from "./toolbar";
import { useTriggerListQuery } from "./use-list-query";

interface TriggerListTableProps {
  loading?: boolean;
  onAction: (action: PageActionEnum, record?: TriggerItem) => void;
}

export function TriggerListTable({ loading, onAction }: TriggerListTableProps) {
  const { filter, setFilter, query, search, reset, setCurrent, setPageSize } = useTriggerListQuery();
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
      <Table<TriggerItem>
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

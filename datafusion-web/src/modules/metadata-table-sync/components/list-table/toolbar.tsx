import { DiffOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type TableSyncListOption } from "../../dto";
import { TableSyncFilters } from "./filters";

interface ListToolbarProps {
  filter: TableSyncListOption;
  onFilterChange: (value: TableSyncListOption) => void;
  onSearch: () => void;
  onReset: () => void;
  onAction: (action: PageActionEnum) => void;
}

export function ListToolbar({ filter, onFilterChange, onSearch, onReset, onAction }: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <TableSyncFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button onClick={onReset}>
          重置
        </Button>
        <Button icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.BATCH_CREATE)}>
          批量创建
        </Button>
        <Button icon={<DiffOutlined />} onClick={() => onAction(PageActionEnum.BATCH_COMPARE)}>
          批量对比
        </Button>
      </Space>
    </div>
  );
}

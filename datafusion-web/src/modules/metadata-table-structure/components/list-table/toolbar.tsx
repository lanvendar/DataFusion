import { PlusOutlined, SearchOutlined, SyncOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type TableStructureListOption } from "../../dto";
import { TableStructureFilters } from "./filters";

interface ListToolbarProps {
  filter: TableStructureListOption;
  onFilterChange: (value: TableStructureListOption) => void;
  onSearch: () => void;
  onReset: () => void;
  onAction: (action: PageActionEnum) => void;
  selectedCount: number;
}

export function ListToolbar({
  filter,
  onFilterChange,
  onSearch,
  onReset,
  onAction,
  selectedCount,
}: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <TableStructureFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button onClick={onReset}>
          重置
        </Button>
        <Button
          icon={<SyncOutlined />}
          disabled={selectedCount === 0}
          onClick={() => onAction(PageActionEnum.BATCH_UPDATE_STRUCTURE)}
        >
          批量更新{selectedCount ? `(${selectedCount})` : ""}
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.ADD)}>
          新增
        </Button>
      </Space>
    </div>
  );
}

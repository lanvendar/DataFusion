import { ClearOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import type { SchedulerInstanceFilterState } from "./use-list-query";
import { SchedulerInstanceFilters } from "./filters";

interface ListToolbarProps {
  filter: SchedulerInstanceFilterState;
  onFilterChange: (value: SchedulerInstanceFilterState) => void;
  onSearch: () => void;
  onReset: () => void;
}

export function ListToolbar({
  filter,
  onFilterChange,
  onSearch,
  onReset,
}: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <SchedulerInstanceFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button icon={<ClearOutlined />} onClick={onReset}>
          重置
        </Button>
      </Space>
    </div>
  );
}

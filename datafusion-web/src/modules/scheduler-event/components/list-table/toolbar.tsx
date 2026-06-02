import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type EventPageOption } from "../../dto";
import { EventFilters } from "./filters";

interface ListToolbarProps {
  filter: EventPageOption;
  onFilterChange: (value: EventPageOption) => void;
  onSearch: () => void;
  onReset: () => void;
  onAction: (action: PageActionEnum) => void;
}

export function ListToolbar({ filter, onFilterChange, onSearch, onReset, onAction }: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <EventFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button onClick={onReset}>重置</Button>
        <Button icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.ADD)}>
          新增
        </Button>
      </Space>
    </div>
  );
}

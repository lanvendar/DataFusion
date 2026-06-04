import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import type { FlowPageOption } from "../../dto";
import { PageActionEnum } from "../../dto";
import { FlowFilters } from "./filters";

interface ListToolbarProps {
  filter: FlowPageOption;
  onFilterChange: (next: FlowPageOption) => void;
  onSearch: () => void;
  onReset: () => void;
  onAction: (action: PageActionEnum) => void;
}

export function ListToolbar({
  filter,
  onFilterChange,
  onSearch,
  onReset,
  onAction,
}: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <FlowFilters value={filter} onChange={onFilterChange} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button onClick={onReset}>重置</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.ADD)}>
          新增
        </Button>
      </Space>
    </div>
  );
}

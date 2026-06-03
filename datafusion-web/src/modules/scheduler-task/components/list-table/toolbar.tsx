import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type TaskPageOption } from "../../dto";
import { TaskFilters } from "./filters";

interface ListToolbarProps {
  filter: TaskPageOption;
  onFilterChange: (value: TaskPageOption) => void;
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
      <TaskFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
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

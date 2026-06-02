import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type DatasourcePageOption } from "../../dto";
import { DatasourceFilters } from "./filters";

interface ListToolbarProps {
  filter: DatasourcePageOption;
  onFilterChange: (value: DatasourcePageOption) => void;
  onSearch: () => void;
  onReset: () => void;
  onAction: (action: PageActionEnum) => void;
}

export function ListToolbar({ filter, onFilterChange, onSearch, onReset, onAction }: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <DatasourceFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button onClick={onReset}>
          重置
        </Button>
        <Button icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.ADD)}>
          新增数据源
        </Button>
      </Space>
    </div>
  );
}

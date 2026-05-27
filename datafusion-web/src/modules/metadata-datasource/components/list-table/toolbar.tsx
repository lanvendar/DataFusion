import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Space } from "antd";
import { PageActionEnum, type DatasourcePageOption } from "../../dto";
import { DatasourceFilters } from "./filters";

interface ListToolbarProps {
  filter: DatasourcePageOption;
  onFilterChange: (value: DatasourcePageOption) => void;
  onSearch: () => void;
  onAction: (action: PageActionEnum) => void;
}

export function ListToolbar({ filter, onFilterChange, onSearch, onAction }: ListToolbarProps) {
  return (
    <div className="table-toolbar">
      <DatasourceFilters value={filter} onChange={onFilterChange} onSearch={onSearch} />
      <Space>
        <Button icon={<ReloadOutlined />} onClick={onSearch}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => onAction(PageActionEnum.ADD)}>
          新增数据源
        </Button>
      </Space>
    </div>
  );
}

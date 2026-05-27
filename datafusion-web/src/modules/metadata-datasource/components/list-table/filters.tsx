import { Input, Select, Space } from "antd";
import { databaseTypeOptions } from "../../constants";
import type { DatasourcePageOption } from "../../dto";

interface FilterProps {
  value: DatasourcePageOption;
  onChange: (value: DatasourcePageOption) => void;
  onSearch: () => void;
}

export function DatasourceFilters({ value, onChange, onSearch }: FilterProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="数据链接名称"
        value={value.name}
        onChange={(event) => onChange({ ...value, name: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="数据库类型"
        value={value.databaseType}
        options={databaseTypeOptions}
        onChange={(databaseType) => onChange({ ...value, databaseType })}
      />
      <Input
        allowClear
        placeholder="数据库名称"
        value={value.databaseName}
        onChange={(event) => onChange({ ...value, databaseName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="表空间名称"
        value={value.schemaName}
        onChange={(event) => onChange({ ...value, schemaName: event.target.value })}
        onPressEnter={onSearch}
      />
    </Space>
  );
}

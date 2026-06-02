import { Input, Select, Space } from "antd";
import { operateTypeOptions } from "../../constants";
import type { TableSyncListOption } from "../../dto";

interface TableSyncFiltersProps {
  value: TableSyncListOption;
  onChange: (value: TableSyncListOption) => void;
  onSearch: () => void;
}

export function TableSyncFilters({ value, onChange, onSearch }: TableSyncFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="源数据源链接名称"
        value={value.sourceDataSourceName}
        onChange={(event) => onChange({ ...value, sourceDataSourceName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="源数据库"
        value={value.sourceDatabaseName}
        onChange={(event) => onChange({ ...value, sourceDatabaseName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="目标数据源链接名称"
        value={value.targetDataSourceName}
        onChange={(event) => onChange({ ...value, targetDataSourceName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="目标数据库"
        value={value.targetDatabaseName}
        onChange={(event) => onChange({ ...value, targetDatabaseName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="同步类型"
        value={value.operateType}
        options={operateTypeOptions}
        onChange={(operateType) => onChange({ ...value, operateType })}
      />
    </Space>
  );
}

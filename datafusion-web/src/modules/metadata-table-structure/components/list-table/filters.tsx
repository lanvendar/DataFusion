import { Input, Select, Space } from "antd";
import { booleanOptions } from "../../constants";
import type { TableStructureListOption } from "../../dto";

interface TableStructureFiltersProps {
  value: TableStructureListOption;
  onChange: (value: TableStructureListOption) => void;
  onSearch: () => void;
}

export function TableStructureFilters({
  value,
  onChange,
  onSearch,
}: TableStructureFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="数据源链接名称"
        value={value.databaseConnectName}
        onChange={(event) => onChange({ ...value, databaseConnectName: event.target.value })}
        onPressEnter={onSearch}
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
      <Input
        allowClear
        placeholder="表名称"
        value={value.tableName}
        onChange={(event) => onChange({ ...value, tableName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="是否同步"
        value={value.isModify}
        options={booleanOptions}
        onChange={(isModify) => onChange({ ...value, isModify })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="是否视图"
        value={value.isView}
        options={booleanOptions}
        onChange={(isView) => onChange({ ...value, isView })}
      />
    </Space>
  );
}

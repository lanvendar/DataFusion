import { Input, Select, Space } from "antd";
import { valueTypeOptions, variableTypeOptions } from "../../constants";
import type { VariablePageOption } from "../../dto";

interface VariableFiltersProps {
  value: VariablePageOption;
  onChange: (value: VariablePageOption) => void;
  onSearch: () => void;
}

export function VariableFilters({ value, onChange, onSearch }: VariableFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="变量编码"
        value={value.code}
        onChange={(event) => onChange({ ...value, code: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="变量名称"
        value={value.name}
        onChange={(event) => onChange({ ...value, name: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="变量类型"
        value={value.type}
        options={variableTypeOptions}
        onChange={(type) => onChange({ ...value, type })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="值类型"
        value={value.valueType}
        options={valueTypeOptions}
        onChange={(valueType) => onChange({ ...value, valueType })}
      />
    </Space>
  );
}

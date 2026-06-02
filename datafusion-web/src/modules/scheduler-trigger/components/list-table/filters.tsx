import { Input, Select, Space } from "antd";
import { policyOptions, triggerTypeOptions } from "../../constants";
import type { TriggerPageOption } from "../../dto";

interface TriggerFiltersProps {
  value: TriggerPageOption;
  onChange: (value: TriggerPageOption) => void;
  onSearch: () => void;
}

export function TriggerFilters({ value, onChange, onSearch }: TriggerFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="触发器名称"
        value={value.name}
        onChange={(event) => onChange({ ...value, name: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="触发器类型"
        value={value.type}
        options={triggerTypeOptions}
        onChange={(type) => onChange({ ...value, type })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="调度策略"
        value={value.policy}
        options={policyOptions}
        onChange={(policy) => onChange({ ...value, policy })}
      />
    </Space>
  );
}

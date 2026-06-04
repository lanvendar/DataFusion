import { Input, Select, Space } from "antd";
import { enabledOptions, flowTypeOptions, publishStateOptions } from "../../constants";
import type { FlowPageOption } from "../../dto";

interface FlowFiltersProps {
  value: FlowPageOption;
  onChange: (next: FlowPageOption) => void;
}

export function FlowFilters({ value, onChange }: FlowFiltersProps) {
  return (
    <Space wrap size={12}>
      <Input
        allowClear
        placeholder="流程名称"
        value={value.flowName}
        onChange={(event) => onChange({ ...value, flowName: event.target.value || undefined })}
        style={{ width: 180 }}
      />
      <Select
        allowClear
        placeholder="流程类型"
        options={flowTypeOptions}
        value={value.flowType}
        onChange={(flowType) => onChange({ ...value, flowType })}
        style={{ width: 160 }}
      />
      <Select
        allowClear
        placeholder="发布状态"
        options={publishStateOptions}
        value={value.publishState}
        onChange={(publishState) => onChange({ ...value, publishState })}
        style={{ width: 140 }}
      />
      <Select
        allowClear
        placeholder="调度状态"
        options={enabledOptions}
        value={value.enabled}
        onChange={(enabled) => onChange({ ...value, enabled })}
        style={{ width: 140 }}
      />
    </Space>
  );
}

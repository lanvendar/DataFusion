import { Input, Select, Space } from "antd";
import {
  boundOptions,
  enabledOptions,
  syncFlagOptions,
} from "../../constants";
import type { TaskPageOption, TaskTypeFilterOption } from "../../dto";

interface TaskFiltersProps {
  value: TaskPageOption;
  taskTypeOptions: TaskTypeFilterOption[];
  taskTypeLoading?: boolean;
  onChange: (value: TaskPageOption) => void;
  onSearch: () => void;
}

export function TaskFilters({
  value,
  taskTypeOptions,
  taskTypeLoading,
  onChange,
  onSearch,
}: TaskFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="任务名称"
        value={value.taskName}
        onChange={(event) => onChange({ ...value, taskName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="任务编码"
        value={value.taskCode}
        onChange={(event) => onChange({ ...value, taskCode: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        loading={taskTypeLoading}
        placeholder="任务类型"
        value={value.taskType}
        options={taskTypeOptions}
        optionFilterProp="label"
        showSearch
        onChange={(taskType) => onChange({ ...value, taskType })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="是否绑定流程"
        value={value.isBound}
        options={boundOptions}
        onChange={(isBound) => onChange({ ...value, isBound })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="是否启用"
        value={value.enabled}
        options={enabledOptions}
        onChange={(enabled) => onChange({ ...value, enabled })}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="是否同步"
        value={value.syncFlag}
        options={syncFlagOptions}
        onChange={(syncFlag) => onChange({ ...value, syncFlag })}
      />
    </Space>
  );
}

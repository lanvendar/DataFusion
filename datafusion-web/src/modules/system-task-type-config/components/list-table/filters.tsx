import { Input, Space } from "antd";
import type { TaskTypeConfigPageOption } from "../../dto";

interface TaskTypeConfigFiltersProps {
  value: TaskTypeConfigPageOption;
  onChange: (value: TaskTypeConfigPageOption) => void;
  onSearch: () => void;
}

export function TaskTypeConfigFilters({ value, onChange, onSearch }: TaskTypeConfigFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="任务类型"
        value={value.taskType}
        onChange={(event) => onChange({ ...value, taskType: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="插件类型"
        value={value.pluginType}
        onChange={(event) => onChange({ ...value, pluginType: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="默认插件ID"
        value={value.defaultPluginId}
        onChange={(event) => onChange({ ...value, defaultPluginId: event.target.value || undefined })}
        onPressEnter={onSearch}
      />
    </Space>
  );
}

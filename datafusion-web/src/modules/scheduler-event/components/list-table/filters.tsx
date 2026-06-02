import { Input, Select, Space } from "antd";
import { eventTypeOptions } from "../../constants";
import type { EventPageOption } from "../../dto";

interface EventFiltersProps {
  value: EventPageOption;
  onChange: (value: EventPageOption) => void;
  onSearch: () => void;
}

export function EventFilters({ value, onChange, onSearch }: EventFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="事件名称"
        value={value.eventName}
        onChange={(event) => onChange({ ...value, eventName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="事件类型"
        value={value.eventType}
        options={eventTypeOptions}
        onChange={(eventType) => onChange({ ...value, eventType })}
      />
      <Input
        allowClear
        placeholder="关联流程ID"
        value={value.flowId}
        onChange={(event) => onChange({ ...value, flowId: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="关联任务ID"
        value={value.taskId}
        onChange={(event) => onChange({ ...value, taskId: event.target.value })}
        onPressEnter={onSearch}
      />
    </Space>
  );
}

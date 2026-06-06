import { DatePicker, Input, Select, Space } from "antd";
import { statusOptions } from "../../constants";
import type { SchedulerInstanceFilterState } from "./use-list-query";

interface SchedulerInstanceFiltersProps {
  value: SchedulerInstanceFilterState;
  onChange: (value: SchedulerInstanceFilterState) => void;
  onSearch: () => void;
}

export function SchedulerInstanceFilters({
  value,
  onChange,
  onSearch,
}: SchedulerInstanceFiltersProps) {
  return (
    <Space wrap size={12}>
      <Input
        allowClear
        placeholder="请输入流程名称/ID"
        value={value.flowKeyword}
        onChange={(event) => onChange({ ...value, flowKeyword: event.target.value || undefined })}
        onPressEnter={onSearch}
        style={{ width: 190 }}
      />
      <Input
        allowClear
        placeholder="请输入任务名称/ID"
        value={value.taskKeyword}
        onChange={(event) => onChange({ ...value, taskKeyword: event.target.value || undefined })}
        onPressEnter={onSearch}
        style={{ width: 190 }}
      />
      <Select
        allowClear
        placeholder="请选择实例状态"
        options={statusOptions}
        value={value.status}
        onChange={(status) => onChange({ ...value, status })}
        style={{ width: 150 }}
      />
      <DatePicker.RangePicker
        value={value.scheduleRange}
        placeholder={["调度开始", "调度结束"]}
        showTime
        onChange={(dates) =>
          onChange({
            ...value,
            scheduleRange: dates && dates[0] && dates[1] ? [dates[0], dates[1]] : null,
          })}
        style={{ width: 320 }}
      />
      <DatePicker.RangePicker
        value={value.startRange}
        placeholder={["开始时间起", "开始时间止"]}
        showTime
        onChange={(dates) =>
          onChange({
            ...value,
            startRange: dates && dates[0] && dates[1] ? [dates[0], dates[1]] : null,
          })}
        style={{ width: 320 }}
      />
      <DatePicker.RangePicker
        value={value.finishRange}
        placeholder={["结束时间起", "结束时间止"]}
        showTime
        onChange={(dates) =>
          onChange({
            ...value,
            finishRange: dates && dates[0] && dates[1] ? [dates[0], dates[1]] : null,
          })}
        style={{ width: 320 }}
      />
    </Space>
  );
}

import { Input, Select, Space } from "antd";
import { templateOptions } from "../../constants";
import type { PluginConfigPageOption } from "../../dto";

interface PluginConfigFiltersProps {
  value: PluginConfigPageOption;
  onChange: (value: PluginConfigPageOption) => void;
  onSearch: () => void;
}

export function PluginConfigFilters({ value, onChange, onSearch }: PluginConfigFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="插件名称"
        value={value.pluginName}
        onChange={(event) => onChange({ ...value, pluginName: event.target.value })}
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
        placeholder="运行模式"
        value={value.runMode}
        onChange={(event) => onChange({ ...value, runMode: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="模板标记"
        value={value.isTemplate}
        options={templateOptions}
        onChange={(isTemplate) => onChange({ ...value, isTemplate })}
      />
    </Space>
  );
}

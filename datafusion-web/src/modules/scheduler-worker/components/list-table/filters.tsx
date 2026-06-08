import { Input, Select, Space } from "antd";
import { activeOptions, workerStatusOptions } from "../../constants";
import type { WorkerRegistryPageOption } from "../../dto";

interface WorkerRegistryFiltersProps {
  value: WorkerRegistryPageOption;
  onChange: (value: WorkerRegistryPageOption) => void;
  onSearch: () => void;
}

export function WorkerRegistryFilters({
  value,
  onChange,
  onSearch,
}: WorkerRegistryFiltersProps) {
  return (
    <Space wrap>
      <Input
        allowClear
        placeholder="节点编码"
        value={value.workerCode}
        onChange={(event) => onChange({ ...value, workerCode: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="主机名称"
        value={value.hostName}
        onChange={(event) => onChange({ ...value, hostName: event.target.value })}
        onPressEnter={onSearch}
      />
      <Input
        allowClear
        placeholder="IP 地址"
        value={value.host}
        onChange={(event) => onChange({ ...value, host: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="状态"
        value={value.status}
        options={workerStatusOptions}
        onChange={(status) => onChange({ ...value, status })}
      />
      <Input
        allowClear
        placeholder="区域"
        value={value.zone}
        onChange={(event) => onChange({ ...value, zone: event.target.value })}
        onPressEnter={onSearch}
      />
      <Select
        allowClear
        className="filter-select"
        placeholder="有效标记"
        value={value.isActive}
        options={activeOptions}
        onChange={(isActive) => onChange({ ...value, isActive })}
      />
    </Space>
  );
}

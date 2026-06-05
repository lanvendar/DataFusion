import { Button, Drawer, Form, Input, Select, Space } from "antd";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo } from "react";
import { pluginConfigApi } from "@/modules/system-plugin-config/api";
import type { PluginConfigItem } from "@/modules/system-plugin-config/dto";
import { demoPluginConfigRows } from "@/modules/system-plugin-config/constants";
import { env } from "@/env";
import type { TaskTypeConfigFormMode, TaskTypeConfigItem } from "../../dto";
import { useTaskTypeConfigSubmit } from "./use-submit";

interface TaskTypeConfigFormProps {
  open: boolean;
  mode: TaskTypeConfigFormMode;
  currentRecord?: TaskTypeConfigItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

function getPluginOptions(records: PluginConfigItem[]) {
  return records.map((item) => ({
    label: `${item.pluginName || item.id} / ${item.pluginType || "-"}`,
    value: item.id,
    pluginType: item.pluginType,
  }));
}

export function TaskTypeConfigForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: TaskTypeConfigFormProps) {
  const { form, title, submit } = useTaskTypeConfigSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });

  const pluginQuery = useQuery({
    queryKey: ["system-plugin-config-options"],
    enabled: open,
    queryFn: async () => {
      try {
        return await pluginConfigApi.list({ isDel: 0 });
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo plugin config options", error);
        return demoPluginConfigRows;
      }
    },
  });

  const pluginOptions = useMemo(() => getPluginOptions(pluginQuery.data || []), [pluginQuery.data]);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue(currentRecord);
    } else {
      form.resetFields();
    }
  }, [currentRecord, form, mode, open]);

  const handlePluginChange = (defaultPluginId: string) => {
    const option = pluginOptions.find((item) => item.value === defaultPluginId);
    if (option?.pluginType) {
      form.setFieldValue("pluginType", option.pluginType);
    }
  };

  return (
    <Drawer
      title={title}
      open={open}
      width={560}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item name="taskType" label="任务类型" rules={[{ required: mode === "add" }]}>
          <Input disabled={mode === "edit"} placeholder="例如 DATAX、SHELL、SQL" />
        </Form.Item>
        <Form.Item name="defaultPluginId" label="默认插件" rules={[{ required: true }]}>
          <Select
            showSearch
            loading={pluginQuery.isFetching}
            options={pluginOptions}
            optionFilterProp="label"
            placeholder="请选择默认插件"
            onChange={handlePluginChange}
          />
        </Form.Item>
        <Form.Item name="pluginType" label="插件类型">
          <Input placeholder="例如 DATAX、SHELL、SQL" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

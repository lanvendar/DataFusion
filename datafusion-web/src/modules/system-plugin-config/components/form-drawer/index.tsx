import { Button, Drawer, Form, Input, Space } from "antd";
import { useEffect } from "react";
import { PLUGIN_COPY_BASE_MAX_LENGTH } from "../../constants";
import type { PluginConfigFormMode, PluginConfigItem } from "../../dto";
import { usePluginConfigSubmit } from "./use-submit";

interface PluginConfigFormProps {
  open: boolean;
  mode: PluginConfigFormMode;
  currentRecord?: PluginConfigItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

function stringifyPluginParam(value: unknown) {
  if (!value) return "";
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

export function PluginConfigForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: PluginConfigFormProps) {
  const { form, title, submit } = usePluginConfigSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue({
        pluginName: currentRecord.pluginName,
        pluginType: currentRecord.pluginType,
        runMode: currentRecord.runMode,
        description: currentRecord.description,
        pluginParamText: stringifyPluginParam(currentRecord.pluginParam),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ runMode: "DEFAULT" });
    }
  }, [currentRecord, form, mode, open]);

  return (
    <Drawer
      title={title}
      open={open}
      width={640}
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
        <Form.Item
          name="pluginName"
          label="插件名称"
          rules={[
            { required: true },
            {
              max: PLUGIN_COPY_BASE_MAX_LENGTH,
              message: `插件名称不能超过${PLUGIN_COPY_BASE_MAX_LENGTH}个字符`,
            },
          ]}
        >
          <Input placeholder="请输入插件名称" />
        </Form.Item>
        <Form.Item name="pluginType" label="插件类型" rules={[{ required: true }]}>
          <Input placeholder="例如 FLINK、DATAX" />
        </Form.Item>
        <Form.Item name="runMode" label="运行模式" rules={[{ required: true }]}>
          <Input placeholder="例如 DEFAULT、YARN、K8S" />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={3} placeholder="请输入描述" />
        </Form.Item>
        <Form.Item
          name="pluginParamText"
          label="插件配置 JSON"
          rules={[
            {
              validator: async (_, value?: string) => {
                if (!value?.trim()) return;
                try {
                  JSON.parse(value);
                } catch {
                  throw new Error("请输入合法 JSON");
                }
              },
            },
          ]}
        >
          <Input.TextArea rows={10} placeholder='例如 {"queue":"default","parallelism":2}' />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

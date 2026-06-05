import { App, Form } from "antd";
import { useMemo } from "react";
import { pluginConfigApi } from "../../api";
import type {
  PluginConfigFormMode,
  PluginConfigFormValues,
  PluginConfigItem,
  PluginConfigSaveReq,
} from "../../dto";

interface UsePluginConfigSubmitOptions {
  mode: PluginConfigFormMode;
  currentRecord?: PluginConfigItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

function parseEnv(envText?: string) {
  if (!envText?.trim()) return undefined;
  return JSON.parse(envText) as unknown;
}

function buildParams(values: PluginConfigFormValues, id?: string): PluginConfigSaveReq {
  return {
    id,
    pluginName: values.pluginName,
    pluginType: values.pluginType,
    runMode: values.runMode,
    description: values.description,
    env: parseEnv(values.envText),
  };
}

export function usePluginConfigSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UsePluginConfigSubmitOptions) {
  const [form] = Form.useForm<PluginConfigFormValues>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑插件配置" : "新增插件配置"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    if (mode === "edit" && currentRecord?.id) {
      await pluginConfigApi.update(buildParams(values, currentRecord.id));
      message.success("编辑成功");
    } else {
      await pluginConfigApi.add(buildParams(values));
      message.success("新增成功");
    }
    onSubmitSuccess();
    onClose();
  };

  return {
    form,
    title,
    submit,
  };
}

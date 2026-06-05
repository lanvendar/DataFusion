import { App, Form } from "antd";
import { useMemo } from "react";
import { taskTypeConfigApi } from "../../api";
import type {
  TaskTypeConfigFormMode,
  TaskTypeConfigItem,
  TaskTypeConfigSaveReq,
} from "../../dto";

interface UseTaskTypeConfigSubmitOptions {
  mode: TaskTypeConfigFormMode;
  currentRecord?: TaskTypeConfigItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useTaskTypeConfigSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseTaskTypeConfigSubmitOptions) {
  const [form] = Form.useForm<TaskTypeConfigSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑任务类型配置" : "新增任务类型配置"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    if (mode === "edit" && currentRecord?.id) {
      await taskTypeConfigApi.update({
        id: currentRecord.id,
        defaultPluginId: values.defaultPluginId,
        pluginType: values.pluginType,
      });
      message.success("编辑成功");
    } else {
      await taskTypeConfigApi.add(values);
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

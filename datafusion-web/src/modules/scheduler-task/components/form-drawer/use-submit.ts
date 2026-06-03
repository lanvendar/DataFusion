import { App, Form } from "antd";
import { useMemo } from "react";
import { taskApi } from "../../api";
import { taskTypeOptions } from "../../constants";
import type { TaskFormMode, TaskItem, TaskSaveReq } from "../../dto";
import { normalizeJsonText } from "../../utils";

export interface TaskFormValues {
  taskName: string;
  taskCode: string;
  description?: string;
  taskTypeId: string;
  taskParamText?: string;
  definitionText?: string;
}

interface UseTaskSubmitOptions {
  mode: TaskFormMode;
  currentRecord?: TaskItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useTaskSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseTaskSubmitOptions) {
  const [form] = Form.useForm<TaskFormValues>();
  const { message } = App.useApp();
  const title = useMemo(() => (mode === "edit" ? "编辑任务" : "新增任务"), [mode]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const taskTypeOption = taskTypeOptions.find((item) => item.value === values.taskTypeId);
      const payload: TaskSaveReq = {
        id: mode === "edit" ? currentRecord?.id : undefined,
        taskName: values.taskName,
        taskCode: values.taskCode,
        description: values.description,
        taskTypeId: values.taskTypeId,
        taskType: taskTypeOption?.value || values.taskTypeId,
        taskParam: normalizeJsonText(values.taskParamText, "任务参数"),
        definition: normalizeJsonText(values.definitionText, "任务定义"),
      };

      if (mode === "edit") {
        await taskApi.update(payload);
        message.success("编辑成功");
      } else {
        await taskApi.add(payload);
        message.success("新增成功");
      }
      onSubmitSuccess();
      onClose();
    } catch (error) {
      if (typeof error === "object" && error && "errorFields" in error) return;
      message.error(error instanceof Error ? error.message : "保存失败");
    }
  };

  return {
    form,
    title,
    submit,
  };
}

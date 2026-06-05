import { App, Form } from "antd";
import { useMemo } from "react";
import { variableApi } from "../../api";
import type { VariableFormMode, VariableItem, VariableSaveReq } from "../../dto";

interface UseVariableSubmitOptions {
  mode: VariableFormMode;
  currentRecord?: VariableItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useVariableSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseVariableSubmitOptions) {
  const [form] = Form.useForm<VariableSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑变量" : "新增变量"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    if (mode === "edit" && currentRecord?.id) {
      const params =
        currentRecord.type === "SYSTEM"
          ? { id: currentRecord.id, value: values.value }
          : { ...values, id: currentRecord.id };
      await variableApi.update(params);
      message.success("编辑成功");
    } else {
      await variableApi.add(values);
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

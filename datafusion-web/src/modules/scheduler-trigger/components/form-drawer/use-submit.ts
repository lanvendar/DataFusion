import { App, Form } from "antd";
import { useMemo } from "react";
import { triggerApi } from "../../api";
import type { TriggerFormMode, TriggerItem, TriggerSaveReq } from "../../dto";

interface UseTriggerSubmitOptions {
  mode: TriggerFormMode;
  currentRecord?: TriggerItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useTriggerSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseTriggerSubmitOptions) {
  const [form] = Form.useForm<TriggerSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑触发器" : "新增触发器"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    const params: TriggerSaveReq = {
      ...values,
      id: mode === "edit" ? currentRecord?.id : undefined,
      cron: values.type === "CRON" ? values.cron : undefined,
      interval: values.type === "INTERVAL" ? values.interval : undefined,
    };

    if (mode === "edit") {
      await triggerApi.update(params);
      message.success("编辑成功");
    } else {
      await triggerApi.add(params);
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

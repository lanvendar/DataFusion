import { App, Form } from "antd";
import { useMemo } from "react";
import { eventApi } from "../../api";
import type { EventFormMode, EventItem, EventSaveReq } from "../../dto";

interface UseEventSubmitOptions {
  mode: EventFormMode;
  currentRecord?: EventItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useEventSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseEventSubmitOptions) {
  const [form] = Form.useForm<EventSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑事件" : "新增事件"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    const params: EventSaveReq = {
      ...values,
      id: mode === "edit" ? currentRecord?.id : undefined,
      flowId: values.eventType === "2" ? values.flowId : undefined,
      taskId: values.eventType === "1" ? values.taskId : undefined,
    };

    if (mode === "edit") {
      await eventApi.update(params);
      message.success("编辑成功");
    } else {
      await eventApi.add(params);
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

import { App, Form } from "antd";
import type { Dayjs } from "dayjs";
import { useMemo } from "react";
import { flowApi } from "../../api";
import type { FlowFormMode, FlowItem, FlowSaveReq } from "../../dto";
import { normalizeJsonText } from "../../utils";

export interface FlowFormValues {
  flowName: string;
  flowCode: string;
  groupId?: string;
  description?: string;
  flowType: string;
  triggerId?: string;
  depEventIds?: string[];
  scheduleWindow?: [Dayjs, Dayjs];
  flowParamText?: string;
}

interface UseFlowSubmitOptions {
  mode: FlowFormMode;
  currentRecord?: FlowItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useFlowSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseFlowSubmitOptions) {
  const [form] = Form.useForm<FlowFormValues>();
  const { message } = App.useApp();
  const title = useMemo(() => (mode === "edit" ? "编辑流程" : "新增流程"), [mode]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const payload: FlowSaveReq = {
        id: mode === "edit" ? currentRecord?.id : undefined,
        flowName: values.flowName,
        flowCode: values.flowCode,
        groupId: values.groupId,
        description: values.description,
        flowType: values.flowType,
        triggerId: values.triggerId,
        depEventIds: values.depEventIds || [],
        flowParam: normalizeJsonText(values.flowParamText, "流程变量参数"),
        startTime: values.scheduleWindow?.[0]?.valueOf(),
        endTime: values.scheduleWindow?.[1]?.valueOf(),
      };

      if (mode === "edit") {
        await flowApi.update(payload);
        message.success("编辑成功");
      } else {
        await flowApi.add(payload);
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

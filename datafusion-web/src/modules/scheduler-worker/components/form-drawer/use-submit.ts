import { App, Form } from "antd";
import { useMemo } from "react";
import { workerRegistryApi } from "../../api";
import type {
  WorkerRegistryFormMode,
  WorkerRegistryItem,
  WorkerRegistrySaveReq,
} from "../../dto";

interface UseWorkerRegistrySubmitOptions {
  mode: WorkerRegistryFormMode;
  currentRecord?: WorkerRegistryItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useWorkerRegistrySubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseWorkerRegistrySubmitOptions) {
  const [form] = Form.useForm<WorkerRegistrySaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑 Worker" : "新增 Worker"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    const params: WorkerRegistrySaveReq = {
      ...values,
      id: mode === "edit" ? currentRecord?.id : undefined,
    };

    if (mode === "edit") {
      await workerRegistryApi.update(params);
      message.success("编辑成功");
    } else {
      await workerRegistryApi.add(params);
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

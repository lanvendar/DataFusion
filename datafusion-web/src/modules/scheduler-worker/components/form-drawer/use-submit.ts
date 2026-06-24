import { App, Form } from "antd";
import { useMemo } from "react";
import { workerRegistryApi } from "../../api";
import type {
  WorkerRegistryFormMode,
  WorkerRegistryItem,
  WorkerRegistrySaveReq,
  WorkerRegistryUpdateReq,
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

  const title = useMemo(() => (mode === "edit" ? "编辑执行节点" : "新增执行节点"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();

    if (mode === "edit") {
      if (!currentRecord?.id) return;
      const params: WorkerRegistryUpdateReq = {
        id: currentRecord.id,
        hostName: values.hostName,
        host: values.host,
        port: values.port,
        zone: values.zone,
        plugins: values.plugins,
        isActive: values.isActive,
        remark: values.remark,
      };
      await workerRegistryApi.update(params);
      message.success("编辑成功");
    } else {
      const params: WorkerRegistrySaveReq = values;
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

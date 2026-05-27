import { App, Form } from "antd";
import { useMemo } from "react";
import { datasourceApi } from "../../api";
import type {
  DatasourceFormMode,
  DatasourceItem,
  DatasourceSaveReq,
} from "../../dto";

interface UseDatasourceSubmitOptions {
  mode: DatasourceFormMode;
  currentRecord?: DatasourceItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useDatasourceSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseDatasourceSubmitOptions) {
  const [form] = Form.useForm<DatasourceSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => {
    if (mode === "edit") return "编辑数据源";
    if (mode === "copy") return "复制新增数据源";
    return "新增数据源";
  }, [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    if (mode === "edit" && currentRecord?.id) {
      await datasourceApi.update({ ...values, id: currentRecord.id });
      message.success("编辑成功");
    } else {
      await datasourceApi.add(values);
      message.success(mode === "copy" ? "复制新增成功" : "新增成功");
    }
    onSubmitSuccess();
    onClose();
  };

  const testConnection = async () => {
    const values = await form.validateFields(["databaseType", "host", "port", "databaseName"]);
    await datasourceApi.testConnection({
      ...form.getFieldsValue(true),
      ...values,
    });
    message.success("连接测试成功");
  };

  return {
    form,
    title,
    submit,
    testConnection,
  };
}

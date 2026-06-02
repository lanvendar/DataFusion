import { App, Form } from "antd";
import { useMemo } from "react";
import { tableStructureApi } from "../../api";
import type {
  TableStructureFormMode,
  TableStructureItem,
  TableStructureSaveReq,
} from "../../dto";

interface UseTableStructureSubmitOptions {
  mode: TableStructureFormMode;
  currentRecord?: TableStructureItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function useTableStructureSubmit({
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseTableStructureSubmitOptions) {
  const [form] = Form.useForm<TableStructureSaveReq>();
  const { message } = App.useApp();

  const title = useMemo(() => (mode === "edit" ? "编辑表结构" : "新增表结构"), [mode]);

  const submit = async () => {
    const values = await form.validateFields();
    if (mode === "edit" && currentRecord?.id) {
      await tableStructureApi.update({ ...values, id: currentRecord.id });
      message.success("编辑成功");
    } else {
      await tableStructureApi.add(values);
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

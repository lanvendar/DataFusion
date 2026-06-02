import { Button, Drawer, Form, Input, Select, Space } from "antd";
import { useEffect } from "react";
import { valueTypeOptions } from "../../constants";
import type { VariableFormMode, VariableItem } from "../../dto";
import { useVariableSubmit } from "./use-submit";

interface VariableFormProps {
  open: boolean;
  mode: VariableFormMode;
  currentRecord?: VariableItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function VariableForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: VariableFormProps) {
  const { form, title, submit } = useVariableSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });
  const isSystemVariable = mode === "edit" && currentRecord?.type === "SYSTEM";

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue(currentRecord);
    } else {
      form.resetFields();
      form.setFieldsValue({ valueType: "STRING" });
    }
  }, [currentRecord, form, mode, open]);

  return (
    <Drawer
      title={title}
      open={open}
      width={560}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item name="code" label="变量编码" rules={[{ required: !isSystemVariable }]}>
          <Input disabled={isSystemVariable} placeholder="请输入变量编码" />
        </Form.Item>
        <Form.Item name="name" label="变量名称" rules={[{ required: !isSystemVariable }]}>
          <Input disabled={isSystemVariable} placeholder="请输入变量名称" />
        </Form.Item>
        <Form.Item name="valueType" label="值类型" rules={[{ required: !isSystemVariable }]}>
          <Select disabled={isSystemVariable} options={valueTypeOptions} placeholder="请选择值类型" />
        </Form.Item>
        <Form.Item name="value" label="变量值">
          <Input.TextArea rows={4} placeholder="请输入变量值" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

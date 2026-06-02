import { Button, Drawer, Form, Input, InputNumber, Select, Space } from "antd";
import { useEffect } from "react";
import { policyOptions, triggerTypeOptions } from "../../constants";
import type { TriggerFormMode, TriggerItem } from "../../dto";
import { useTriggerSubmit } from "./use-submit";

interface TriggerFormProps {
  open: boolean;
  mode: TriggerFormMode;
  currentRecord?: TriggerItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function TriggerForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: TriggerFormProps) {
  const { form, title, submit } = useTriggerSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });
  const triggerType = Form.useWatch("type", form);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue(currentRecord);
    } else {
      form.resetFields();
      form.setFieldsValue({
        type: "CRON",
        policy: "SERIAL_WAIT",
      });
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
        <Form.Item name="name" label="触发器名称" rules={[{ required: true }]}>
          <Input placeholder="请输入触发器名称" />
        </Form.Item>
        <Form.Item name="type" label="触发器类型" rules={[{ required: true }]}>
          <Select options={triggerTypeOptions} placeholder="请选择触发器类型" />
        </Form.Item>
        <Form.Item name="policy" label="调度策略" rules={[{ required: true }]}>
          <Select options={policyOptions} placeholder="请选择调度策略" />
        </Form.Item>
        {triggerType === "INTERVAL" ? (
          <Form.Item
            name="interval"
            label="周期间隔（分钟）"
            rules={[{ required: true, message: "请输入周期间隔" }]}
          >
            <InputNumber min={1} precision={0} className="full-input" placeholder="请输入周期间隔" />
          </Form.Item>
        ) : (
          <Form.Item
            name="cron"
            label="CRON 表达式"
            rules={[{ required: true, message: "请输入 CRON 表达式" }]}
          >
            <Input placeholder="例如 0 0 2 * * ?" />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  );
}

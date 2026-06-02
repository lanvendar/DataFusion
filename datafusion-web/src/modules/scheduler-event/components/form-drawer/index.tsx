import { Button, Drawer, Form, Input, Select, Space } from "antd";
import { useEffect } from "react";
import { eventTypeOptions } from "../../constants";
import type { EventFormMode, EventItem } from "../../dto";
import { useEventSubmit } from "./use-submit";

interface EventFormProps {
  open: boolean;
  mode: EventFormMode;
  currentRecord?: EventItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function EventForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: EventFormProps) {
  const { form, title, submit } = useEventSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });
  const eventType = Form.useWatch("eventType", form);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue(currentRecord);
    } else {
      form.resetFields();
      form.setFieldsValue({ eventType: "1" });
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
        <Form.Item name="eventName" label="事件名称" rules={[{ required: true }]}>
          <Input placeholder="请输入事件名称" />
        </Form.Item>
        <Form.Item name="eventType" label="事件类型" rules={[{ required: true }]}>
          <Select options={eventTypeOptions} placeholder="请选择事件类型" />
        </Form.Item>
        {eventType === "2" ? (
          <Form.Item
            name="flowId"
            label="关联流程ID"
            rules={[{ required: true, message: "FLOW 类型事件必须关联流程" }]}
          >
            <Input placeholder="请输入关联流程ID" />
          </Form.Item>
        ) : (
          <Form.Item
            name="taskId"
            label="关联任务ID"
            rules={[{ required: true, message: "TASK 类型事件必须关联任务" }]}
          >
            <Input placeholder="请输入关联任务ID" />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  );
}

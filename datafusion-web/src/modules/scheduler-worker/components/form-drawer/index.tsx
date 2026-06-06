import { Button, Drawer, Form, Input, InputNumber, Select, Space } from "antd";
import { useEffect } from "react";
import { activeOptions, workerStatusOptions } from "../../constants";
import type { WorkerRegistryFormMode, WorkerRegistryItem } from "../../dto";
import { useWorkerRegistrySubmit } from "./use-submit";

interface WorkerRegistryFormProps {
  open: boolean;
  mode: WorkerRegistryFormMode;
  currentRecord?: WorkerRegistryItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function WorkerRegistryForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: WorkerRegistryFormProps) {
  const { form, title, submit } = useWorkerRegistrySubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      form.setFieldsValue(currentRecord);
    } else {
      form.resetFields();
      form.setFieldsValue({
        status: 0,
        isActive: 1,
      });
    }
  }, [currentRecord, form, mode, open]);

  return (
    <Drawer
      title={title}
      open={open}
      width={640}
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
        <Form.Item name="workerCode" label="worker 编码" rules={[{ required: true }]}>
          <Input placeholder="请输入 worker 编码" />
        </Form.Item>
        <Form.Item name="hostName" label="主机名称" rules={[{ required: true }]}>
          <Input placeholder="请输入主机名称" />
        </Form.Item>
        <Form.Item name="host" label="IP 地址" rules={[{ required: true }]}>
          <Input placeholder="请输入 IP 地址" />
        </Form.Item>
        <Form.Item name="port" label="端口" rules={[{ required: true }]}>
          <InputNumber min={1} precision={0} className="full-input" placeholder="请输入端口" />
        </Form.Item>
        <Form.Item name="status" label="状态" rules={[{ required: true }]}>
          <Select options={workerStatusOptions} placeholder="请选择状态" />
        </Form.Item>
        <Form.Item name="isActive" label="有效标记" rules={[{ required: true }]}>
          <Select options={activeOptions} placeholder="请选择有效标记" />
        </Form.Item>
        <Form.Item name="zone" label="区域">
          <Input placeholder="请输入区域/分组" />
        </Form.Item>
        <Form.Item name="plugins" label="插件类型">
          <Input placeholder="例如 DATAX,FLINK" />
        </Form.Item>
        <Form.Item name="remark" label="资源说明">
          <Input.TextArea rows={3} placeholder="请输入资源说明" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

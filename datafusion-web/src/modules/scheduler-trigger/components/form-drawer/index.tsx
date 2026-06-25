import { App, Button, Drawer, Form, Input, InputNumber, List, Select, Space, Typography } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { triggerApi } from "../../api";
import { policyOptions, triggerTypeOptions } from "../../constants";
import type { TriggerCronPreviewRes, TriggerFormMode, TriggerItem } from "../../dto";
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
  const { message } = App.useApp();
  const [cronPreview, setCronPreview] = useState<TriggerCronPreviewRes>();
  const [cronPreviewLoading, setCronPreviewLoading] = useState(false);
  const { form, title, submit } = useTriggerSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });
  const triggerType = Form.useWatch("type", form);
  const cronValue = Form.useWatch("cron", form);

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

  useEffect(() => {
    setCronPreview(undefined);
  }, [cronValue, triggerType]);

  const previewCron = async () => {
    try {
      const values = await form.validateFields(["cron"]);
      const cron = values.cron?.trim();
      if (!cron) return;
      setCronPreviewLoading(true);
      const result = await triggerApi.previewCron({ cron, count: 5 });
      setCronPreview(result);
    } catch (error) {
      if (typeof error === "object" && error && "errorFields" in error) return;
      message.error(error instanceof Error ? error.message : "运行查看失败");
    } finally {
      setCronPreviewLoading(false);
    }
  };

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
          <>
            <Form.Item label="CRON 表达式" required>
              <Space.Compact style={{ width: "100%" }}>
                <Form.Item
                  name="cron"
                  noStyle
                  rules={[{ required: true, message: "请输入 CRON 表达式" }]}
                >
                  <Input placeholder="例如 0 0 2 * * ?" />
                </Form.Item>
                <Button loading={cronPreviewLoading} onClick={() => void previewCron()}>
                  运行查看
                </Button>
              </Space.Compact>
            </Form.Item>
            {cronPreview?.nextTimes?.length ? (
              <List
                bordered
                size="small"
                header={
                  <Typography.Text type="secondary">
                    Java Cron 后续运行时间{cronPreview.timeZone ? `（${cronPreview.timeZone}）` : ""}
                  </Typography.Text>
                }
                dataSource={cronPreview.nextTimes}
                renderItem={(item, index) => (
                  <List.Item>
                    <Space>
                      <Typography.Text type="secondary">#{index + 1}</Typography.Text>
                      <Typography.Text>{dayjs(item).format("YYYY-MM-DD HH:mm:ss")}</Typography.Text>
                    </Space>
                  </List.Item>
                )}
                style={{ marginBottom: 24 }}
              />
            ) : null}
          </>
        )}
      </Form>
    </Drawer>
  );
}

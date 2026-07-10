import { App, DatePicker, Form, Modal, Select, Typography } from "antd";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { flowApi, schedulerFlowRelationApi } from "../../api";
import { scheduleWindowPresetDefinitions } from "../../constants";
import type { FlowItem, TriggerListItem } from "../../dto";
import { normalizeTimestamp } from "../../utils";

interface FlowScheduleModalProps {
  open: boolean;
  currentRecord?: FlowItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

interface FlowScheduleFormValues {
  triggerId: string;
  scheduleWindow: [Dayjs, Dayjs];
}

function normalizeTriggerOption(item: TriggerListItem) {
  return {
    label: item.name || item.triggerName || item.id,
    value: String(item.id),
  };
}

function createScheduleWindow(durationMonths = 12): [Dayjs, Dayjs] {
  const startTime = dayjs().startOf("day");
  const endTime = startTime.add(durationMonths, "month").endOf("day");
  return [startTime, endTime];
}

function createCurrentDayScheduleWindow(): [Dayjs, Dayjs] {
  const startTime = dayjs().millisecond(0);
  return [startTime, startTime.endOf("day")];
}

const scheduleWindowPresets = scheduleWindowPresetDefinitions.map((definition) => ({
  label: definition.label,
  value: () => definition.type === "CURRENT_DAY"
    ? createCurrentDayScheduleWindow()
    : createScheduleWindow(definition.durationMonths),
}));

const scheduleWindowDefaultOpenTime: [Dayjs, Dayjs] = [
  dayjs().startOf("day"),
  dayjs().endOf("day"),
];

export function FlowScheduleModal({
  open,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: FlowScheduleModalProps) {
  const [form] = Form.useForm<FlowScheduleFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const { message } = App.useApp();
  const published = Boolean(currentRecord?.publishState);

  const triggerQuery = useQuery({
    queryKey: ["scheduler-flow-triggers"],
    queryFn: schedulerFlowRelationApi.triggers,
    enabled: open,
  });

  const triggerOptions = useMemo(
    () => (Array.isArray(triggerQuery.data) ? triggerQuery.data.map(normalizeTriggerOption) : []),
    [triggerQuery.data],
  );

  useEffect(() => {
    if (!open) return;

    const savedStartTime = normalizeTimestamp(currentRecord?.startTime);
    const savedEndTime = normalizeTimestamp(currentRecord?.endTime);
    const scheduleWindow: [Dayjs, Dayjs] =
      savedStartTime && savedEndTime
        ? [dayjs(savedStartTime), dayjs(savedEndTime)]
        : createScheduleWindow();

    form.setFieldsValue({
      triggerId: currentRecord?.triggerId ? String(currentRecord.triggerId) : undefined,
      scheduleWindow,
    });
  }, [currentRecord, form, open]);

  const handleClose = () => {
    form.resetFields();
    onClose();
  };

  const handleSubmit = async () => {
    if (!currentRecord?.id) return;

    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await flowApi.enable({
        id: currentRecord.id,
        triggerId: values.triggerId,
        startTime: values.scheduleWindow[0].valueOf(),
        endTime: values.scheduleWindow[1].valueOf(),
      });
      message.success(published ? "开始调度成功" : "发布并开始调度成功");
      onSubmitSuccess();
      handleClose();
    } catch (error) {
      if (typeof error === "object" && error && "errorFields" in error) return;
      message.error(error instanceof Error ? error.message : "开始调度失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="调度配置"
      open={open}
      okText={published ? "开始调度" : "发布并开始调度"}
      cancelText="取消"
      confirmLoading={submitting}
      cancelButtonProps={{ disabled: submitting }}
      onCancel={handleClose}
      onOk={() => void handleSubmit()}
    >
      <Form form={form} layout="vertical">
        {!published ? (
          <Typography.Paragraph type="secondary">
            当前流程尚未发布，开始调度后将自动发布。
          </Typography.Paragraph>
        ) : null}
        <Form.Item name="triggerId" label="触发器" rules={[{ required: true, message: "请选择触发器" }]}>
          <Select
            showSearch
            optionFilterProp="label"
            options={triggerOptions}
            placeholder="请选择触发器"
            loading={triggerQuery.isFetching}
          />
        </Form.Item>
        <Form.Item
          name="scheduleWindow"
          label="调度窗口"
          rules={[
            { required: true, message: "请选择调度窗口" },
            {
              validator: (_, value?: [Dayjs, Dayjs]) =>
                !value || value[1].isAfter(value[0])
                  ? Promise.resolve()
                  : Promise.reject(new Error("调度结束时间必须晚于开始时间")),
            },
          ]}
        >
          <DatePicker.RangePicker
            showTime={{ defaultOpenValue: scheduleWindowDefaultOpenTime }}
            presets={scheduleWindowPresets}
            style={{ width: "100%" }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

import { Button, DatePicker, Drawer, Form, Input, Select, Space, Spin } from "antd";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { flowApi, schedulerFlowRelationApi } from "../../api";
import { flowTypeOptions } from "../../constants";
import type { EventListItem, FlowFormMode, FlowItem, TriggerListItem } from "../../dto";
import { formatJsonText, normalizeStringArray, normalizeTimestamp } from "../../utils";
import { JsonEditor } from "../json-editor";
import { useFlowSubmit } from "./use-submit";

interface FlowFormProps {
  open: boolean;
  mode: FlowFormMode;
  currentRecord?: FlowItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

function normalizeTriggerOption(item: TriggerListItem) {
  return {
    label: item.name || item.triggerName || item.id,
    value: String(item.id),
  };
}

function normalizeEventOption(item: EventListItem) {
  return {
    label: item.eventName || item.name || item.id,
    value: String(item.id),
  };
}

export function FlowForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: FlowFormProps) {
  const [detailData, setDetailData] = useState<FlowItem>();
  const [loading, setLoading] = useState(false);
  const { form, title, submit } = useFlowSubmit({
    mode,
    currentRecord: detailData || currentRecord,
    onClose,
    onSubmitSuccess,
  });

  const triggerQuery = useQuery({
    queryKey: ["scheduler-flow-triggers"],
    queryFn: schedulerFlowRelationApi.triggers,
    enabled: open,
  });

  const eventQuery = useQuery({
    queryKey: ["scheduler-flow-events"],
    queryFn: schedulerFlowRelationApi.events,
    enabled: open,
  });

  const triggerOptions = useMemo(
    () => (Array.isArray(triggerQuery.data) ? triggerQuery.data.map(normalizeTriggerOption) : []),
    [triggerQuery.data],
  );

  const eventOptions = useMemo(
    () => (Array.isArray(eventQuery.data) ? eventQuery.data.map(normalizeEventOption) : []),
    [eventQuery.data],
  );

  useEffect(() => {
    if (!open) return;

    const initForm = async () => {
      if (mode === "edit" && currentRecord?.id) {
        setLoading(true);
        try {
          const detail = await flowApi.detail(currentRecord.id);
          const startTime = normalizeTimestamp(detail.startTime);
          const endTime = normalizeTimestamp(detail.endTime);

          setDetailData(detail);
          form.setFieldsValue({
            flowName: detail.flowName,
            flowCode: detail.flowCode,
            groupId: detail.groupId,
            description: detail.description,
            flowType: detail.flowType,
            triggerId: detail.triggerId ? String(detail.triggerId) : undefined,
            depEventIds: normalizeStringArray(detail.depEventIds),
            scheduleWindow: startTime && endTime ? [dayjs(startTime), dayjs(endTime)] : undefined,
            flowParamText: formatJsonText(detail.flowParam),
          });
        } finally {
          setLoading(false);
        }
        return;
      }

      setDetailData(undefined);
      form.resetFields();
      form.setFieldsValue({ flowType: "2" });
    };

    void initForm();
  }, [currentRecord, form, mode, open]);

  const handleClose = () => {
    form.resetFields();
    setDetailData(undefined);
    onClose();
  };

  return (
    <Drawer
      title={title}
      open={open}
      width={760}
      onClose={handleClose}
      extra={
        <Space>
          <Button onClick={handleClose}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading || triggerQuery.isFetching || eventQuery.isFetching}>
        <Form form={form} layout="vertical">
          <Form.Item
            name="flowName"
            label="流程名称"
            rules={[
              { required: true, message: "请输入流程名称" },
              { max: 255, message: "流程名称不能超过255个字符" },
            ]}
          >
            <Input placeholder="请输入流程名称" />
          </Form.Item>
          <Form.Item
            name="flowCode"
            label="流程编码"
            rules={[
              { required: true, message: "请输入流程编码" },
              { max: 255, message: "流程编码不能超过255个字符" },
            ]}
          >
            <Input placeholder="请输入流程编码" disabled={mode === "edit"} />
          </Form.Item>
          <Form.Item name="flowType" label="流程类型" rules={[{ required: true, message: "请选择流程类型" }]}>
            <Select options={flowTypeOptions} placeholder="请选择流程类型" />
          </Form.Item>
          <Form.Item name="triggerId" label="触发器" rules={[{ required: true, message: "请选择触发器" }]}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={triggerOptions}
              placeholder="请选择触发器"
              loading={triggerQuery.isFetching}
            />
          </Form.Item>
          <Form.Item name="scheduleWindow" label="调度窗口">
            <DatePicker.RangePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item name="groupId" label="流程分组">
            <Input placeholder="请输入流程分组 ID" />
          </Form.Item>
          <Form.Item name="depEventIds" label="依赖事件">
            <Select
              allowClear
              mode="multiple"
              optionFilterProp="label"
              options={eventOptions}
              placeholder="请选择依赖事件"
              loading={eventQuery.isFetching}
            />
          </Form.Item>
          <Form.Item name="description" label="流程描述" rules={[{ max: 1000, message: "流程描述不能超过1000个字符" }]}>
            <Input.TextArea rows={4} placeholder="请输入流程描述" />
          </Form.Item>
          <Form.Item name="flowParamText" label="流程参数">
            <JsonEditor title="流程参数 JSON" placeholder="请输入流程参数（JSON 格式）" rows={10} />
          </Form.Item>
        </Form>
      </Spin>
    </Drawer>
  );
}

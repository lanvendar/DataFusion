import { Button, Drawer, Form, Input, Select, Space, Spin } from "antd";
import { useEffect, useState } from "react";
import { taskApi } from "../../api";
import { taskTypeOptions } from "../../constants";
import type { TaskFormMode, TaskItem } from "../../dto";
import { formatJsonText } from "../../utils";
import { JsonEditor } from "../json-editor";
import { useTaskSubmit } from "./use-submit";

interface TaskFormProps {
  open: boolean;
  mode: TaskFormMode;
  currentRecord?: TaskItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function TaskForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: TaskFormProps) {
  const [detailData, setDetailData] = useState<TaskItem>();
  const [loading, setLoading] = useState(false);
  const { form, title, submit } = useTaskSubmit({
    mode,
    currentRecord: detailData || currentRecord,
    onClose,
    onSubmitSuccess,
  });

  useEffect(() => {
    if (!open) return;

    const initForm = async () => {
      if (mode === "edit" && currentRecord?.id) {
        setLoading(true);
        try {
          const detail = await taskApi.detail(currentRecord.id);
          setDetailData(detail);
          form.setFieldsValue({
            taskName: detail.taskName,
            taskCode: detail.taskCode,
            description: detail.description,
            taskTypeId: detail.taskTypeId || detail.taskType,
            taskParamText: formatJsonText(detail.taskParam),
            definitionText: formatJsonText(detail.definition),
          });
        } finally {
          setLoading(false);
        }
        return;
      }

      setDetailData(undefined);
      form.resetFields();
      form.setFieldsValue({
        taskTypeId: "DATAX",
      });
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
      <Spin spinning={loading}>
        <Form form={form} layout="vertical">
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[
              { required: true, message: "请输入任务名称" },
              { max: 255, message: "任务名称不能超过255个字符" },
            ]}
          >
            <Input placeholder="请输入任务名称" />
          </Form.Item>
          <Form.Item
            name="taskCode"
            label="任务编码"
            rules={[
              { required: true, message: "请输入任务编码" },
              { max: 255, message: "任务编码不能超过255个字符" },
            ]}
          >
            <Input placeholder="请输入任务编码" disabled={mode === "edit"} />
          </Form.Item>
          <Form.Item name="taskTypeId" label="任务类型" rules={[{ required: true, message: "请选择任务类型" }]}>
            <Select options={taskTypeOptions} placeholder="请选择任务类型" />
          </Form.Item>
          <Form.Item name="description" label="任务描述" rules={[{ max: 1000, message: "任务描述不能超过1000个字符" }]}>
            <Input.TextArea rows={4} placeholder="请输入任务描述" />
          </Form.Item>
          <Form.Item name="taskParamText" label="任务变量参数">
            <JsonEditor title="任务变量参数 JSON" placeholder="请输入任务变量参数（JSON 格式）" />
          </Form.Item>
          <Form.Item name="definitionText" label="任务定义">
            <JsonEditor title="任务定义 JSON" placeholder="请输入任务定义（JSON 格式）" rows={10} />
          </Form.Item>
        </Form>
      </Spin>
    </Drawer>
  );
}

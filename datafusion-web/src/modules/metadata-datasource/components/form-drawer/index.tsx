import { Button, Drawer, Form, Input, InputNumber, Select, Space, Tabs } from "antd";
import { useEffect } from "react";
import { databaseTypeOptions } from "../../constants";
import {
  type DatasourceFormMode,
  type DatasourceItem,
  type JsonParamItem,
} from "../../dto";
import { JsonParamsEditor } from "./json-params";
import { useDatasourceSubmit } from "./use-submit";

interface DatasourceFormProps {
  open: boolean;
  mode: DatasourceFormMode;
  currentRecord?: DatasourceItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

function parseExtendParamToJsonParamItems(extendParam: unknown): JsonParamItem[] {
  if (!extendParam) return [];
  let objectValue: unknown = extendParam;
  if (typeof extendParam === "string") {
    try {
      objectValue = JSON.parse(extendParam || "{}");
    } catch {
      return [];
    }
  }
  if (!objectValue || typeof objectValue !== "object" || Array.isArray(objectValue)) return [];
  return Object.entries(objectValue as Record<string, unknown>).map(([paramName, paramValue]) => ({
    paramName,
    paramValue: paramValue == null ? "" : String(paramValue),
  }));
}

function buildJsonParams(record?: DatasourceItem): JsonParamItem[] {
  if (!record) return [];
  const params = [...(record.jsonParams || [])];
  const exists = new Set(params.map((item) => item.paramName));
  for (const item of parseExtendParamToJsonParamItems(record.extendParam)) {
    if (!exists.has(item.paramName)) params.push(item);
  }
  return params;
}

export function DatasourceForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: DatasourceFormProps) {
  const { form, title, submit, testConnection } = useDatasourceSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });

  useEffect(() => {
    if (!open) return;
    if ((mode === "edit" || mode === "copy") && currentRecord) {
      form.setFieldsValue({
        ...currentRecord,
        id: mode === "edit" ? currentRecord.id : undefined,
        name: mode === "copy" ? `${currentRecord.name}_copy` : currentRecord.name,
        jsonParams: buildJsonParams(currentRecord),
      });
    } else {
      form.resetFields();
    }
  }, [currentRecord, form, mode, open]);

  return (
    <Drawer
      title={title}
      open={open}
      width={680}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={testConnection}>连接测试</Button>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Tabs
          items={[
            {
              key: "basic",
              label: "基础信息",
              children: (
                <>
                  <Form.Item name="name" label="数据链接名称" rules={[{ required: true }]}>
                    <Input placeholder="请输入数据链接名称" />
                  </Form.Item>
                  <Form.Item name="databaseType" label="数据库类型" rules={[{ required: true }]}>
                    <Select options={databaseTypeOptions} placeholder="请选择数据库类型" />
                  </Form.Item>
                  <Form.Item name="databaseName" label="数据库名称" rules={[{ required: true }]}>
                    <Input placeholder="请输入数据库名称" />
                  </Form.Item>
                  <Form.Item name="schemaName" label="表空间名称">
                    <Input placeholder="请输入表空间名称" />
                  </Form.Item>
                  <Form.Item name="host" label="地址" rules={[{ required: true }]}>
                    <Input placeholder="请输入地址" />
                  </Form.Item>
                  <Form.Item name="port" label="端口" rules={[{ required: true }]}>
                    <InputNumber className="full-input" placeholder="请输入端口" />
                  </Form.Item>
                  <Form.Item name="username" label="登录用户">
                    <Input placeholder="请输入登录用户" />
                  </Form.Item>
                  <Form.Item name="password" label="登录密码">
                    <Input.Password placeholder="请输入登录密码" />
                  </Form.Item>
                </>
              ),
            },
            {
              key: "extra",
              label: "附加参数",
              children: <JsonParamsEditor />,
            },
          ]}
        />
      </Form>
    </Drawer>
  );
}

import { Button, Drawer, Form, Input, Select, Space, Switch, Tabs } from "antd";
import { useEffect, useState } from "react";
import { tableStructureApi } from "../../api";
import type {
  DatasourceOptionItem,
  TableStructureFormMode,
  TableStructureItem,
  TableStructureSaveReq,
} from "../../dto";
import { useTableStructureSubmit } from "./use-submit";

interface TableStructureFormProps {
  open: boolean;
  mode: TableStructureFormMode;
  currentRecord?: TableStructureItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

type TableStructureFormValues = Omit<TableStructureSaveReq, "tableProperties"> & {
  tableProperties?: string;
};

function parseProperties(value?: Record<string, unknown>) {
  if (!value) return "";
  return JSON.stringify(value, null, 2);
}

function parsePropertiesText(value?: string) {
  if (!value?.trim()) return undefined;
  return JSON.parse(value) as Record<string, unknown>;
}

export function TableStructureForm({
  open,
  mode,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: TableStructureFormProps) {
  const { form, title, submit } = useTableStructureSubmit({
    mode,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });
  const [datasources, setDatasources] = useState<DatasourceOptionItem[]>([]);

  useEffect(() => {
    if (!open) return;
    tableStructureApi.datasourceList().then(setDatasources).catch(() => setDatasources([]));
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && currentRecord) {
      const { tableProperties, ...basicValues } = currentRecord;
      form.setFieldsValue(basicValues);
      form.setFieldValue("tableProperties" as never, parseProperties(tableProperties));
    } else {
      form.resetFields();
    }
  }, [currentRecord, form, mode, open]);

  const datasourceOptions = datasources.map((item) => ({
    label: item.name,
    value: item.id,
  }));

  const handleSubmit = async () => {
    const values = form.getFieldsValue(true) as TableStructureFormValues;
    if (typeof values.tableProperties === "string") {
      form.setFieldValue("tableProperties", parsePropertiesText(values.tableProperties));
    }
    await submit();
  };

  return (
    <Drawer
      title={title}
      open={open}
      width={720}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSubmit}>
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
                  <Form.Item name="datasourceId" label="数据源链接">
                    <Select allowClear showSearch options={datasourceOptions} placeholder="请选择数据源链接" />
                  </Form.Item>
                  <Form.Item name="databaseConnectName" label="数据源链接名称">
                    <Input placeholder="请输入数据源链接名称" />
                  </Form.Item>
                  <Form.Item name="databaseName" label="数据库名称">
                    <Input placeholder="请输入数据库名称" />
                  </Form.Item>
                  <Form.Item name="schemaName" label="表空间名称">
                    <Input placeholder="请输入表空间名称" />
                  </Form.Item>
                  <Form.Item name="tableName" label="表名称" rules={[{ required: true }]}>
                    <Input placeholder="请输入表名称" />
                  </Form.Item>
                  <Form.Item name="tableDesc" label="表注释">
                    <Input.TextArea placeholder="请输入表注释" autoSize={{ minRows: 3, maxRows: 6 }} />
                  </Form.Item>
                  <Form.Item name="isView" label="是否视图" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </>
              ),
            },
            {
              key: "view",
              label: "视图/属性",
              children: (
                <>
                  <Form.Item name="viewDef" label="视图定义">
                    <Input.TextArea placeholder="请输入视图定义" autoSize={{ minRows: 5, maxRows: 12 }} />
                  </Form.Item>
                  <Form.Item
                    name="tableProperties"
                    label="表属性 JSON"
                    rules={[
                      {
                        validator: (_, value) => {
                          if (!value || typeof value !== "string") return Promise.resolve();
                          try {
                            JSON.parse(value);
                            return Promise.resolve();
                          } catch {
                            return Promise.reject(new Error("请输入合法 JSON"));
                          }
                        },
                      },
                    ]}
                  >
                    <Input.TextArea placeholder='例如 {"bucket":"4"}' autoSize={{ minRows: 6, maxRows: 12 }} />
                  </Form.Item>
                </>
              ),
            },
          ]}
        />
      </Form>
    </Drawer>
  );
}

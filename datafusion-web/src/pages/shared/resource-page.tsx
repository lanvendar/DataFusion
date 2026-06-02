import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useMemo, useState } from "react";
import { PageHeader } from "@/components/page-header";
import {
  request,
  type ApiPage,
  type PageQuery,
} from "@/api/http";

export type FieldType = "text" | "number" | "select" | "textarea" | "boolean";

export interface SelectOption {
  label: string;
  value: string | number | boolean;
}

export interface ResourceField<T extends Record<string, unknown>> {
  key: keyof T & string;
  label: string;
  width?: number;
  type?: FieldType;
  required?: boolean;
  hiddenInTable?: boolean;
  hiddenInForm?: boolean;
  options?: SelectOption[];
  render?: (value: unknown, record: T) => React.ReactNode;
}

export interface ResourceEndpoint {
  list: string;
  add?: string;
  update?: string;
  delete?: string;
  detail?: string;
}

export interface RowAction<T extends Record<string, unknown>> {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: (record: T) => boolean;
  onClick: (record: T, helpers: ResourceActionHelpers) => void | Promise<void>;
}

export interface ResourceActionHelpers {
  refresh: () => void;
}

export interface ResourcePageProps<T extends Record<string, unknown>> {
  title: string;
  description: string;
  breadcrumb: string[];
  rowKey?: keyof T & string;
  entityName: string;
  endpoints: ResourceEndpoint;
  fields: ResourceField<T>[];
  demoRows: T[];
  searchPlaceholder?: string;
  mapSearch?: (keyword: string) => Record<string, unknown>;
  extraActions?: RowAction<T>[];
}

const DEFAULT_PAGE_SIZE = 10;

function fillPath(url: string, record: Record<string, unknown>) {
  return url.replace(/\{(\w+)}/g, (_, key) => String(record[key] ?? ""));
}

function pageRows<T>(page: ApiPage<T> | T[] | undefined): T[] {
  if (!page) return [];
  if (Array.isArray(page)) return page;
  return page.dataList || page.records || page.list || [];
}

function pageTotal<T>(page: ApiPage<T> | T[] | undefined, fallback: number) {
  if (!page || Array.isArray(page)) return fallback;
  return page.total ?? fallback;
}

function renderFieldValue<T extends Record<string, unknown>>(
  field: ResourceField<T>,
  value: unknown,
  record: T,
) {
  if (field.render) return field.render(value, record);
  if (field.type === "boolean") {
    return <Tag color={value ? "green" : "default"}>{value ? "是" : "否"}</Tag>;
  }
  if (typeof value === "boolean") return value ? "是" : "否";
  return value === undefined || value === null || value === "" ? "-" : String(value);
}

export default function ResourcePage<T extends Record<string, unknown>>({
  title,
  description,
  breadcrumb,
  rowKey = "id" as keyof T & string,
  entityName,
  endpoints,
  fields,
  demoRows,
  searchPlaceholder,
  mapSearch,
  extraActions = [],
}: ResourcePageProps<T>) {
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState("");
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingRecord, setEditingRecord] = useState<T | undefined>();
  const [viewRecord, setViewRecord] = useState<T | undefined>();
  const [form] = Form.useForm();

  const query = useQuery({
    queryKey: [title, endpoints.list, current, pageSize, keyword],
    queryFn: async () => {
      const option = keyword ? mapSearch?.(keyword) || { keyword } : {};
      try {
        return await request<ApiPage<T>>({
          url: endpoints.list,
          method: "POST",
          data: {
            current,
            size: pageSize,
            option,
          } satisfies PageQuery,
        });
      } catch (error) {
        console.warn(`Using demo rows for ${title}`, error);
        return {
          dataList: demoRows,
          current,
          size: pageSize,
          total: demoRows.length,
        } satisfies ApiPage<T>;
      }
    },
  });

  const saveMutation = useMutation({
    mutationFn: async (values: Partial<T>) => {
      const isEdit = Boolean(editingRecord);
      const url = isEdit ? endpoints.update : endpoints.add;
      if (!url) return;
      await request({
        url,
        method: "POST",
        data: isEdit ? { ...editingRecord, ...values } : values,
      });
    },
    onSuccess: () => {
      message.success("保存成功");
      setEditingRecord(undefined);
      form.resetFields();
      void query.refetch();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (record: T) => {
      if (!endpoints.delete) return;
      await request({
        url: fillPath(endpoints.delete, record),
        method: endpoints.delete.includes("{") ? "DELETE" : "POST",
        data: record,
      });
    },
    onSuccess: () => {
      message.success("删除成功");
      void query.refetch();
    },
  });

  const dataSource = pageRows(query.data);
  const total = pageTotal(query.data, dataSource.length);

  const startAdd = () => {
    setEditingRecord({} as T);
    form.resetFields();
  };

  const startEdit = useCallback((record: T) => {
    setEditingRecord(record);
    form.setFieldsValue(record);
  }, [form]);

  const columns = useMemo<ColumnsType<T>>(() => {
    const tableColumns: ColumnsType<T> = fields
      .filter((field) => !field.hiddenInTable)
      .map((field): ColumnsType<T>[number] => ({
        title: field.label,
        dataIndex: field.key,
        key: field.key,
        width: field.width,
        ellipsis: true,
        render: (value: unknown, record: T) =>
          renderFieldValue(field, value, record),
      }));

    tableColumns.push({
      title: "操作",
      key: "action",
      width: 220,
      fixed: "right",
      render: (_, record: T) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => setViewRecord(record)}>
            查看
          </Button>
          {endpoints.update ? (
            <Button type="link" icon={<EditOutlined />} onClick={() => startEdit(record)}>
              编辑
            </Button>
          ) : null}
          {extraActions.map((action) => (
            <Button
              key={action.key}
              type="link"
              danger={action.danger}
              disabled={action.disabled?.(record)}
              onClick={() =>
                void action.onClick(record, {
                  refresh: () => void query.refetch(),
                })
              }
            >
              {action.label}
            </Button>
          ))}
          {endpoints.delete ? (
            <Button
              type="link"
              danger
              icon={<DeleteOutlined />}
              onClick={() => {
                Modal.confirm({
                  title: `确认删除该${entityName}吗？`,
                  onOk: () => deleteMutation.mutate(record),
                });
              }}
            >
              删除
            </Button>
          ) : null}
        </Space>
      ),
    } as ColumnsType<T>[number]);

    return tableColumns;
  }, [deleteMutation, endpoints.delete, endpoints.update, entityName, extraActions, fields, query, startEdit]);

  const renderFormItem = (field: ResourceField<T>) => {
    if (field.hiddenInForm) return null;
    const rules = field.required ? [{ required: true, message: `请输入${field.label}` }] : [];
    const commonProps = {
      placeholder: `请输入${field.label}`,
    };

    return (
      <Form.Item key={field.key} name={String(field.key)} label={field.label} rules={rules}>
        {field.type === "number" ? (
          <InputNumber className="full-input" {...commonProps} />
        ) : field.type === "select" || field.type === "boolean" ? (
          <Select
            allowClear
            placeholder={`请选择${field.label}`}
            options={
              field.type === "boolean"
                ? [
                    { label: "是", value: true },
                    { label: "否", value: false },
                  ]
                : field.options
            }
          />
        ) : field.type === "textarea" ? (
          <Input.TextArea rows={4} {...commonProps} />
        ) : (
          <Input {...commonProps} />
        )}
      </Form.Item>
    );
  };

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={breadcrumb.map((label) => ({ label }))}
        title={title}
        description={description}
        actions={
          <Space wrap>
          <Input.Search
            allowClear
            enterButton={<SearchOutlined />}
            placeholder={searchPlaceholder || `搜索${entityName}`}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={() => {
              setCurrent(1);
              void query.refetch();
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void query.refetch()}>
            刷新
          </Button>
          {endpoints.add ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={startAdd}>
              新增{entityName}
            </Button>
          ) : null}
          </Space>
        }
      />

      <Card>
        <Table<T>
          rowKey={(record) => String(record[rowKey] ?? "")}
          loading={query.isFetching || deleteMutation.isPending}
          columns={columns}
          dataSource={dataSource}
          scroll={{ x: "max-content" }}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextCurrent, nextSize) => {
              setCurrent(nextCurrent);
              setPageSize(nextSize);
            },
          }}
        />
      </Card>

      <Drawer
        title={`${editingRecord && editingRecord[rowKey] ? "编辑" : "新增"}${entityName}`}
        open={Boolean(editingRecord)}
        width={560}
        onClose={() => setEditingRecord(undefined)}
        extra={
          <Space>
            <Button onClick={() => setEditingRecord(undefined)}>取消</Button>
            <Button
              type="primary"
              loading={saveMutation.isPending}
              onClick={() => void form.validateFields().then((values) => saveMutation.mutate(values))}
            >
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          {fields.map(renderFormItem)}
        </Form>
      </Drawer>

      <Drawer
        title={`${entityName}详情`}
        open={Boolean(viewRecord)}
        width={640}
        onClose={() => setViewRecord(undefined)}
      >
        {viewRecord ? (
          <Descriptions bordered column={1} size="small">
            {fields.map((field) => (
              <Descriptions.Item label={field.label} key={field.key}>
                {renderFieldValue(field, viewRecord[field.key], viewRecord)}
              </Descriptions.Item>
            ))}
          </Descriptions>
        ) : null}
      </Drawer>
    </Space>
  );
}

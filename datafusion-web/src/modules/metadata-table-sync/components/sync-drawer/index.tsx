import { Button, Drawer, Form, Select, Space, Steps, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { tableSyncApi } from "../../api";
import type {
  DatasourceOptionItem,
  SyncDrawerValues,
  SyncMode,
  TableInfoItem,
} from "../../dto";

interface TableSyncDrawerProps {
  open: boolean;
  mode: SyncMode;
  onClose: () => void;
  onSubmit: (mode: SyncMode, values: SyncDrawerValues, source?: DatasourceOptionItem) => Promise<void>;
}

export function TableSyncDrawer({ open, mode, onClose, onSubmit }: TableSyncDrawerProps) {
  const [form] = Form.useForm<SyncDrawerValues>();
  const [datasources, setDatasources] = useState<DatasourceOptionItem[]>([]);
  const [tables, setTables] = useState<TableInfoItem[]>([]);
  const [loadingTables, setLoadingTables] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setTables([]);
    tableSyncApi.datasourceList().then(setDatasources).catch(() => setDatasources([]));
  }, [form, open]);

  const datasourceOptions = datasources.map((item) => ({
    label: `${item.name}${item.databaseName ? ` / ${item.databaseName}` : ""}`,
    value: item.id,
  }));

  const tableOptions = tables.map((item) => ({
    label: item.tableName || item.name || "",
    value: item.tableName || item.name || "",
  }));

  const selectedSourceId = Form.useWatch("sourceDatasourceId", form);
  const source = useMemo(
    () => datasources.find((item) => item.id === selectedSourceId),
    [datasources, selectedSourceId],
  );

  const loadTables = async (datasourceId: string) => {
    form.setFieldValue("tableNames", []);
    setLoadingTables(true);
    try {
      const next = await tableSyncApi.getTableInfos(datasourceId);
      setTables(Array.isArray(next) ? next : []);
      const ds = datasources.find((item) => item.id === datasourceId);
      if (ds?.databaseType) {
        const defaults = await tableSyncApi.defaultColumns(ds.databaseType).catch(() => []);
        form.setFieldValue(
          "defaultColumns",
          defaults.map((item) => item.key || item.value || item.name || item.label).filter(Boolean),
        );
      }
    } finally {
      setLoadingTables(false);
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await onSubmit(mode, values, source);
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={mode === "create" ? "批量创建表结构" : "批量对比表结构"}
      open={open}
      width={760}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={submitting} onClick={submit}>
            执行
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={20} className="page-stack">
        <Steps
          current={1}
          items={[
            { title: "选择数据源" },
            { title: "选择表" },
            { title: "提交同步" },
          ]}
        />
        <Typography.Paragraph type="secondary">
          选择源端和目标端数据源后，按表名生成批量同步请求。批量创建会将源表映射到同名目标表，批量对比会提交同一组表进行结构同步。
        </Typography.Paragraph>
        <Form form={form} layout="vertical">
          <Form.Item name="sourceDatasourceId" label="源数据源" rules={[{ required: true }]}>
            <Select
              showSearch
              options={datasourceOptions}
              placeholder="请选择源数据源"
              onChange={loadTables}
            />
          </Form.Item>
          <Form.Item name="targetDatasourceId" label="目标数据源" rules={[{ required: true }]}>
            <Select showSearch options={datasourceOptions} placeholder="请选择目标数据源" />
          </Form.Item>
          <Form.Item name="tableNames" label="源表" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              loading={loadingTables}
              options={tableOptions}
              placeholder="请选择需要同步的表"
            />
          </Form.Item>
          <Form.Item name="defaultColumns" label="默认公共字段">
            <Select mode="tags" tokenSeparators={[","]} placeholder="可输入默认字段，回车确认" />
          </Form.Item>
        </Form>
      </Space>
    </Drawer>
  );
}

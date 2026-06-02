import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { App, Button, Card, Input, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { PageHeader } from "@/components/page-header";
import {
  createDemoRows,
  type DemoRow,
  postPage,
} from "@/api/http";

interface DataTablePageProps {
  title: string;
  description: string;
  entityName: string;
  apiPath: string;
  breadcrumb: string[];
}

const statusMap = {
  enabled: { color: "success", text: "启用" },
  disabled: { color: "default", text: "禁用" },
  draft: { color: "processing", text: "草稿" },
} as const;

export default function DataTablePage({
  title,
  description,
  entityName,
  apiPath,
  breadcrumb,
}: DataTablePageProps) {
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState("");

  const { data, isFetching, refetch, error } = useQuery({
    queryKey: ["page", apiPath, keyword],
    queryFn: async () => {
      try {
        const page = await postPage<DemoRow>(apiPath, {
          current: 1,
          size: 20,
          option: keyword ? { keyword } : {},
        });
        return page.dataList || page.records || page.list || [];
      } catch (requestError) {
        console.warn(`Using demo rows for ${apiPath}`, requestError);
        return createDemoRows(entityName);
      }
    },
  });

  const columns = useMemo<ColumnsType<DemoRow>>(
    () => [
      {
        title: "编码",
        dataIndex: "code",
        width: 180,
      },
      {
        title: "名称",
        dataIndex: "name",
      },
      {
        title: "负责人",
        dataIndex: "owner",
        width: 140,
      },
      {
        title: "业务域",
        dataIndex: "domain",
        width: 140,
      },
      {
        title: "状态",
        dataIndex: "status",
        width: 120,
        render: (value: DemoRow["status"]) => (
          <Tag color={statusMap[value].color}>{statusMap[value].text}</Tag>
        ),
      },
      {
        title: "更新时间",
        dataIndex: "updatedAt",
        width: 140,
      },
      {
        title: "操作",
        key: "action",
        width: 180,
        render: (_, record) => (
          <Space>
            <Button type="link" onClick={() => message.info(`查看 ${record.name}`)}>
              查看
            </Button>
            <Button type="link" onClick={() => message.info(`编辑 ${record.name}`)}>
              编辑
            </Button>
          </Space>
        ),
      },
    ],
    [message],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={breadcrumb.map((label) => ({ label }))}
        title={title}
        description={description}
        actions={
          <Space>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder={`搜索${entityName}`}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => void refetch()}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
            刷新
          </Button>
          <Button type="primary" onClick={() => message.info(`新增${entityName}`)}>
            新增{entityName}
          </Button>
          </Space>
        }
      />

      <Card>
        <Table
          rowKey="id"
          loading={isFetching}
          columns={columns}
          dataSource={data}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Card>

      {error ? (
        <Typography.Text type="secondary">
          后端接口暂不可用时，页面会自动展示演示数据，方便继续迁移前端结构。
        </Typography.Text>
      ) : null}
    </Space>
  );
}

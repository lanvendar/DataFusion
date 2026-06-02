import { Button, Descriptions, Empty, Space, Spin, Table, Tabs } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { PageHeader } from "@/components/page-header";
import { tableStructureApi } from "./api";
import type { ColumnInfoDto } from "./dto";

const columnColumns: ColumnsType<ColumnInfoDto> = [
  { title: "序号", dataIndex: "columnSerial", key: "columnSerial", width: 80 },
  { title: "字段名称", dataIndex: "columnName", key: "columnName", width: 180 },
  { title: "字段类型", dataIndex: "columnType", key: "columnType", width: 140 },
  { title: "长度", dataIndex: "columnLength", key: "columnLength", width: 100 },
  { title: "精度", dataIndex: "columnPrecision", key: "columnPrecision", width: 100 },
  { title: "小数位", dataIndex: "columnScale", key: "columnScale", width: 100 },
  { title: "主键", dataIndex: "isPrimaryKey", key: "isPrimaryKey", width: 90, render: (value) => (value ? "是" : "否") },
  { title: "非空", dataIndex: "isNotNull", key: "isNotNull", width: 90, render: (value) => (value ? "是" : "否") },
  { title: "默认值", dataIndex: "defaultValue", key: "defaultValue", width: 140, ellipsis: true },
  { title: "字段注释", dataIndex: "columnDesc", key: "columnDesc", width: 220, ellipsis: true },
];

function formatTime(value: unknown) {
  if (value == null || value === "") return "-";
  const text = String(value);
  if (!text.includes("T")) return text;
  return text.replace("T", " ").slice(0, 19);
}

function formatBoolean(value?: boolean) {
  if (value === undefined || value === null) return "-";
  return value ? "是" : "否";
}

function SummaryItem({ label, value }: { label: string; value?: unknown }) {
  return (
    <div className="detail-summary-item">
      <div className="detail-summary-label">{label}</div>
      <div className="detail-summary-value">{value == null || value === "" ? "-" : String(value)}</div>
    </div>
  );
}

export default function MetadataTableStructureDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const tableId = String(id || "");

  const detailQuery = useQuery({
    queryKey: ["metadata-table-structure-detail", tableId],
    enabled: !!tableId,
    queryFn: () => tableStructureApi.detail(tableId),
  });
  const columnQuery = useQuery({
    queryKey: ["metadata-table-structure-columns", tableId],
    enabled: !!tableId,
    queryFn: () => tableStructureApi.columnList({ tableId }),
  });
  const storageQuery = useQuery({
    queryKey: ["metadata-table-structure-storage", tableId],
    enabled: !!tableId,
    queryFn: () => tableStructureApi.getRowCountAndSize(tableId),
  });
  const previewQuery = useQuery({
    queryKey: ["metadata-table-structure-preview", tableId],
    enabled: !!tableId,
    queryFn: () =>
      tableStructureApi.getMetaTableData({
        tableId,
        queryConditions: [],
        orderConditions: [],
        limit: 20,
      }),
  });

  const detail = detailQuery.data;
  const tableName = detail?.tableName || "表详情";
  const previewColumns = (previewQuery.data?.header || []).map((item) => ({
    title: item.title || item.columnName,
    dataIndex: item.field || item.columnName,
    key: item.field || item.columnName,
    width: 160,
    ellipsis: true,
  }));

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[
          { label: "元数据管理" },
          { label: "表结构管理", path: "/metadata-table-structure" },
          { label: tableName },
        ]}
        title={tableName}
        description="查看表基础信息、字段信息和数据预览。"
        actions={<Button onClick={() => navigate("/metadata-table-structure")}>返回列表</Button>}
      >
        <div className="detail-summary-grid">
          <SummaryItem label="数据源" value={detail?.databaseConnectName} />
          <SummaryItem label="数据库" value={detail?.databaseName} />
          <SummaryItem label="Schema" value={detail?.schemaName} />
          <SummaryItem label="表类型" value={detail?.isView ? "视图" : detail?.tableType || "普通表"} />
          <SummaryItem label="行数" value={storageQuery.data?.rowCount} />
          <SummaryItem label="存储大小" value={storageQuery.data?.tableSize || storageQuery.data?.size} />
          <SummaryItem label="字段数" value={columnQuery.data?.length} />
          <SummaryItem label="最近检查" value={formatTime(detail?.checkTime)} />
        </div>
      </PageHeader>

      <Spin spinning={detailQuery.isFetching}>
        {detail ? (
          <Tabs
            className="detail-panel"
            items={[
              {
                key: "basic",
                label: "基础信息",
                children: (
                  <div className="detail-tab-content">
                    <Descriptions bordered column={2} size="small">
                      <Descriptions.Item label="数据源链接">{detail.databaseConnectName || "-"}</Descriptions.Item>
                      <Descriptions.Item label="数据库">{detail.databaseName || "-"}</Descriptions.Item>
                      <Descriptions.Item label="数据库类型">{detail.databaseType || "-"}</Descriptions.Item>
                      <Descriptions.Item label="表空间">{detail.schemaName || "-"}</Descriptions.Item>
                      <Descriptions.Item label="表名称">{detail.tableName}</Descriptions.Item>
                      <Descriptions.Item label="表类型">{detail.tableType || "-"}</Descriptions.Item>
                      <Descriptions.Item label="是否视图">{formatBoolean(detail.isView)}</Descriptions.Item>
                      <Descriptions.Item label="是否同步">{formatBoolean(detail.isModify)}</Descriptions.Item>
                      <Descriptions.Item label="是否一致">{formatBoolean(detail.isEqual)}</Descriptions.Item>
                      <Descriptions.Item label="检查时间">{formatTime(detail.checkTime)}</Descriptions.Item>
                      <Descriptions.Item label="主键">{detail.primaryKeys || "-"}</Descriptions.Item>
                      <Descriptions.Item label="分区键">{detail.partitionKeys || "-"}</Descriptions.Item>
                      <Descriptions.Item label="表注释" span={2}>{detail.tableDesc || "-"}</Descriptions.Item>
                      <Descriptions.Item label="视图定义" span={2}>{detail.viewDef || "-"}</Descriptions.Item>
                    </Descriptions>
                  </div>
                ),
              },
              {
                key: "columns",
                label: "字段信息",
                children: (
                  <div className="detail-tab-content">
                    <Table<ColumnInfoDto>
                      rowKey={(row) => row.id || row.columnName}
                      loading={columnQuery.isFetching}
                      columns={columnColumns}
                      dataSource={columnQuery.data || []}
                      pagination={false}
                      scroll={{ x: "max-content" }}
                    />
                  </div>
                ),
              },
              {
                key: "preview",
                label: "数据预览",
                children: (
                  <div className="detail-tab-content">
                    {previewColumns.length ? (
                      <Table
                        rowKey={(_, index) => String(index)}
                        loading={previewQuery.isFetching}
                        columns={previewColumns}
                        dataSource={previewQuery.data?.data || []}
                        pagination={false}
                        scroll={{ x: "max-content" }}
                      />
                    ) : (
                      <Empty description="暂无预览数据" />
                    )}
                  </div>
                ),
              },
            ]}
          />
        ) : (
          <div className="detail-panel">
            <div className="detail-tab-content">
              <Empty description="未找到表结构详情" />
            </div>
          </div>
        )}
      </Spin>
    </Space>
  );
}

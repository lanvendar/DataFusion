import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
  TableOutlined,
} from "@ant-design/icons";
import { Button, Popconfirm, Space } from "antd";
import type { ColumnsType } from "antd/es/table";
import { type DatasourceItem, PageActionEnum } from "../../dto";

interface UseColumnsProps {
  onAction: (action: PageActionEnum, record?: DatasourceItem) => void;
}

export function useColumns({ onAction }: UseColumnsProps): ColumnsType<DatasourceItem> {
  return [
    { title: "数据链接名称", dataIndex: "name", key: "name", width: 180, ellipsis: true },
    { title: "数据库名称", dataIndex: "databaseName", key: "databaseName", width: 140 },
    { title: "数据库类型", dataIndex: "databaseType", key: "databaseType", width: 130 },
    { title: "表空间名称", dataIndex: "schemaName", key: "schemaName", width: 140 },
    { title: "地址", dataIndex: "host", key: "host", width: 180, ellipsis: true },
    { title: "端口", dataIndex: "port", key: "port", width: 100 },
    { title: "已同步表数", dataIndex: "syncCount", key: "syncCount", width: 120 },
    {
      title: "操作",
      key: "action",
      width: 300,
      fixed: "right",
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<TableOutlined />} onClick={() => onAction(PageActionEnum.TABLE_REGISTER, record)}>
            表登记
          </Button>
          <Button type="link" icon={<ReloadOutlined />} onClick={() => onAction(PageActionEnum.REFRESH, record)}>
            刷新
          </Button>
          <Button type="link" icon={<CopyOutlined />} onClick={() => onAction(PageActionEnum.COPY_ADD, record)}>
            复制
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => onAction(PageActionEnum.EDIT, record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该数据源吗？" onConfirm={() => onAction(PageActionEnum.DELETE, record)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];
}

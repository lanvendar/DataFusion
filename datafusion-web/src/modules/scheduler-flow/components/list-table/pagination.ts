import type { TablePaginationConfig } from "antd";
import { DEFAULT_PAGE_SIZE } from "../../constants";
import type { FlowPageRes } from "../../dto";

export function getPagination(page?: FlowPageRes): TablePaginationConfig {
  return {
    current: page?.current || 1,
    pageSize: page?.size || DEFAULT_PAGE_SIZE,
    total: page?.total || 0,
    showSizeChanger: true,
    showTotal: (total) => `共 ${total} 条`,
  };
}

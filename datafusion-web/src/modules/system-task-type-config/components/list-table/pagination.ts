import type { TablePaginationConfig } from "antd";
import type { TaskTypeConfigPageRes } from "../../dto";

export function getPagination(page?: TaskTypeConfigPageRes): TablePaginationConfig {
  return {
    current: page?.current || 1,
    pageSize: page?.size || 10,
    total: page?.total || 0,
    showSizeChanger: true,
  };
}

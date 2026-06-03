import type { TaskPageRes } from "../../dto";
import { DEFAULT_PAGE_SIZE } from "../../constants";

export function getPagination(data?: TaskPageRes) {
  return {
    current: data?.current || 1,
    pageSize: data?.size || DEFAULT_PAGE_SIZE,
    total: data?.total || 0,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  };
}

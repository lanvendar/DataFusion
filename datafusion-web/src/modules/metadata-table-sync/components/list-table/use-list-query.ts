import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { tableSyncApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  METADATA_TABLE_SYNC_QUERY_KEY,
  defaultFilter,
  demoTableSyncRows,
} from "../../constants";
import type { TableSyncListOption, TableSyncListReq, TableSyncListRes } from "../../dto";

function sameFilter(left: TableSyncListOption, right: TableSyncListOption) {
  return (
    left.sourceDataSourceName === right.sourceDataSourceName &&
    left.sourceDatabaseName === right.sourceDatabaseName &&
    left.targetDataSourceName === right.targetDataSourceName &&
    left.targetDatabaseName === right.targetDatabaseName &&
    left.operateType === right.operateType
  );
}

export function useTableSyncListQuery() {
  const [filter, setFilter] = useState<TableSyncListOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<TableSyncListOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<TableSyncListReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        sourceDataSourceName: appliedFilter.sourceDataSourceName || undefined,
        sourceDatabaseName: appliedFilter.sourceDatabaseName || undefined,
        targetDataSourceName: appliedFilter.targetDataSourceName || undefined,
        targetDatabaseName: appliedFilter.targetDatabaseName || undefined,
        operateType: appliedFilter.operateType,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [METADATA_TABLE_SYNC_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await tableSyncApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo table sync rows", error);
        return {
          dataList: demoTableSyncRows,
          current,
          size: pageSize,
          total: demoTableSyncRows.length,
        } satisfies TableSyncListRes;
      }
    },
  });

  const search = () => {
    setCurrent(1);
    setAppliedFilter({ ...filter });
    if (current === 1 && sameFilter(filter, appliedFilter)) {
      void query.refetch();
    }
  };

  const reset = () => {
    const nextFilter = { ...defaultFilter };
    setFilter(nextFilter);
    setCurrent(1);
    setAppliedFilter(nextFilter);
    if (current === 1 && sameFilter(appliedFilter, defaultFilter)) {
      void query.refetch();
    }
  };

  return {
    filter,
    setFilter,
    current,
    setCurrent,
    pageSize,
    setPageSize,
    query,
    search,
    reset,
  };
}

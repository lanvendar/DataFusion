import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { tableStructureApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  METADATA_TABLE_STRUCTURE_QUERY_KEY,
  defaultFilter,
  demoTableStructureRows,
} from "../../constants";
import type {
  TableStructureListOption,
  TableStructureListReq,
  TableStructureListRes,
} from "../../dto";

function sameFilter(left: TableStructureListOption, right: TableStructureListOption) {
  return (
    left.databaseConnectName === right.databaseConnectName &&
    left.databaseName === right.databaseName &&
    left.databaseType === right.databaseType &&
    left.schemaName === right.schemaName &&
    left.tableName === right.tableName &&
    left.tableDesc === right.tableDesc &&
    left.isModify === right.isModify &&
    left.isView === right.isView &&
    left.isEqual === right.isEqual
  );
}

export function useTableStructureListQuery() {
  const [filter, setFilter] = useState<TableStructureListOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<TableStructureListOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<TableStructureListReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        databaseConnectName: appliedFilter.databaseConnectName || undefined,
        databaseName: appliedFilter.databaseName || undefined,
        databaseType: appliedFilter.databaseType || undefined,
        schemaName: appliedFilter.schemaName || undefined,
        tableName: appliedFilter.tableName || undefined,
        tableDesc: appliedFilter.tableDesc || undefined,
        isModify: appliedFilter.isModify,
        isView: appliedFilter.isView,
        isEqual: appliedFilter.isEqual,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [METADATA_TABLE_STRUCTURE_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await tableStructureApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo table structure rows", error);
        return {
          dataList: demoTableStructureRows,
          current,
          size: pageSize,
          total: demoTableStructureRows.length,
        } satisfies TableStructureListRes;
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

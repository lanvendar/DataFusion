import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { datasourceApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  METADATA_DATASOURCE_QUERY_KEY,
  defaultFilter,
  demoDatasourceRows,
} from "../../constants";
import type {
  DatasourcePageOption,
  DatasourcePageReq,
  DatasourcePageRes,
} from "../../dto";

function sameFilter(left: DatasourcePageOption, right: DatasourcePageOption) {
  return (
    left.name === right.name &&
    left.databaseType === right.databaseType &&
    left.databaseName === right.databaseName &&
    left.schemaName === right.schemaName
  );
}

export function useDatasourceListQuery() {
  const [filter, setFilter] = useState<DatasourcePageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<DatasourcePageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<DatasourcePageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        name: appliedFilter.name || undefined,
        databaseType: appliedFilter.databaseType || undefined,
        databaseName: appliedFilter.databaseName || undefined,
        schemaName: appliedFilter.schemaName || undefined,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [METADATA_DATASOURCE_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await datasourceApi.page(params);
      } catch (error) {
        if (!env.DEV) {
          throw error;
        }
        console.warn("Using demo datasource rows", error);
        return {
          dataList: demoDatasourceRows,
          current,
          size: pageSize,
          total: demoDatasourceRows.length,
        } satisfies DatasourcePageRes;
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

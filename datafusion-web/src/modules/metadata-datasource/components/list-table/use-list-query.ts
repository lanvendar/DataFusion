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

export function useDatasourceListQuery() {
  const [filter, setFilter] = useState<DatasourcePageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<DatasourcePageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        name: filter.name || undefined,
        databaseType: filter.databaseType || undefined,
        databaseName: filter.databaseName || undefined,
        schemaName: filter.schemaName || undefined,
      },
    }),
    [current, filter, pageSize],
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
    void query.refetch();
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
  };
}

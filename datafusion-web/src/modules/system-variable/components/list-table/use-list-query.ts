import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { variableApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SYSTEM_VARIABLE_QUERY_KEY,
  defaultFilter,
  demoVariableRows,
} from "../../constants";
import type { VariablePageOption, VariablePageReq, VariablePageRes } from "../../dto";

function sameFilter(left: VariablePageOption, right: VariablePageOption) {
  return (
    left.code === right.code &&
    left.name === right.name &&
    left.type === right.type &&
    left.valueType === right.valueType
  );
}

export function useVariableListQuery() {
  const [filter, setFilter] = useState<VariablePageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<VariablePageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<VariablePageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        code: appliedFilter.code || undefined,
        name: appliedFilter.name || undefined,
        type: appliedFilter.type || undefined,
        valueType: appliedFilter.valueType || undefined,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SYSTEM_VARIABLE_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await variableApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo variable rows", error);
        return {
          dataList: demoVariableRows,
          current,
          size: pageSize,
          total: demoVariableRows.length,
        } satisfies VariablePageRes;
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

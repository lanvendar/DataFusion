import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { triggerApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SCHEDULER_TRIGGER_QUERY_KEY,
  defaultFilter,
  demoTriggerRows,
} from "../../constants";
import type { TriggerPageOption, TriggerPageReq, TriggerPageRes } from "../../dto";

function sameFilter(left: TriggerPageOption, right: TriggerPageOption) {
  return left.name === right.name && left.type === right.type && left.policy === right.policy;
}

export function useTriggerListQuery() {
  const [filter, setFilter] = useState<TriggerPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<TriggerPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<TriggerPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        name: appliedFilter.name || undefined,
        type: appliedFilter.type || undefined,
        policy: appliedFilter.policy || undefined,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SCHEDULER_TRIGGER_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await triggerApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo trigger rows", error);
        return {
          dataList: demoTriggerRows,
          current,
          size: pageSize,
          total: demoTriggerRows.length,
        } satisfies TriggerPageRes;
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

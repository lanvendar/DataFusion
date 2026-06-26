import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { taskApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SCHEDULER_TASK_QUERY_KEY,
  defaultFilter,
  demoTaskRows,
} from "../../constants";
import type { TaskPageOption, TaskPageReq, TaskPageRes } from "../../dto";

function sameFilter(left: TaskPageOption, right: TaskPageOption) {
  return (
    left.taskName === right.taskName &&
    left.taskCode === right.taskCode &&
    left.taskType === right.taskType &&
    left.isBound === right.isBound &&
    left.enabled === right.enabled &&
    left.syncFlag === right.syncFlag
  );
}

export function useTaskListQuery() {
  const [filter, setFilter] = useState<TaskPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<TaskPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<TaskPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        taskName: appliedFilter.taskName || undefined,
        taskCode: appliedFilter.taskCode || undefined,
        taskType: appliedFilter.taskType || undefined,
        isBound: appliedFilter.isBound,
        enabled: appliedFilter.enabled,
        syncFlag: appliedFilter.syncFlag,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SCHEDULER_TASK_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await taskApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo task rows", error);
        return {
          dataList: demoTaskRows,
          current,
          size: pageSize,
          total: demoTaskRows.length,
        } satisfies TaskPageRes;
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

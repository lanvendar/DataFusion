import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { taskTypeConfigApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY,
  defaultFilter,
  demoTaskTypeConfigRows,
} from "../../constants";
import type {
  TaskTypeConfigPageOption,
  TaskTypeConfigPageReq,
  TaskTypeConfigPageRes,
} from "../../dto";

function sameFilter(left: TaskTypeConfigPageOption, right: TaskTypeConfigPageOption) {
  return (
    left.taskType === right.taskType &&
    left.defaultPluginId === right.defaultPluginId &&
    left.pluginType === right.pluginType
  );
}

export function useTaskTypeConfigListQuery() {
  const [filter, setFilter] = useState<TaskTypeConfigPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<TaskTypeConfigPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<TaskTypeConfigPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        taskType: appliedFilter.taskType || undefined,
        defaultPluginId: appliedFilter.defaultPluginId || undefined,
        pluginType: appliedFilter.pluginType || undefined,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await taskTypeConfigApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo task type config rows", error);
        return {
          dataList: demoTaskTypeConfigRows,
          current,
          size: pageSize,
          total: demoTaskTypeConfigRows.length,
        } satisfies TaskTypeConfigPageRes;
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

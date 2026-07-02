import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { taskTypeConfigApi } from "@/modules/system-task-type-config/api";
import {
  demoTaskTypeConfigRows,
  SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY,
} from "@/modules/system-task-type-config/constants";
import type { TaskTypeConfigItem } from "@/modules/system-task-type-config/dto";
import type { TaskTypeFilterOption, TaskTypeFormOption } from "./dto";

export function useTaskTypeOptions() {
  const query = useQuery({
    queryKey: [SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY, "scheduler-task-options"],
    queryFn: async () => {
      try {
        return await taskTypeConfigApi.list({});
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo task type config rows", error);
        return demoTaskTypeConfigRows;
      }
    },
  });

  const taskTypeConfigs = useMemo<TaskTypeConfigItem[]>(
    () => query.data || [],
    [query.data],
  );

  const formOptions = useMemo<TaskTypeFormOption[]>(
    () =>
      taskTypeConfigs.map((item) => ({
        label: item.taskType,
        value: item.id,
        taskType: item.taskType,
      })),
    [taskTypeConfigs],
  );

  const filterOptions = useMemo<TaskTypeFilterOption[]>(
    () =>
      taskTypeConfigs.map((item) => ({
        label: item.taskType,
        value: item.taskType,
      })),
    [taskTypeConfigs],
  );

  return {
    query,
    taskTypeConfigs,
    formOptions,
    filterOptions,
  };
}

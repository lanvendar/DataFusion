import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { workerRegistryApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SCHEDULER_WORKER_QUERY_KEY,
  defaultFilter,
  demoWorkerRows,
} from "../../constants";
import type {
  WorkerRegistryPageOption,
  WorkerRegistryPageReq,
  WorkerRegistryPageRes,
} from "../../dto";

function sameFilter(left: WorkerRegistryPageOption, right: WorkerRegistryPageOption) {
  return (
    left.workerCode === right.workerCode &&
    left.hostName === right.hostName &&
    left.host === right.host &&
    left.status === right.status &&
    left.zone === right.zone &&
    left.isActive === right.isActive
  );
}

export function useWorkerRegistryListQuery() {
  const [filter, setFilter] = useState<WorkerRegistryPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<WorkerRegistryPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<WorkerRegistryPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        workerCode: appliedFilter.workerCode || undefined,
        hostName: appliedFilter.hostName || undefined,
        host: appliedFilter.host || undefined,
        status: appliedFilter.status,
        zone: appliedFilter.zone || undefined,
        isActive: appliedFilter.isActive,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SCHEDULER_WORKER_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await workerRegistryApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo worker registry rows", error);
        return {
          dataList: demoWorkerRows,
          current,
          size: pageSize,
          total: demoWorkerRows.length,
        } satisfies WorkerRegistryPageRes;
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

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { eventApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SCHEDULER_EVENT_QUERY_KEY,
  defaultFilter,
  demoEventRows,
} from "../../constants";
import type { EventPageOption, EventPageReq, EventPageRes } from "../../dto";

function sameFilter(left: EventPageOption, right: EventPageOption) {
  return (
    left.eventName === right.eventName &&
    left.eventType === right.eventType &&
    left.flowId === right.flowId &&
    left.taskId === right.taskId
  );
}

export function useEventListQuery() {
  const [filter, setFilter] = useState<EventPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<EventPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<EventPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        eventName: appliedFilter.eventName || undefined,
        eventType: appliedFilter.eventType || undefined,
        flowId: appliedFilter.flowId || undefined,
        taskId: appliedFilter.taskId || undefined,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SCHEDULER_EVENT_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await eventApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo event rows", error);
        return {
          dataList: demoEventRows,
          current,
          size: pageSize,
          total: demoEventRows.length,
        } satisfies EventPageRes;
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

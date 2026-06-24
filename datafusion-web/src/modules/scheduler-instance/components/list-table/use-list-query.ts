import type { Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { flowInstanceApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SCHEDULER_INSTANCE_FLOW_QUERY_KEY,
  defaultFilter,
} from "../../constants";
import type {
  SchedulerInstanceQueryOption,
  SchedulerInstanceViewType,
} from "../../dto";

export type DateRange = [Dayjs, Dayjs] | null;

export interface SchedulerInstanceFilterState {
  flowKeyword?: string;
  taskKeyword?: string;
  status?: string;
  scheduleRange?: DateRange;
  startRange?: DateRange;
  finishRange?: DateRange;
}

export const initialFilter: SchedulerInstanceFilterState = {
  flowKeyword: undefined,
  taskKeyword: undefined,
  status: undefined,
  scheduleRange: null,
  startRange: null,
  finishRange: null,
};

const FLOW_KEYWORD_PARAM = "flowKeyword";

function trimValue(value?: string) {
  const next = value?.trim();
  return next || undefined;
}

function createInitialFilter(flowKeyword?: string): SchedulerInstanceFilterState {
  return {
    ...initialFilter,
    flowKeyword: trimValue(flowKeyword),
  };
}

function toTimeRange(range?: DateRange) {
  if (!range) return {};
  return {
    start: range[0].valueOf(),
    end: range[1].valueOf(),
  };
}

function toQueryOption(
  filter: SchedulerInstanceFilterState,
  viewType: SchedulerInstanceViewType,
): SchedulerInstanceQueryOption {
  const schedule = toTimeRange(filter.scheduleRange);
  const start = toTimeRange(filter.startRange);
  const finish = toTimeRange(filter.finishRange);

  return {
    ...defaultFilter,
    viewType,
    flowKeyword: trimValue(filter.flowKeyword),
    taskKeyword: trimValue(filter.taskKeyword),
    status: filter.status,
    scheduleStartTime: schedule.start,
    scheduleEndTime: schedule.end,
    startTime: start.start,
    endTime: start.end,
    finishStartTime: finish.start,
    finishEndTime: finish.end,
  };
}

function rangeValue(range?: DateRange) {
  if (!range) return "";
  return `${range[0].valueOf()}-${range[1].valueOf()}`;
}

function sameFilter(left: SchedulerInstanceFilterState, right: SchedulerInstanceFilterState) {
  return (
    left.flowKeyword === right.flowKeyword
    && left.taskKeyword === right.taskKeyword
    && left.status === right.status
    && rangeValue(left.scheduleRange) === rangeValue(right.scheduleRange)
    && rangeValue(left.startRange) === rangeValue(right.startRange)
    && rangeValue(left.finishRange) === rangeValue(right.finishRange)
  );
}

export function useSchedulerInstanceListQuery() {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlFlowKeyword = searchParams.get(FLOW_KEYWORD_PARAM) || undefined;
  const urlFilter = useMemo(() => createInitialFilter(urlFlowKeyword), [urlFlowKeyword]);
  const [filter, setFilter] = useState<SchedulerInstanceFilterState>(urlFilter);
  const [appliedFilter, setAppliedFilter] = useState<SchedulerInstanceFilterState>(urlFilter);
  const [viewType, setViewTypeState] = useState<SchedulerInstanceViewType>("REALTIME");
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => {
    setFilter(urlFilter);
    setAppliedFilter(urlFilter);
    setCurrent(1);
  }, [urlFilter]);

  const option = useMemo(
    () => toQueryOption(appliedFilter, viewType),
    [appliedFilter, viewType],
  );
  const queryKey = [SCHEDULER_INSTANCE_FLOW_QUERY_KEY, option, current, pageSize] as const;

  const query = useQuery({
    queryKey,
    queryFn: () => flowInstanceApi.page({ current, size: pageSize, option }),
  });

  const search = () => {
    setCurrent(1);
    setAppliedFilter({ ...filter });
    setSearchParams((params) => {
      const next = new URLSearchParams(params);
      const flowKeyword = trimValue(filter.flowKeyword);
      if (flowKeyword) {
        next.set(FLOW_KEYWORD_PARAM, flowKeyword);
      } else {
        next.delete(FLOW_KEYWORD_PARAM);
      }
      return next;
    }, { replace: true });
    if (current === 1 && sameFilter(filter, appliedFilter)) {
      void query.refetch();
    }
  };

  const reset = () => {
    const nextFilter = { ...initialFilter };
    setFilter(nextFilter);
    setAppliedFilter(nextFilter);
    setViewTypeState("REALTIME");
    setSearchParams((params) => {
      const next = new URLSearchParams(params);
      next.delete(FLOW_KEYWORD_PARAM);
      return next;
    }, { replace: true });
    setCurrent(1);
    if (current === 1 && viewType === "REALTIME" && sameFilter(appliedFilter, initialFilter)) {
      void query.refetch();
    }
  };

  const setViewType = (nextViewType: SchedulerInstanceViewType) => {
    setViewTypeState(nextViewType);
    setCurrent(1);
  };

  return {
    filter,
    setFilter,
    viewType,
    setViewType,
    current,
    setCurrent,
    pageSize,
    setPageSize,
    queryKey,
    query,
    search,
    reset,
  };
}

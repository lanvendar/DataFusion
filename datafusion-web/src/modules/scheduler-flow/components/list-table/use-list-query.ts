import { useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { flowApi } from "../../api";
import { DEFAULT_PAGE_SIZE, defaultFilter } from "../../constants";
import type { FlowPageOption, FlowPageReq } from "../../dto";
import { SCHEDULER_FLOW_QUERY_KEY } from "../../constants";

function sameFilter(left: FlowPageOption, right: FlowPageOption) {
  return (
    left.flowName === right.flowName &&
    left.flowType === right.flowType &&
    left.enabled === right.enabled &&
    left.publishState === right.publishState
  );
}

export function useFlowListQuery() {
  const [filter, setFilter] = useState<FlowPageOption>(defaultFilter);
  const [queryFilter, setQueryFilter] = useState<FlowPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const query = useQuery({
    queryKey: [SCHEDULER_FLOW_QUERY_KEY, queryFilter, current, pageSize],
    queryFn: () => {
      const params: FlowPageReq = {
        current,
        size: pageSize,
        option: {
          flowName: queryFilter.flowName,
          flowType: queryFilter.flowType,
          enabled: queryFilter.enabled,
          publishState: queryFilter.publishState,
        },
      };

      return flowApi.page(params);
    },
  });

  const search = useCallback(() => {
    setCurrent(1);
    setQueryFilter(filter);
    if (sameFilter(filter, queryFilter) && current === 1) void query.refetch();
  }, [current, filter, query, queryFilter]);

  const reset = useCallback(() => {
    setFilter(defaultFilter);
    setQueryFilter(defaultFilter);
    setCurrent(1);
  }, []);

  return {
    filter,
    setFilter,
    query,
    search,
    reset,
    setCurrent,
    setPageSize,
  };
}

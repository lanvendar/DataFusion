import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { env } from "@/env";
import { pluginConfigApi } from "../../api";
import {
  DEFAULT_PAGE_SIZE,
  SYSTEM_PLUGIN_CONFIG_QUERY_KEY,
  defaultFilter,
  demoPluginConfigRows,
} from "../../constants";
import type {
  PluginConfigPageOption,
  PluginConfigPageReq,
  PluginConfigPageRes,
} from "../../dto";

function sameFilter(left: PluginConfigPageOption, right: PluginConfigPageOption) {
  return (
    left.pluginName === right.pluginName &&
    left.pluginType === right.pluginType &&
    left.runMode === right.runMode &&
    left.isTemplate === right.isTemplate
  );
}

export function usePluginConfigListQuery() {
  const [filter, setFilter] = useState<PluginConfigPageOption>(defaultFilter);
  const [appliedFilter, setAppliedFilter] = useState<PluginConfigPageOption>(defaultFilter);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params = useMemo<PluginConfigPageReq>(
    () => ({
      current,
      size: pageSize,
      option: {
        pluginName: appliedFilter.pluginName || undefined,
        pluginType: appliedFilter.pluginType || undefined,
        runMode: appliedFilter.runMode || undefined,
        isTemplate: appliedFilter.isTemplate,
      },
    }),
    [appliedFilter, current, pageSize],
  );

  const query = useQuery({
    queryKey: [SYSTEM_PLUGIN_CONFIG_QUERY_KEY, params],
    queryFn: async () => {
      try {
        return await pluginConfigApi.page(params);
      } catch (error) {
        if (!env.DEV) throw error;
        console.warn("Using demo plugin config rows", error);
        return {
          dataList: demoPluginConfigRows,
          current,
          size: pageSize,
          total: demoPluginConfigRows.length,
        } satisfies PluginConfigPageRes;
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

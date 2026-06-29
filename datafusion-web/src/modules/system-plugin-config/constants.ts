import type { PluginConfigItem, PluginConfigPageOption } from "./dto";

export const SYSTEM_PLUGIN_CONFIG_QUERY_KEY = "system-plugin-config";

export const DEFAULT_PAGE_SIZE = 10;

export const PLUGIN_COPY_BASE_MAX_LENGTH = 235;

export const defaultFilter: PluginConfigPageOption = {
  pluginName: "",
  pluginType: "",
  runMode: "",
  isTemplate: undefined,
};

export const templateOptions = [
  { label: "模板数据", value: true },
  { label: "普通配置", value: false },
];

export const demoPluginConfigRows: PluginConfigItem[] = [
  {
    id: "11111111-1111-4111-8111-111111111111",
    pluginName: "flink_yarn_template",
    pluginType: "FLINK",
    runMode: "YARN",
    description: "Flink on YARN 初始化模板",
    pluginParam: {
      queue: "default",
      parallelism: 2,
    },
    isTemplate: true,
    isDel: 0,
    updater: "system",
    updateTime: "2026-06-04 10:00:00",
  },
];

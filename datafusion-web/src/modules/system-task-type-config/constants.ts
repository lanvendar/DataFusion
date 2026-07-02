import type { TaskTypeConfigItem, TaskTypeConfigPageOption } from "./dto";

export const SYSTEM_TASK_TYPE_CONFIG_QUERY_KEY = "system-task-type-config";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: TaskTypeConfigPageOption = {
  taskType: "",
  defaultPluginId: undefined,
  pluginType: "",
};

export const demoTaskTypeConfigRows: TaskTypeConfigItem[] = [
  {
    id: "8d9c307c-3f2c-3e1c-95fb-9c593878691f",
    taskType: "DATAX",
    defaultPluginId: "11111111-1111-4111-8111-111111111111",
    pluginType: "DATAX",
    updater: "system",
    updateTime: "2026-06-05 10:00:00",
  },
  {
    id: "db974238-714c-38de-a34a-7ce1d083a14f",
    taskType: "API",
    defaultPluginId: "22222222-2222-4222-8222-222222222222",
    pluginType: "API",
    updater: "system",
    updateTime: "2026-06-05 10:00:00",
  },
];

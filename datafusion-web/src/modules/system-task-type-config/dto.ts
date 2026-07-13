export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface TaskTypeConfigItem {
  id: string;
  taskType: string;
  defaultPluginId: string;
  defaultPluginName?: string;
  pluginType?: string;
  tenantId?: string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TaskTypeConfigPageOption {
  taskType?: string;
  defaultPluginId?: string;
  pluginType?: string;
}

export interface TaskTypeConfigPageReq {
  size?: number;
  current?: number;
  option?: TaskTypeConfigPageOption;
}

export interface TaskTypeConfigPageRes {
  dataList?: TaskTypeConfigItem[];
  records?: TaskTypeConfigItem[];
  list?: TaskTypeConfigItem[];
  size: number;
  current: number;
  total: number;
}

export interface TaskTypeConfigSaveReq {
  id?: string;
  taskType?: string;
  defaultPluginId: string;
  pluginType?: string;
}

export type TaskTypeConfigFormMode = "add" | "edit";

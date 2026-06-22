export enum PageActionEnum {
  ADD = "ADD",
  COPY = "COPY",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface PluginConfigItem {
  id: string;
  pluginName: string;
  pluginType: string;
  runMode: string;
  description?: string;
  pluginParam?: unknown;
  isTemplate?: boolean;
  isDel?: number;
  tenantId?: string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PluginConfigPageOption {
  pluginName?: string;
  pluginType?: string;
  runMode?: string;
  isTemplate?: boolean;
}

export interface PluginConfigPageReq {
  size?: number;
  current?: number;
  option?: PluginConfigPageOption;
}

export interface PluginConfigPageRes {
  dataList?: PluginConfigItem[];
  records?: PluginConfigItem[];
  list?: PluginConfigItem[];
  size: number;
  current: number;
  total: number;
}

export interface PluginConfigSaveReq {
  id?: string;
  pluginName?: string;
  pluginType?: string;
  runMode?: string;
  description?: string;
  pluginParam?: unknown;
}

export interface PluginConfigFormValues {
  pluginName?: string;
  pluginType?: string;
  runMode?: string;
  description?: string;
  pluginParamText?: string;
}

export type PluginConfigFormMode = "add" | "edit";

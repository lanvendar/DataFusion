export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
  VIEW = "VIEW",
}

export interface TaskItem {
  id: string;
  taskName: string;
  taskCode: string;
  description?: string;
  taskTypeId: string;
  taskType: string;
  taskParam?: string;
  definition?: string;
  isBound?: boolean;
  enabled?: boolean;
  syncFlag?: boolean;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TaskPageOption {
  taskName?: string;
  taskCode?: string;
  taskType?: string;
  isBound?: boolean;
  enabled?: boolean;
  syncFlag?: boolean;
}

export interface TaskPageReq {
  size?: number;
  current?: number;
  option?: TaskPageOption;
}

export interface TaskPageRes {
  dataList?: TaskItem[];
  records?: TaskItem[];
  list?: TaskItem[];
  size: number;
  current: number;
  total: number;
}

export interface TaskSaveReq {
  id?: string;
  taskName: string;
  taskCode: string;
  description?: string;
  taskTypeId: string;
  taskType: string;
  taskParam?: string;
  definition?: string;
}

export type TaskFormMode = "add" | "edit";

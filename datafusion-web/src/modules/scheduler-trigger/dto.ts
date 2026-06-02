export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface TriggerItem {
  id: string;
  name: string;
  type: string;
  policy: string;
  cron?: string;
  interval?: number;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TriggerPageOption {
  name?: string;
  type?: string;
  policy?: string;
}

export interface TriggerPageReq {
  size?: number;
  current?: number;
  option?: TriggerPageOption;
}

export interface TriggerPageRes {
  dataList?: TriggerItem[];
  records?: TriggerItem[];
  list?: TriggerItem[];
  size: number;
  current: number;
  total: number;
}

export interface TriggerSaveReq {
  id?: string;
  name: string;
  type: string;
  policy: string;
  cron?: string;
  interval?: number;
}

export type TriggerFormMode = "add" | "edit";

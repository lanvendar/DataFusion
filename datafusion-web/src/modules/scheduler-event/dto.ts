export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface EventItem {
  id: string;
  eventName: string;
  eventType: string;
  flowId?: string;
  taskId?: string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface EventPageOption {
  eventName?: string;
  eventType?: string;
  flowId?: string;
  taskId?: string;
}

export interface EventPageReq {
  size?: number;
  current?: number;
  option?: EventPageOption;
}

export interface EventPageRes {
  dataList?: EventItem[];
  records?: EventItem[];
  list?: EventItem[];
  size: number;
  current: number;
  total: number;
}

export interface EventSaveReq {
  id?: string;
  eventName: string;
  eventType: string;
  flowId?: string;
  taskId?: string;
}

export type EventFormMode = "add" | "edit";

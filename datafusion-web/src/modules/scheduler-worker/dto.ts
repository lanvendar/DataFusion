export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface WorkerRegistryItem {
  id: string;
  workerCode: string;
  hostName: string;
  host: string;
  port: number;
  status: number;
  zone?: string;
  plugins?: string;
  registerTime?: string;
  lastHeartbeatTime?: string;
  isActive: number;
  remark?: string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface WorkerRegistryPageOption {
  workerCode?: string;
  hostName?: string;
  host?: string;
  status?: number;
  zone?: string;
  isActive?: number;
}

export interface WorkerRegistryPageReq {
  size?: number;
  current?: number;
  option?: WorkerRegistryPageOption;
}

export interface WorkerRegistryPageRes {
  dataList?: WorkerRegistryItem[];
  records?: WorkerRegistryItem[];
  list?: WorkerRegistryItem[];
  size: number;
  current: number;
  total: number;
}

export interface WorkerRegistrySaveReq {
  id?: string;
  workerCode?: string;
  hostName?: string;
  host?: string;
  port?: number;
  zone?: string;
  plugins?: string;
  isActive?: number;
  remark?: string;
}

export type WorkerRegistryFormMode = "add" | "edit";

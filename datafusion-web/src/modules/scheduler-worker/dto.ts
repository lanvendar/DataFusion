export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  ACTIVE = "ACTIVE",
  DELETE = "DELETE",
}

export type WorkerActiveFlag = 0 | 1;

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
  workerLogDir?: string;
  isActive: WorkerActiveFlag;
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
  isActive?: WorkerActiveFlag;
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
  workerCode?: string;
  hostName?: string;
  host?: string;
  port?: number;
  zone?: string;
  plugins?: string;
  isActive?: WorkerActiveFlag;
  remark?: string;
}

export interface WorkerRegistryUpdateReq {
  id: string;
  zone?: string;
  remark?: string;
}

export interface WorkerRegistryActiveReq {
  id: string;
  isActive: WorkerActiveFlag;
}

export interface WorkerRegistryFormValues extends WorkerRegistrySaveReq {
  status?: number;
}

export type WorkerRegistryFormMode = "add" | "edit";

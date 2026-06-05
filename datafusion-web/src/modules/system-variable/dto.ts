export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  DELETE = "DELETE",
}

export interface VariableItem {
  id: string;
  code: string;
  name: string;
  type: string;
  valueType: string;
  value?: string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface VariablePageOption {
  code?: string;
  name?: string;
  type?: string;
  valueType?: string;
}

export interface VariablePageReq {
  size?: number;
  current?: number;
  option?: VariablePageOption;
}

export interface VariablePageRes {
  dataList?: VariableItem[];
  records?: VariableItem[];
  list?: VariableItem[];
  size: number;
  current: number;
  total: number;
}

export interface VariableSaveReq {
  id?: string;
  code?: string;
  name?: string;
  valueType?: string;
  value?: string;
}

export type VariableFormMode = "add" | "edit";

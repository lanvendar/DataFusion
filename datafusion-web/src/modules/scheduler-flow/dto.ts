import type { Edge, Node } from "@xyflow/react";

export enum PageActionEnum {
  ADD = "ADD",
  EDIT = "EDIT",
  VIEW = "VIEW",
  DELETE = "DELETE",
  DAG_EDIT = "DAG_EDIT",
  PUBLISH = "PUBLISH",
  UNPUBLISH = "UNPUBLISH",
  ENABLE = "ENABLE",
  DISABLE = "DISABLE",
  VIEW_INSTANCE = "VIEW_INSTANCE",
}

export interface FlowItem {
  id: string;
  flowName: string;
  flowCode: string;
  groupId?: string;
  description?: string;
  flowType: string;
  triggerId?: string;
  triggerName?: string;
  flowParam?: Record<string, unknown> | string;
  startTime?: number | string;
  endTime?: number | string;
  enabled?: boolean;
  depEventIds?: string[] | string;
  eventId?: string;
  publishState?: boolean;
  publishVersion?: number | string;
  creator?: string;
  updater?: string;
  createTime?: string;
  updateTime?: string;
}

export interface FlowPageOption {
  flowName?: string;
  flowType?: string;
  enabled?: boolean;
  publishState?: boolean;
}

export interface FlowPageReq {
  size?: number;
  current?: number;
  option?: FlowPageOption;
}

export interface FlowPageRes {
  dataList?: FlowItem[];
  records?: FlowItem[];
  list?: FlowItem[];
  size: number;
  current: number;
  total: number;
}

export interface FlowSaveReq {
  id?: string;
  flowName: string;
  flowCode: string;
  groupId?: string;
  description?: string;
  flowType: string;
  depEventIds?: string[];
  flowParam?: string;
}

export interface FlowScheduleReq {
  id: string;
  triggerId: string;
  startTime: number;
  endTime: number;
}

export interface TriggerListItem {
  id: string;
  name?: string;
  triggerName?: string;
  type?: string;
  policy?: string;
}

export interface EventListItem {
  id: string;
  eventName?: string;
  name?: string;
  eventType?: string;
  flowId?: string;
  taskId?: string;
}

export interface TaskListItem {
  id: string;
  taskName: string;
  taskCode: string;
  taskType: string;
  description?: string;
  isBound?: boolean;
  flowId?: string;
  syncFlag?: boolean;
}

export interface TaskDetailItem extends TaskListItem {
  taskTypeId?: string;
  taskParam?: string;
  definition?: string;
  pluginId?: string;
  depEventIds?: string[] | string;
  eventId?: string;
  enabled?: boolean;
}

export interface TaskUpdateReq {
  id: string;
  pluginId?: string;
  depEventIds?: string;
  eventId?: string;
  clearEventId?: boolean;
  enabled?: boolean;
  taskParam?: string;
}

export interface PluginConfigListItem {
  id: string;
  pluginName?: string;
  pluginType?: string;
  runMode?: string;
}

export interface SchedulerVariableValue {
  name?: string;
  type?: string;
  value?: string;
}

export interface ParamDataValue {
  vars?: Record<string, SchedulerVariableValue>;
}

export interface FlowDagNodeView {
  position?: {
    x?: number;
    y?: number;
  };
  style?: Record<string, unknown>;
  extra?: Record<string, unknown>;
  x?: number;
  y?: number;
  type?: string;
  width?: number;
  height?: number;
  [key: string]: unknown;
}

export interface FlowDagEdgeView {
  style?: Record<string, unknown>;
  extra?: Record<string, unknown>;
  type?: string;
  animated?: boolean;
  label?: string;
  [key: string]: unknown;
}

export interface FlowDagNodeData {
  taskId?: string;
  taskName?: string;
  taskCode?: string;
  taskType?: string;
  description?: string;
  syncFlag?: boolean;
  pluginId?: string;
  depEventIds?: string[] | string;
  eventId?: string;
  enabled?: boolean;
  taskParam?: string;
  definition?: string;
  [key: string]: unknown;
}

export interface FlowDagNodeDto {
  id: string;
  data?: FlowDagNodeData;
  nodeView?: FlowDagNodeView;
}

export interface FlowDagEdgeDto {
  id?: string;
  source: string;
  target: string;
  edgeView?: FlowDagEdgeView;
}

export interface FlowDagDto {
  flowId: string;
  nodes?: FlowDagNodeDto[];
  edges?: FlowDagEdgeDto[];
}

export interface FlowDagSaveReq {
  flowId: string;
  nodes: FlowDagNodeDto[];
  edges: FlowDagEdgeDto[];
}

export type FlowFormMode = "add" | "edit";

export type FlowCanvasNode = Node<FlowDagNodeData>;

export type FlowCanvasEdge = Edge;

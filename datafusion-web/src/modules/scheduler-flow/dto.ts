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
  triggerId?: string;
  depEventIds?: string[];
  flowParam?: string;
  startTime?: number;
  endTime?: number;
}

export interface FlowPublishReq {
  id: string;
  enableSchedule?: boolean;
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
}

export interface TaskListItem {
  id: string;
  taskName: string;
  taskCode: string;
  taskType: string;
  description?: string;
  isBound?: boolean;
  flowId?: string;
}

export interface FlowDagNodeView {
  x?: number;
  y?: number;
  type?: string;
  width?: number;
  height?: number;
  [key: string]: unknown;
}

export interface FlowDagEdgeView {
  type?: string;
  animated?: boolean;
  label?: string;
  [key: string]: unknown;
}

export interface FlowDagNodeData {
  taskName?: string;
  taskCode?: string;
  taskType?: string;
  description?: string;
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

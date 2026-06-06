import type {
  FlowCanvasEdge,
  FlowCanvasNode,
  FlowDagEdgeDto,
  FlowDagNodeDto,
  ParamDataValue,
} from "./dto";

export function formatJsonText(value?: Record<string, unknown> | string | null) {
  if (!value) return "";

  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, 2);
  } catch {
    return String(value);
  }
}

export function compressJsonText(value?: string) {
  if (!value?.trim()) return undefined;
  return JSON.stringify(JSON.parse(value));
}

export function normalizeJsonText(value?: string, label = "JSON") {
  if (!value?.trim()) return undefined;
  try {
    return compressJsonText(value);
  } catch {
    throw new Error(`${label} 格式不正确`);
  }
}

export function normalizeStringArray(value?: string[] | string) {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (!value) return [];
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function normalizeTimestamp(value?: number | string | null) {
  if (value === undefined || value === null || value === "") return undefined;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : undefined;
}

export function parseParamData(value?: Record<string, unknown> | string | null): ParamDataValue {
  if (!value) return {};

  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    if (parsed && typeof parsed === "object") return parsed as ParamDataValue;
  } catch {
    return {};
  }

  return {};
}

function readNodePosition(node: FlowDagNodeDto, index: number) {
  const position = node.nodeView?.position;
  const legacyX = node.nodeView?.x;
  const legacyY = node.nodeView?.y;

  return {
    x: typeof position?.x === "number" ? position.x : typeof legacyX === "number" ? legacyX : 120 + (index % 4) * 220,
    y: typeof position?.y === "number" ? position.y : typeof legacyY === "number" ? legacyY : 120 + Math.floor(index / 4) * 140,
  };
}

export function dagNodeToCanvas(node: FlowDagNodeDto, index: number): FlowCanvasNode {
  const position = readNodePosition(node, index);
  const type = typeof node.nodeView?.extra?.type === "string" ? node.nodeView.extra.type : node.nodeView?.type;

  return {
    id: node.id,
    type: type || "taskNode",
    position,
    style: node.nodeView?.style,
    data: {
      taskId: node.data?.taskId || node.id,
      taskName: node.data?.taskName || node.id,
      taskCode: node.data?.taskCode,
      taskType: node.data?.taskType,
      description: node.data?.description,
      syncFlag: node.data?.syncFlag,
      pluginId: node.data?.pluginId,
      depEventIds: node.data?.depEventIds,
      eventId: node.data?.eventId,
      enabled: node.data?.enabled,
      taskParam: node.data?.taskParam,
      definition: node.data?.definition,
    },
  };
}

export function dagEdgeToCanvas(edge: FlowDagEdgeDto): FlowCanvasEdge {
  const type = typeof edge.edgeView?.extra?.type === "string" ? edge.edgeView.extra.type : edge.edgeView?.type;
  const animated = typeof edge.edgeView?.extra?.animated === "boolean" ? edge.edgeView.extra.animated : edge.edgeView?.animated;
  const sourceHandle = typeof edge.edgeView?.extra?.sourceHandle === "string" ? edge.edgeView.extra.sourceHandle : undefined;
  const targetHandle = typeof edge.edgeView?.extra?.targetHandle === "string" ? edge.edgeView.extra.targetHandle : undefined;

  return {
    id: edge.id || `${edge.source}-${edge.target}`,
    source: edge.source,
    target: edge.target,
    sourceHandle,
    targetHandle,
    type: type || "smoothstep",
    animated: animated ?? true,
    label: edge.edgeView?.label,
    style: edge.edgeView?.style,
  };
}

export function canvasNodeToDag(node: FlowCanvasNode): FlowDagNodeDto {
  return {
    id: node.id,
    data: {
      taskId: node.data.taskId,
      taskName: node.data.taskName,
      taskCode: node.data.taskCode,
      taskType: node.data.taskType,
      description: node.data.description,
      syncFlag: node.data.syncFlag,
      taskParam: node.data.taskParam,
      definition: node.data.definition,
    },
    nodeView: {
      position: {
        x: node.position.x,
        y: node.position.y,
      },
      style: node.style as Record<string, unknown> | undefined,
      extra: {
        type: node.type,
        width: node.measured?.width,
        height: node.measured?.height,
      },
    },
  };
}

export function canvasEdgeToDag(edge: FlowCanvasEdge): FlowDagEdgeDto {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    edgeView: {
      label: typeof edge.label === "string" ? edge.label : undefined,
      style: edge.style as Record<string, unknown> | undefined,
      extra: {
        type: edge.type,
        animated: edge.animated,
        sourceHandle: edge.sourceHandle,
        targetHandle: edge.targetHandle,
      },
    },
  };
}

export async function runSequentialActions(actions: Array<() => Promise<unknown>>) {
  for (const action of actions) {
    await action();
  }
}

import type {
  FlowCanvasEdge,
  FlowCanvasNode,
  FlowDagEdgeDto,
  FlowDagNodeDto,
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

export function dagNodeToCanvas(node: FlowDagNodeDto, index: number): FlowCanvasNode {
  const x = typeof node.nodeView?.x === "number" ? node.nodeView.x : 120 + (index % 4) * 220;
  const y = typeof node.nodeView?.y === "number" ? node.nodeView.y : 120 + Math.floor(index / 4) * 140;

  return {
    id: node.id,
    type: node.nodeView?.type || "default",
    position: { x, y },
    data: {
      taskName: node.data?.taskName || node.id,
      taskCode: node.data?.taskCode,
      taskType: node.data?.taskType,
      description: node.data?.description,
    },
  };
}

export function dagEdgeToCanvas(edge: FlowDagEdgeDto): FlowCanvasEdge {
  return {
    id: edge.id || `${edge.source}-${edge.target}`,
    source: edge.source,
    target: edge.target,
    type: edge.edgeView?.type || "smoothstep",
    animated: edge.edgeView?.animated ?? true,
    label: edge.edgeView?.label,
  };
}

export function canvasNodeToDag(node: FlowCanvasNode): FlowDagNodeDto {
  return {
    id: node.id,
    data: node.data,
    nodeView: {
      x: node.position.x,
      y: node.position.y,
      type: node.type,
      width: node.measured?.width,
      height: node.measured?.height,
    },
  };
}

export function canvasEdgeToDag(edge: FlowCanvasEdge): FlowDagEdgeDto {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    edgeView: {
      type: edge.type,
      animated: edge.animated,
      label: typeof edge.label === "string" ? edge.label : undefined,
    },
  };
}

export async function runSequentialActions(actions: Array<() => Promise<unknown>>) {
  for (const action of actions) {
    await action();
  }
}

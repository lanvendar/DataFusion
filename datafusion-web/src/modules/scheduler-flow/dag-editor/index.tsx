import {
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
} from "@ant-design/icons";
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { App, Button, Drawer, Empty, Input, List, Space, Spin, Tag } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { flowApi, schedulerFlowRelationApi } from "../api";
import type { FlowCanvasEdge, FlowCanvasNode, FlowItem, TaskListItem } from "../dto";
import {
  canvasEdgeToDag,
  canvasNodeToDag,
  dagEdgeToCanvas,
  dagNodeToCanvas,
} from "../utils";

interface DagEditorProps {
  open: boolean;
  currentRecord?: FlowItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function DagEditor({
  open,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: DagEditorProps) {
  const { message } = App.useApp();
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowCanvasNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowCanvasEdge>([]);
  const [selectedNode, setSelectedNode] = useState<FlowCanvasNode>();
  const [keyword, setKeyword] = useState("");
  const [loadingDag, setLoadingDag] = useState(false);
  const [saving, setSaving] = useState(false);

  const taskQuery = useQuery({
    queryKey: ["scheduler-flow-task-pool", currentRecord?.id],
    queryFn: () => schedulerFlowRelationApi.tasks({ isBound: false }),
    enabled: open,
  });

  const loadDag = useCallback(async () => {
    if (!currentRecord?.id) return;

    setLoadingDag(true);
    try {
      const dag = await flowApi.dagDetail(currentRecord.id);
      setNodes((dag.nodes || []).map(dagNodeToCanvas));
      setEdges((dag.edges || []).map(dagEdgeToCanvas));
      setSelectedNode(undefined);
    } finally {
      setLoadingDag(false);
    }
  }, [currentRecord?.id, setEdges, setNodes]);

  useEffect(() => {
    if (!open) return;
    void loadDag();
  }, [loadDag, open]);

  const existingNodeIds = useMemo(() => new Set(nodes.map((node) => node.id)), [nodes]);
  const taskPool = useMemo(() => {
    const list = Array.isArray(taskQuery.data) ? taskQuery.data : [];
    const lowered = keyword.trim().toLowerCase();

    return list.filter((task) => {
      if (existingNodeIds.has(task.id)) return false;
      if (!lowered) return true;
      return [task.taskName, task.taskCode, task.taskType].some((value) =>
        value?.toLowerCase().includes(lowered),
      );
    });
  }, [existingNodeIds, keyword, taskQuery.data]);

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((currentEdges) =>
        addEdge({ ...connection, type: "smoothstep", animated: true }, currentEdges),
      );
    },
    [setEdges],
  );

  const addTaskNode = (task: TaskListItem) => {
    const nextIndex = nodes.length;
    setNodes((currentNodes) => [
      ...currentNodes,
      {
        id: task.id,
        type: "default",
        position: {
          x: 120 + (nextIndex % 4) * 220,
          y: 120 + Math.floor(nextIndex / 4) * 140,
        },
        data: {
          taskName: task.taskName,
          taskCode: task.taskCode,
          taskType: task.taskType,
          description: task.description,
        },
      },
    ]);
  };

  const removeSelectedNode = () => {
    if (!selectedNode) return;
    setNodes((currentNodes) => currentNodes.filter((node) => node.id !== selectedNode.id));
    setEdges((currentEdges) =>
      currentEdges.filter((edge) => edge.source !== selectedNode.id && edge.target !== selectedNode.id),
    );
    setSelectedNode(undefined);
  };

  const saveDag = async () => {
    if (!currentRecord?.id) return;

    setSaving(true);
    try {
      await flowApi.saveDag({
        flowId: currentRecord.id,
        nodes: nodes.map(canvasNodeToDag),
        edges: edges.map(canvasEdgeToDag),
      });
      message.success("编排保存成功");
      onSubmitSuccess();
    } finally {
      setSaving(false);
    }
  };

  const handleClose = () => {
    setSelectedNode(undefined);
    onClose();
  };

  return (
    <Drawer
      title={currentRecord ? `流程编排：${currentRecord.flowName}` : "流程编排"}
      open={open}
      width="88vw"
      onClose={handleClose}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadDag}>
            重新加载
          </Button>
          <Button icon={<DeleteOutlined />} disabled={!selectedNode} onClick={removeSelectedNode}>
            移除节点
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveDag}>
            保存编排
          </Button>
        </Space>
      }
    >
      <div className="flow-dag-layout">
        <aside className="flow-dag-side">
          <Space direction="vertical" size={12} className="full-input">
            <Input.Search
              allowClear
              placeholder="搜索任务"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
            <Spin spinning={taskQuery.isFetching}>
              <List
                size="small"
                dataSource={taskPool}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可添加任务" /> }}
                renderItem={(task) => (
                  <List.Item
                    actions={[
                      <Button
                        key="add"
                        type="link"
                        size="small"
                        icon={<PlusOutlined />}
                        onClick={() => addTaskNode(task)}
                      >
                        添加
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={task.taskName}
                      description={
                        <Space size={6}>
                          <span>{task.taskCode}</span>
                          <Tag>{task.taskType}</Tag>
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            </Spin>
          </Space>
        </aside>

        <main className="flow-dag-canvas">
          <Spin spinning={loadingDag}>
            <div className="flow-editor-surface">
              <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={(_, node) => setSelectedNode(node)}
                fitView
              >
                <MiniMap />
                <Controls />
                <Background />
              </ReactFlow>
            </div>
          </Spin>
        </main>

        <aside className="flow-dag-side">
          {selectedNode ? (
            <Space direction="vertical" size={10} className="full-input">
              <div className="detail-summary-item">
                <div className="detail-summary-label">任务名称</div>
                <div className="detail-summary-value">{selectedNode.data.taskName}</div>
              </div>
              <div className="detail-summary-item">
                <div className="detail-summary-label">任务编码</div>
                <div className="detail-summary-value">{selectedNode.data.taskCode || "-"}</div>
              </div>
              <div className="detail-summary-item">
                <div className="detail-summary-label">任务类型</div>
                <div className="detail-summary-value">{selectedNode.data.taskType || "-"}</div>
              </div>
              <div className="detail-summary-item">
                <div className="detail-summary-label">节点 ID</div>
                <div className="detail-summary-value">{selectedNode.id}</div>
              </div>
            </Space>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择节点" />
          )}
        </aside>
      </div>
    </Drawer>
  );
}

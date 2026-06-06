import {
  CloseOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  Background,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { eventApi } from "../../scheduler-event/api";
import { flowApi, schedulerFlowRelationApi } from "../api";
import type {
  FlowCanvasEdge,
  FlowCanvasNode,
  FlowDagNodeData,
  FlowItem,
  SchedulerVariableValue,
  TaskDetailItem,
  TaskListItem,
} from "../dto";
import {
  canvasEdgeToDag,
  canvasNodeToDag,
  dagEdgeToCanvas,
  dagNodeToCanvas,
  formatJsonText,
  normalizeStringArray,
  parseParamData,
} from "../utils";

interface DagEditorProps {
  open: boolean;
  currentRecord?: FlowItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

interface DagEditorContentProps extends DagEditorProps {
  readOnly: boolean;
}

type VariableRow = SchedulerVariableValue & {
  key: string;
};

interface ScheduleInfoFormValues {
  pluginId?: string;
  depEventIds?: string[];
  eventId?: string;
  enabled?: boolean;
  varsText?: string;
}

const taskDragMime = "application/datafusion-task";

function isUnsynced(value?: boolean) {
  return value === false;
}

function TaskNode({ data, selected }: NodeProps<FlowCanvasNode>) {
  const className = [
    "flow-task-node",
    data.enabled ? "enabled" : "disabled",
    selected ? "selected" : "",
  ].filter(Boolean).join(" ");

  return (
    <div className={className}>
      <Handle type="target" position={Position.Top} id="top-target" />
      <Handle type="source" position={Position.Top} id="top-source" />
      <Handle type="target" position={Position.Left} id="left-target" />
      <Handle type="source" position={Position.Left} id="left-source" />
      <div className="flow-task-node-title">
        <span>{data.taskName || data.taskId || "-"}</span>
      </div>
      <div className="flow-task-node-code">{data.taskCode || "-"}</div>
      <Tag className="flow-task-node-type">{data.taskType || "-"}</Tag>
      <Handle type="target" position={Position.Right} id="right-target" />
      <Handle type="source" position={Position.Right} id="right-source" />
      <Handle type="target" position={Position.Bottom} id="bottom-target" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" />
    </div>
  );
}

const nodeTypes = {
  taskNode: TaskNode,
};

function createNodeFromTask(task: TaskListItem, position: { x: number; y: number }): FlowCanvasNode {
  return {
    id: task.id,
    type: "taskNode",
    position,
    data: {
      taskId: task.id,
      taskName: task.taskName,
      taskCode: task.taskCode,
      taskType: task.taskType,
      description: task.description,
      syncFlag: task.syncFlag,
      enabled: false,
    },
  };
}

function FlowTaskPanel({
  keyword,
  readOnly,
  existingNodeIds,
  taskPool,
  loading,
  onKeywordChange,
  onSearch,
  onAddTask,
}: {
  keyword: string;
  readOnly: boolean;
  existingNodeIds: Set<string>;
  taskPool: TaskListItem[];
  loading: boolean;
  onKeywordChange: (value: string) => void;
  onSearch: () => void;
  onAddTask: (task: TaskListItem) => void;
}) {
  const startDrag = (event: React.DragEvent<HTMLDivElement>, task: TaskListItem) => {
    if (readOnly || existingNodeIds.has(task.id)) return;
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData(taskDragMime, JSON.stringify(task));
  };

  return (
    <aside className="flow-dag-side">
      <Space direction="vertical" size={12} className="full-input">
        <Input.Search
          allowClear
          enterButton={<SearchOutlined />}
          placeholder="搜索任务名称 / 编码"
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          onSearch={onSearch}
        />
        {readOnly ? <Alert type="info" showIcon message="流程已发布或调度中，任务池只读" /> : null}
        <Spin spinning={loading}>
          <List
            className="flow-task-pool-list"
            size="small"
            dataSource={taskPool}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可添加任务" /> }}
            renderItem={(task) => {
              const exists = existingNodeIds.has(task.id);
              return (
                <List.Item
                  className={readOnly || exists ? "flow-task-pool-item disabled" : "flow-task-pool-item"}
                  draggable={!readOnly && !exists}
                  onDragStart={(event) => startDrag(event, task)}
                >
                  <div className="flow-task-pool-card">
                    <div className="flow-task-pool-title-row">
                      <div className="flow-task-pool-title">{task.taskName}</div>
                      <Button
                        type="link"
                        size="small"
                        icon={<PlusOutlined />}
                        disabled={readOnly || exists}
                        onClick={() => onAddTask(task)}
                      >
                        添加
                      </Button>
                    </div>
                    <div className="flow-task-pool-code">{task.taskCode}</div>
                    <div className="flow-task-pool-tags">
                      <Tag>{task.taskType}</Tag>
                      {isUnsynced(task.syncFlag) ? <Tag color="warning">未同步</Tag> : null}
                    </div>
                  </div>
                </List.Item>
              );
            }}
          />
        </Spin>
      </Space>
    </aside>
  );
}

function BasicInfoPanel({ node }: { node?: FlowCanvasNode }) {
  const data = node?.data;
  const paramData = parseParamData(data?.taskParam);
  const varsText = formatJsonText(paramData.vars || {});
  const definitionText = formatJsonText(data?.definition);

  if (!node || !data) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择节点" />;
  }

  return (
    <Space direction="vertical" size={12} className="full-input">
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="任务名称">{data.taskName || "-"}</Descriptions.Item>
        <Descriptions.Item label="任务编码">{data.taskCode || "-"}</Descriptions.Item>
        <Descriptions.Item label="任务类型">
          <Tag>{data.taskType || "-"}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="同步状态">
          {isUnsynced(data.syncFlag) ? <Tag color="warning">未同步</Tag> : <Tag color="success">已同步</Tag>}
        </Descriptions.Item>
        <Descriptions.Item label="任务描述">{data.description || "-"}</Descriptions.Item>
      </Descriptions>
      <div>
        <Typography.Text strong>调度变量 vars</Typography.Text>
        <pre className="json-block">{varsText || "暂无调度变量"}</pre>
      </div>
      <div>
        <Typography.Text strong>任务定义</Typography.Text>
        <pre className="json-block">{definitionText || "暂无任务定义"}</pre>
      </div>
    </Space>
  );
}

function validateVarsText(value?: string) {
  if (!value?.trim()) return {};

  const parsed = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("调度变量 vars 必须是 JSON 对象");
  }
  return parsed;
}

function buildOptionLabel(name?: string, extra?: string, fallback?: string) {
  const label = name || fallback || "-";
  return extra ? `${label} (${extra})` : label;
}

function buildSchedulePayload(values: ScheduleInfoFormValues, paramData: ReturnType<typeof parseParamData>) {
  const vars = validateVarsText(values.varsText);
  const taskParam = JSON.stringify({
    ...paramData,
    vars,
  });

  return {
    taskParam,
    payload: JSON.stringify({
      pluginId: values.pluginId || "",
      depEventIds: (values.depEventIds || []).join(","),
      eventId: values.eventId || "",
      enabled: Boolean(values.enabled),
      taskParam,
    }),
  };
}

function buildGeneratedEventName(data?: Partial<TaskDetailItem>) {
  if (!data?.taskName || !data.taskCode) return "";
  return `${data.taskName}|${data.taskCode}`;
}

function findEventName(events: Array<{ id: string; eventName?: string; name?: string }>, eventId?: string) {
  if (!eventId) return "";
  const event = events.find((item) => item.id === eventId);
  return event?.eventName || event?.name || "";
}

function isEventMissingError(error: unknown) {
  return error instanceof Error && error.message.includes("事件不存在");
}

function ScheduleInfoPanel({
  node,
  nodes,
  readOnly,
  onNodeDataPatch,
}: {
  node?: FlowCanvasNode;
  nodes: FlowCanvasNode[];
  readOnly: boolean;
  onNodeDataPatch: (nodeId: string, data: Partial<FlowDagNodeData>) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<ScheduleInfoFormValues>();
  const taskId = node?.id;
  const [saving, setSaving] = useState(false);
  const [eventSaving, setEventSaving] = useState(false);
  const [closedEventIds, setClosedEventIds] = useState<Set<string>>(() => new Set());
  const [saveStatus, setSaveStatus] = useState("");
  const lastSavedPayloadRef = useRef("");
  const detailQuery = useQuery({
    queryKey: ["scheduler-flow-task-detail", taskId],
    queryFn: () => schedulerFlowRelationApi.taskDetail(taskId || ""),
    enabled: Boolean(taskId),
  });
  const eventQuery = useQuery({
    queryKey: ["scheduler-flow-events"],
    queryFn: schedulerFlowRelationApi.events,
    enabled: Boolean(taskId),
  });
  const pluginQuery = useQuery({
    queryKey: ["scheduler-flow-plugins"],
    queryFn: () => schedulerFlowRelationApi.plugins(),
    enabled: Boolean(taskId),
  });

  const detail = detailQuery.data;
  const fallbackDetail = node?.data as Partial<TaskDetailItem> | undefined;
  const scheduleData = detail || fallbackDetail;
  const generatedEventName = useMemo(() => buildGeneratedEventName(scheduleData), [scheduleData]);
  const currentEventId = Form.useWatch("eventId", form);
  const effectiveEventId = useMemo(() => {
    if (currentEventId && !closedEventIds.has(currentEventId)) return currentEventId;
    const fallbackEventId = scheduleData?.eventId;
    return fallbackEventId && !closedEventIds.has(fallbackEventId) ? fallbackEventId : undefined;
  }, [closedEventIds, currentEventId, scheduleData?.eventId]);
  const paramData = useMemo(() => parseParamData(scheduleData?.taskParam), [scheduleData?.taskParam]);
  const producedEventName = useMemo(() => {
    if (!effectiveEventId) return "";

    const eventName = findEventName(eventQuery.data || [], effectiveEventId);
    return eventName || generatedEventName;
  }, [effectiveEventId, eventQuery.data, generatedEventName]);
  const variableRows = useMemo<VariableRow[]>(() => {
    const vars = paramData.vars || {};
    return Object.entries(vars).map(([key, value]) => ({
      key,
      name: value.name || key,
      type: value.type,
      value: value.value,
    }));
  }, [paramData.vars]);

  const columns: ColumnsType<VariableRow> = [
    { title: "变量名", dataIndex: "name", key: "name", width: 100 },
    { title: "类型", dataIndex: "type", key: "type", width: 80 },
    {
      title: "值",
      dataIndex: "value",
      key: "value",
      render: (value?: string) => value || "由上游传递",
    },
  ];

  const pluginOptions = useMemo(() => {
    const currentPluginId = scheduleData?.pluginId;
    const options = (pluginQuery.data || []).map((plugin) => ({
      label: buildOptionLabel(plugin.pluginName, plugin.pluginType, plugin.id),
      value: plugin.id,
    }));

    if (currentPluginId && !options.some((option) => option.value === currentPluginId)) {
      options.unshift({ label: currentPluginId, value: currentPluginId });
    }

    return options;
  }, [pluginQuery.data, scheduleData?.pluginId]);

  const eventOptions = useMemo(() => {
    const options = (eventQuery.data || []).map((event) => ({
      label: buildOptionLabel(event.eventName || event.name, event.eventType, event.id),
      value: event.id,
    }));
    const currentIds = [
      ...normalizeStringArray(scheduleData?.depEventIds),
      ...(scheduleData?.eventId ? [scheduleData.eventId] : []),
    ];

    currentIds.forEach((eventId) => {
      if (!options.some((option) => option.value === eventId)) {
        options.unshift({ label: eventId, value: eventId });
      }
    });

    return options;
  }, [eventQuery.data, scheduleData?.depEventIds, scheduleData?.eventId]);

  useEffect(() => {
    if (!node) {
      form.resetFields();
      lastSavedPayloadRef.current = "";
      setSaveStatus("");
      return;
    }

    const nextValues = {
      pluginId: scheduleData?.pluginId,
      depEventIds: normalizeStringArray(scheduleData?.depEventIds),
      eventId: scheduleData?.eventId && !closedEventIds.has(scheduleData.eventId) ? scheduleData.eventId : undefined,
      enabled: Boolean(scheduleData?.enabled),
      varsText: formatJsonText(paramData.vars || {}),
    };
    form.setFieldsValue(nextValues);
    lastSavedPayloadRef.current = buildSchedulePayload(nextValues, paramData).payload;
    setSaveStatus("");
  }, [closedEventIds, form, node, paramData, scheduleData?.depEventIds, scheduleData?.enabled, scheduleData?.eventId, scheduleData?.pluginId]);

  useEffect(() => {
    setClosedEventIds(new Set());
  }, [taskId]);

  const saveScheduleInfo = useCallback(async (
    showMessage = false,
    overrides: Partial<ScheduleInfoFormValues> = {},
    options: { clearEventId?: boolean; throwOnError?: boolean } = {},
  ) => {
    if (!taskId || readOnly) return;

    const formValues = await form.validateFields();
    const values = {
      ...formValues,
      ...overrides,
    };
    try {
      const { payload, taskParam } = buildSchedulePayload(values, paramData);
      const nextPayload = options.clearEventId
        ? JSON.stringify({ ...JSON.parse(payload), clearEventId: true })
        : payload;

      if (nextPayload === lastSavedPayloadRef.current) return;

      setSaving(true);
      setSaveStatus("自动保存中...");
      await schedulerFlowRelationApi.updateTask({
        id: taskId,
        pluginId: values.pluginId || undefined,
        depEventIds: (values.depEventIds || []).join(","),
        eventId: values.eventId || undefined,
        clearEventId: options.clearEventId,
        enabled: values.enabled,
        taskParam,
      });
      lastSavedPayloadRef.current = payload;
      setSaveStatus("已自动保存");
      if (showMessage) {
        message.success("调度信息保存成功");
      }
      onNodeDataPatch(taskId, {
        pluginId: values.pluginId,
        depEventIds: values.depEventIds,
        eventId: values.eventId,
        enabled: values.enabled,
        syncFlag: false,
        taskParam,
      });
      await detailQuery.refetch();
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
      if (options.throwOnError) {
        throw error;
      }
    } finally {
      setSaving(false);
    }
  }, [detailQuery, form, message, onNodeDataPatch, paramData, readOnly, taskId]);

  const handleScheduleValuesChange = (changedValues: Partial<ScheduleInfoFormValues>) => {
    if (
      readOnly
      || !taskId
      || Object.prototype.hasOwnProperty.call(changedValues, "varsText")
      || Object.prototype.hasOwnProperty.call(changedValues, "eventId")
    ) {
      return;
    }
    window.setTimeout(() => {
      void saveScheduleInfo();
    });
  };

  const eventReferencedByNode = useMemo(() => {
    if (!effectiveEventId) return undefined;
    return nodes.find((item) =>
      item.id !== taskId && normalizeStringArray(item.data?.depEventIds).includes(effectiveEventId),
    );
  }, [effectiveEventId, nodes, taskId]);

  const handleGeneratedEventChange = async (checked: boolean) => {
    if (!taskId || readOnly) return;
    if (!checked) {
      if (!effectiveEventId) return;
      if (eventReferencedByNode) {
        message.warning("该事件已被其他任务依赖引用，请解除依赖后关闭");
        return;
      }

      setEventSaving(true);
      const eventIdToClose = effectiveEventId;
      form.setFieldsValue({ eventId: undefined });
      setClosedEventIds((eventIds) => new Set(eventIds).add(eventIdToClose));
      try {
        await saveScheduleInfo(false, { eventId: undefined }, { clearEventId: true, throwOnError: true });
      } catch (error) {
        setClosedEventIds((eventIds) => {
          const nextEventIds = new Set(eventIds);
          nextEventIds.delete(eventIdToClose);
          return nextEventIds;
        });
        form.setFieldsValue({ eventId: eventIdToClose });
        if (error instanceof Error) {
          message.error(error.message || "关闭生成事件失败");
        }
        setEventSaving(false);
        return;
      }

      try {
        await eventApi.delete(eventIdToClose);
      } catch (error) {
        if (!isEventMissingError(error)) {
          setClosedEventIds((eventIds) => {
            const nextEventIds = new Set(eventIds);
            nextEventIds.delete(eventIdToClose);
            return nextEventIds;
          });
          form.setFieldsValue({ eventId: eventIdToClose });
          await saveScheduleInfo(false, { eventId: eventIdToClose });
          if (error instanceof Error) {
            message.error(error.message || "关闭生成事件失败，请解除依赖后关闭");
          }
          setEventSaving(false);
          return;
        }
      }

      try {
        form.setFieldsValue({ eventId: undefined });
        onNodeDataPatch(taskId, { eventId: undefined, syncFlag: false });
        await eventQuery.refetch();
        await detailQuery.refetch();
        message.success("生成事件已关闭");
      } finally {
        setEventSaving(false);
      }
      return;
    }

    if (effectiveEventId) return;
    if (!generatedEventName) {
      message.error("任务名称或任务编码为空，无法生成事件名称");
      return;
    }

    setEventSaving(true);
    try {
      const reusableEvent = (eventQuery.data || []).find((event) =>
        (event.eventName || event.name) === generatedEventName
        && event.eventType === "1"
        && (!event.taskId || event.taskId === taskId),
      );
      const eventId = reusableEvent?.id || await eventApi.add({
        eventName: generatedEventName,
        eventType: "1",
        taskId,
      });
      setClosedEventIds((eventIds) => {
        const nextEventIds = new Set(eventIds);
        nextEventIds.delete(eventId);
        return nextEventIds;
      });
      form.setFieldsValue({ eventId });
      await saveScheduleInfo(false, { eventId }, { throwOnError: true });
      onNodeDataPatch(taskId, { eventId, syncFlag: false });
      await eventQuery.refetch();
      await detailQuery.refetch();
      message.success("生成事件已开启");
    } catch (error) {
      form.setFieldsValue({ eventId: undefined });
      if (error instanceof Error) {
        message.error(error.message || "开启生成事件失败");
      }
    } finally {
      setEventSaving(false);
    }
  };

  if (!node) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择节点" />;
  }

  return (
    <Spin spinning={detailQuery.isFetching || eventQuery.isFetching || pluginQuery.isFetching}>
      <Space direction="vertical" size={12} className="full-input">
        {readOnly ? <Alert type="info" showIcon message="流程已发布或调度中，调度信息只读" /> : null}
        <Form<ScheduleInfoFormValues>
          form={form}
          layout="vertical"
          disabled={readOnly}
          onValuesChange={handleScheduleValuesChange}
        >
          <Form.Item label="执行插件" name="pluginId">
            <Select
              showSearch
              placeholder="请选择执行插件"
              optionFilterProp="label"
              options={pluginOptions}
            />
          </Form.Item>
          <Form.Item label="依赖事件" name="depEventIds">
            <Select
              mode="multiple"
              allowClear
              showSearch
              placeholder="请选择依赖事件"
              optionFilterProp="label"
              options={eventOptions}
            />
          </Form.Item>
          <Form.Item name="eventId" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="生成事件">
            <Switch
              checked={Boolean(effectiveEventId)}
              loading={eventSaving}
              checkedChildren="开启"
              unCheckedChildren="关闭"
              onChange={handleGeneratedEventChange}
            />
          </Form.Item>
          <Form.Item label="eventName">
            <Input value={producedEventName} placeholder="开启后自动生成事件名称" readOnly />
          </Form.Item>
          <Form.Item label="启用状态" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item
            label="调度变量 vars"
            name="varsText"
            rules={[
              {
                validator: (_, value?: string) => {
                  try {
                    validateVarsText(value);
                    return Promise.resolve();
                  } catch (error) {
                    return Promise.reject(error);
                  }
                },
              },
            ]}
          >
            <Input.TextArea rows={8} placeholder="请输入 vars JSON 对象" onBlur={() => void saveScheduleInfo()} />
          </Form.Item>
          <Typography.Text type="secondary">{saving ? "自动保存中..." : saveStatus || "修改后自动保存"}</Typography.Text>
        </Form>
        <div>
          <Typography.Text strong>调度变量 vars</Typography.Text>
          <Table<VariableRow>
            size="small"
            rowKey="key"
            columns={columns}
            dataSource={variableRows}
            pagination={false}
            locale={{ emptyText: "暂无调度变量" }}
          />
        </div>
      </Space>
    </Spin>
  );
}

function NodeSidePanel({
  selectedNode,
  nodes,
  open,
  readOnly,
  onClose,
  onNodeDataPatch,
}: {
  selectedNode?: FlowCanvasNode;
  nodes: FlowCanvasNode[];
  open: boolean;
  readOnly: boolean;
  onClose: () => void;
  onNodeDataPatch: (nodeId: string, data: Partial<FlowDagNodeData>) => void;
}) {
  if (!open) return null;

  return (
    <section className="flow-node-detail-panel">
      <div className="flow-node-detail-panel-header">
        <Button type="text" icon={<CloseOutlined />} onClick={onClose} />
        <Typography.Title level={5}>节点信息</Typography.Title>
      </div>
      <div className="flow-node-detail-panel-body">
        <Tabs
          size="small"
          items={[
            {
              key: "basic",
              label: "基本信息",
              children: <BasicInfoPanel node={selectedNode} />,
            },
            {
              key: "schedule",
              label: "调度信息",
              children: (
                <ScheduleInfoPanel
                  node={selectedNode}
                  nodes={nodes}
                  readOnly={readOnly}
                  onNodeDataPatch={onNodeDataPatch}
                />
              ),
            },
          ]}
        />
      </div>
    </section>
  );
}

function DagEditorContent({
  open,
  currentRecord,
  readOnly,
  onClose,
  onSubmitSuccess,
}: DagEditorContentProps) {
  const { message } = App.useApp();
  const reactFlow = useReactFlow<FlowCanvasNode, FlowCanvasEdge>();
  const canvasRef = useRef<HTMLDivElement>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowCanvasNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowCanvasEdge>([]);
  const [selectedNode, setSelectedNode] = useState<FlowCanvasNode>();
  const [, setSelectedEdge] = useState<Edge>();
  const [nodeDrawerOpen, setNodeDrawerOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    type: "node" | "edge";
    id: string;
  }>();
  const [keyword, setKeyword] = useState("");
  const [submittedKeyword, setSubmittedKeyword] = useState("");
  const [loadingDag, setLoadingDag] = useState(false);
  const [saving, setSaving] = useState(false);

  const taskQuery = useQuery({
    queryKey: ["scheduler-flow-task-pool", currentRecord?.id, submittedKeyword],
    queryFn: () =>
      schedulerFlowRelationApi.tasks({
        isBound: false,
        keyword: submittedKeyword || undefined,
      }),
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
      setSelectedEdge(undefined);
      setNodeDrawerOpen(false);
      setContextMenu(undefined);
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
    return list.filter((task) => !existingNodeIds.has(task.id));
  }, [existingNodeIds, taskQuery.data]);

  const addTaskNode = useCallback(
    (task: TaskListItem, position?: { x: number; y: number }) => {
      if (readOnly || existingNodeIds.has(task.id)) return;
      const nextIndex = nodes.length;
      const nextNode = createNodeFromTask(
        task,
        position || {
          x: 120 + (nextIndex % 4) * 220,
          y: 120 + Math.floor(nextIndex / 4) * 140,
        },
      );
      setNodes((currentNodes) => [
        ...currentNodes,
        nextNode,
      ]);
      setSelectedNode(nextNode);
      setSelectedEdge(undefined);
    },
    [existingNodeIds, nodes.length, readOnly, setNodes],
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      if (readOnly) return;
      setEdges((currentEdges) =>
        addEdge({ ...connection, type: "smoothstep", animated: true }, currentEdges),
      );
    },
    [readOnly, setEdges],
  );

  const onDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      if (readOnly) return;

      const raw = event.dataTransfer.getData(taskDragMime);
      if (!raw) return;

      try {
        const task = JSON.parse(raw) as TaskListItem;
        addTaskNode(
          task,
          reactFlow.screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
          }),
        );
      } catch {
        message.error("任务拖拽数据异常");
      }
    },
    [addTaskNode, message, reactFlow, readOnly],
  );

  const onDragOver = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      if (readOnly) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
    },
    [readOnly],
  );

  const removeNodeById = useCallback((nodeId: string) => {
    if (readOnly) return;
    setNodes((currentNodes) => currentNodes.filter((node) => node.id !== nodeId));
    setEdges((currentEdges) =>
      currentEdges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId),
    );
    setSelectedNode((currentNode) => (currentNode?.id === nodeId ? undefined : currentNode));
    setSelectedEdge(undefined);
    setNodeDrawerOpen(false);
    setContextMenu(undefined);
  }, [readOnly, setEdges, setNodes]);

  const removeEdgeById = useCallback((edgeId: string) => {
    if (readOnly) return;
    setEdges((currentEdges) => currentEdges.filter((edge) => edge.id !== edgeId));
    setSelectedEdge((currentEdge) => (currentEdge?.id === edgeId ? undefined : currentEdge));
    setContextMenu(undefined);
  }, [readOnly, setEdges]);

  const viewNodeInfo = () => {
    if (!contextMenu) return;
    if (contextMenu.type !== "node") return;
    const node = nodes.find((item) => item.id === contextMenu.id);
    if (!node) return;
    setSelectedNode(node);
    setSelectedEdge(undefined);
    setNodeDrawerOpen(true);
    setContextMenu(undefined);
  };

  const deleteContextTarget = () => {
    if (!contextMenu || readOnly) return;
    if (contextMenu.type === "node") removeNodeById(contextMenu.id);
    if (contextMenu.type === "edge") removeEdgeById(contextMenu.id);
  };

  const patchNodeData = useCallback(
    (nodeId: string, data: Partial<FlowDagNodeData>) => {
      setNodes((currentNodes) =>
        currentNodes.map((node) =>
          node.id === nodeId
            ? {
                ...node,
                data: {
                  ...node.data,
                  ...data,
                },
              }
            : node,
        ),
      );
      setSelectedNode((currentNode) =>
        currentNode?.id === nodeId
          ? {
              ...currentNode,
              data: {
                ...currentNode.data,
                ...data,
              },
            }
          : currentNode,
      );
    },
    [setNodes],
  );

  const saveDag = async () => {
    if (!currentRecord?.id || readOnly) return;

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
    setSelectedEdge(undefined);
    setNodeDrawerOpen(false);
    setContextMenu(undefined);
    onClose();
  };

  return (
    <Drawer
      title={currentRecord ? `流程编排：${currentRecord.flowName}` : "流程编排"}
      open={open}
      width="90vw"
      onClose={handleClose}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadDag}>
            重新加载
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={readOnly} onClick={saveDag}>
            保存编排
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={12} className="full-input">
        {readOnly ? (
          <Alert type="warning" showIcon message="已发布或调度中的流程只能查看，不能编辑流程编排" />
        ) : null}
        <div className="flow-dag-layout">
          <FlowTaskPanel
            keyword={keyword}
            readOnly={readOnly}
            existingNodeIds={existingNodeIds}
            taskPool={taskPool}
            loading={taskQuery.isFetching}
            onKeywordChange={setKeyword}
            onSearch={() => setSubmittedKeyword(keyword.trim())}
            onAddTask={addTaskNode}
          />

          <main className="flow-dag-canvas">
            <Spin spinning={loadingDag}>
              <div
                ref={canvasRef}
                className="flow-editor-surface"
                onDrop={onDrop}
                onDragOver={onDragOver}
              >
                <ReactFlow
                  nodes={nodes}
                  edges={edges}
                  nodeTypes={nodeTypes}
                  nodesDraggable={!readOnly}
                  nodesConnectable={!readOnly}
                  edgesFocusable
                  onNodesChange={readOnly ? undefined : onNodesChange}
                  onEdgesChange={readOnly ? undefined : onEdgesChange}
                  onConnect={onConnect}
                  onNodeClick={(_, node) => {
                    setSelectedNode(node);
                    setSelectedEdge(undefined);
                    setContextMenu(undefined);
                  }}
                  onNodeDoubleClick={(_, node) => {
                    setSelectedNode(node);
                    setSelectedEdge(undefined);
                    setNodeDrawerOpen(true);
                    setContextMenu(undefined);
                  }}
                  onEdgeClick={(_, edge) => {
                    setSelectedEdge(edge);
                    setSelectedNode(undefined);
                    setNodeDrawerOpen(false);
                    setContextMenu(undefined);
                  }}
                  onNodeContextMenu={(event, node) => {
                    event.preventDefault();
                    setSelectedNode(node);
                    setSelectedEdge(undefined);
                    setContextMenu({
                      x: event.clientX,
                      y: event.clientY,
                      type: "node",
                      id: node.id,
                    });
                  }}
                  onEdgeContextMenu={(event, edge) => {
                    event.preventDefault();
                    setSelectedEdge(edge);
                    setSelectedNode(undefined);
                    setNodeDrawerOpen(false);
                    setContextMenu({
                      x: event.clientX,
                      y: event.clientY,
                      type: "edge",
                      id: edge.id,
                    });
                  }}
                  onPaneClick={() => {
                    setSelectedNode(undefined);
                    setSelectedEdge(undefined);
                    setNodeDrawerOpen(false);
                    setContextMenu(undefined);
                  }}
                  onPaneContextMenu={(event) => {
                    event.preventDefault();
                    setContextMenu(undefined);
                  }}
                  fitView
                >
                  <MiniMap />
                  <Controls />
                  <Background />
                </ReactFlow>
              </div>
            </Spin>
            <NodeSidePanel
              selectedNode={selectedNode}
              nodes={nodes}
              open={nodeDrawerOpen && Boolean(selectedNode)}
              readOnly={readOnly}
              onClose={() => setNodeDrawerOpen(false)}
              onNodeDataPatch={patchNodeData}
            />
          </main>

          {contextMenu ? (
            <div
              className="flow-context-menu"
              style={{ left: contextMenu.x, top: contextMenu.y }}
              onClick={(event) => event.stopPropagation()}
            >
              {contextMenu.type === "node" ? (
                <button type="button" onClick={viewNodeInfo}>
                  <EyeOutlined />
                  查看节点信息
                </button>
              ) : null}
              <button type="button" className="danger" disabled={readOnly} onClick={deleteContextTarget}>
                <DeleteOutlined />
                {contextMenu.type === "node" ? "删除节点" : "删除连线"}
              </button>
            </div>
          ) : null}

        </div>
      </Space>
    </Drawer>
  );
}

export function DagEditor(props: DagEditorProps) {
  const readOnly = Boolean(props.currentRecord?.publishState || props.currentRecord?.enabled);

  return (
    <ReactFlowProvider>
      <DagEditorContent {...props} readOnly={readOnly} />
    </ReactFlowProvider>
  );
}

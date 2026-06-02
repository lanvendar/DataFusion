import { DeploymentUnitOutlined } from "@ant-design/icons";
import { Button, Drawer, Space, Tag } from "antd";
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  type Edge,
  type Node,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useState } from "react";
import ResourcePage, { type ResourceField } from "@/pages/shared/resource-page";

interface FlowRow extends Record<string, unknown> {
  id: string;
  flowName: string;
  flowCode: string;
  flowType: string;
  triggerName?: string;
  enabled: boolean;
  publishState: boolean;
  description?: string;
  updateTime?: string;
}

const fields: ResourceField<FlowRow>[] = [
  { key: "flowName", label: "流程名称", required: true, width: 180 },
  { key: "flowCode", label: "流程编码", required: true, width: 160 },
  {
    key: "flowType",
    label: "流程类型",
    type: "select",
    required: true,
    width: 140,
    options: [
      { label: "Stream 流任务", value: "1" },
      { label: "Batch 批任务", value: "2" },
    ],
    render: (value) => (
      <Tag color={value === "1" ? "green" : "blue"}>{value === "1" ? "Stream 流任务" : "Batch 批任务"}</Tag>
    ),
  },
  { key: "triggerName", label: "触发器", width: 150 },
  {
    key: "enabled",
    label: "调度状态",
    type: "boolean",
    width: 120,
    render: (value) => <Tag color={value ? "green" : "default"}>{value ? "调度中" : "未调度"}</Tag>,
  },
  {
    key: "publishState",
    label: "发布状态",
    type: "boolean",
    width: 120,
    render: (value) => <Tag color={value ? "blue" : "default"}>{value ? "已发布" : "未发布"}</Tag>,
  },
  { key: "description", label: "描述", type: "textarea", width: 180 },
  { key: "updateTime", label: "更新时间", hiddenInForm: true, width: 180 },
];

const demoRows: FlowRow[] = [
  {
    id: "flow-1",
    flowName: "每日订单加工",
    flowCode: "daily_order_dw",
    flowType: "2",
    triggerName: "每日凌晨调度",
    enabled: true,
    publishState: true,
    description: "ODS 到 DWD 的订单加工链路",
    updateTime: "2026-05-25 13:00:00",
  },
];

const nodes: Node[] = [
  { id: "source", type: "input", position: { x: 40, y: 90 }, data: { label: "读取源表" } },
  { id: "transform", position: { x: 300, y: 90 }, data: { label: "SQL 清洗转换" } },
  { id: "quality", position: { x: 560, y: 90 }, data: { label: "质量校验" } },
  { id: "sink", type: "output", position: { x: 820, y: 90 }, data: { label: "写入指标表" } },
];

const edges: Edge[] = [
  { id: "e1", source: "source", target: "transform", animated: true },
  { id: "e2", source: "transform", target: "quality", animated: true },
  { id: "e3", source: "quality", target: "sink", animated: true },
];

function FlowDagDrawer({ record, onClose }: { record?: FlowRow; onClose: () => void }) {
  return (
    <Drawer
      title={record ? `流程编排：${record.flowName}` : "流程编排"}
      open={Boolean(record)}
      width="82vw"
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>关闭</Button>
          <Button type="primary">保存编排</Button>
        </Space>
      }
    >
      <div className="flow-editor-surface">
        <ReactFlow nodes={nodes} edges={edges} fitView>
          <MiniMap />
          <Controls />
          <Background />
        </ReactFlow>
      </div>
    </Drawer>
  );
}

export default function SchedulerFlowPage() {
  const [dagRecord, setDagRecord] = useState<FlowRow>();

  return (
    <>
      <ResourcePage<FlowRow>
        title="流程管理"
        description="维护调度流程定义、发布状态、调度状态，并使用 React Flow 进行 DAG 编排。"
        breadcrumb={["调度中心", "流程管理"]}
        entityName="流程"
        endpoints={{
          list: "/api/scheduler/flow/page",
          add: "/api/scheduler/flow/add",
          update: "/api/scheduler/flow/update",
          delete: "/api/scheduler/flow/delete",
        }}
        fields={fields}
        demoRows={demoRows}
        mapSearch={(keyword) => ({ flowName: keyword })}
        extraActions={[
          {
            key: "dag",
            label: "编排",
            onClick: (record) => setDagRecord(record),
          },
          {
            key: "publish",
            label: "发布",
            disabled: (record) => Boolean(record.publishState),
            onClick: () => undefined,
          },
          {
            key: "toggle",
            label: "启停",
            onClick: () => undefined,
          },
        ]}
      />
      <div className="flow-hint">
        <DeploymentUnitOutlined />
        已用 @xyflow/react 替代原私有 DAG 编辑器，后续可继续迁移任务面板和依赖事件选择。
      </div>
      <FlowDagDrawer record={dagRecord} onClose={() => setDagRecord(undefined)} />
    </>
  );
}

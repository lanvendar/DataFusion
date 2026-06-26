import { Space, Tag, Typography } from "antd";
import dayjs from "dayjs";
import {
  EMPTY_PLACEHOLDER,
  statusColorMap,
  statusOptions,
} from "./constants";
import type { TaskInstanceItem, TaskWorkerPluginResult } from "./dto";

export function getRows<T>(page?: { dataList?: T[]; records?: T[]; list?: T[] }) {
  return page?.dataList || page?.records || page?.list || [];
}

export function formatTime(value?: number) {
  if (!value) return EMPTY_PLACEHOLDER;
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

export function formatDuration(value?: number) {
  if (value === undefined || value === null) return EMPTY_PLACEHOLDER;
  if (value < 1000) return `${value}ms`;
  return `${(value / 1000).toFixed(1)}s`;
}

export function formatJson(value?: unknown) {
  if (!value) return EMPTY_PLACEHOLDER;
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function getTaskPluginLogUri(task: TaskInstanceItem) {
  if (task.workerResult?.pluginLogUri) return task.workerResult.pluginLogUri;
  const result = task.workerResult?.result;
  if (!result || typeof result === "string") return undefined;
  return (result as TaskWorkerPluginResult).pluginLogUri || undefined;
}

export function getTaskWorkDirPath(task: TaskInstanceItem) {
  return task.workDirPath || task.workerResult?.workDirPath;
}

export function getTaskWorkerResultRows(task: TaskInstanceItem) {
  const workerResult = task.workerResult;
  return [
    { label: "workerResultText", value: task.workerResultText },
    { label: "message", value: workerResult?.message },
    { label: "appId", value: workerResult?.appId },
    { label: "workerId", value: workerResult?.workerId || task.workerId },
    { label: "workDirPath", value: getTaskWorkDirPath(task) },
    { label: "pluginLogUri", value: getTaskPluginLogUri(task) },
  ].filter((item) => item.value);
}

export function renderStatus(value?: string) {
  const label = statusOptions.find((item) => item.value === value)?.label || value || EMPTY_PLACEHOLDER;
  return <Tag color={value ? statusColorMap[value] || "default" : "default"}>{label}</Tag>;
}

export function renderFlowType(value?: string) {
  const labelMap: Record<string, string> = {
    "1": "Stream 流任务",
    "2": "Batch 批任务",
  };
  const colorMap: Record<string, string> = {
    "1": "green",
    "2": "blue",
  };
  return (
    <Tag color={value ? colorMap[value] || "default" : "default"}>
      {value ? labelMap[value] || value : EMPTY_PLACEHOLDER}
    </Tag>
  );
}

export function renderType(value?: string) {
  return <Tag>{value || EMPTY_PLACEHOLDER}</Tag>;
}

export function renderCopyableId(value?: string) {
  if (!value) return <Typography.Text type="secondary">{EMPTY_PLACEHOLDER}</Typography.Text>;

  return (
    <Typography.Text
      copyable={{ text: value }}
      ellipsis={{ tooltip: value }}
      style={{ display: "block", maxWidth: "100%", fontSize: 12 }}
      type="secondary"
    >
      {value}
    </Typography.Text>
  );
}

export function renderTimeBlock(startTime?: number, endTime?: number, duration?: number) {
  return (
    <Space direction="vertical" size={2}>
      <Typography.Text>{formatTime(startTime)}</Typography.Text>
      <Typography.Text type="secondary">{formatTime(endTime)}</Typography.Text>
      <Typography.Text type="secondary">{formatDuration(duration)}</Typography.Text>
    </Space>
  );
}

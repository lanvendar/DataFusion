import type { WorkerRegistryItem, WorkerRegistryPageOption } from "./dto";

export const SCHEDULER_WORKER_QUERY_KEY = "scheduler-worker";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: WorkerRegistryPageOption = {
  workerCode: "",
  hostName: "",
  host: "",
  status: undefined,
  zone: "",
  isActive: undefined,
};

export const workerStatusOptions = [
  { label: "下线", value: 0 },
  { label: "上线", value: 1 },
];

export const activeOptions = [
  { label: "有效", value: 1 },
  { label: "无效", value: 0 },
];

export const demoWorkerRows: WorkerRegistryItem[] = [
  {
    id: "11111111-1111-4111-8111-111111111111",
    workerCode: "worker-local-01",
    hostName: "df-worker-01",
    host: "127.0.0.1",
    port: 18080,
    status: 1,
    zone: "default",
    plugins: "DATAX,FLINK",
    registerTime: "2026-06-06 09:00:00",
    lastHeartbeatTime: "2026-06-06 09:30:00",
    isActive: 1,
    remark: "本地开发 worker",
    updater: "system",
    updateTime: "2026-06-06 09:30:00",
  },
];

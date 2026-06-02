import axios, { type AxiosError, type AxiosRequestConfig } from "axios";

export interface ApiPage<T> {
  dataList?: T[];
  records?: T[];
  list?: T[];
  current?: number;
  size?: number;
  total?: number;
}

export interface ApiEnvelope<T> {
  code?: number | string;
  description?: string;
  msg?: string;
  message?: string;
  errorMsg?: string;
  data?: T;
  result?: T;
}

export interface PageQuery {
  current?: number;
  size?: number;
  option?: Record<string, unknown>;
}

export interface DemoRow {
  id: string;
  code: string;
  name: string;
  owner: string;
  domain: string;
  status: "enabled" | "disabled" | "draft";
  updatedAt: string;
}

export class HttpError extends Error {
  status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = "HttpError";
    this.status = status;
  }
}

const successCodes = new Set<unknown>([0, "0", "00000", 200, "200"]);

const http = axios.create({
  baseURL: "/",
  timeout: 60_000,
  headers: {
    "Content-Type": "application/json",
  },
});

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiEnvelope<unknown>;
    const hasBusinessError =
      body && typeof body === "object" && "code" in body && !successCodes.has(body.code);

    if (hasBusinessError) {
      throw new HttpError(
        body.errorMsg || body.message || body.msg || body.description || "请求处理失败",
        response.status,
      );
    }

    return response;
  },
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const message =
      error.response?.data?.errorMsg ||
      error.response?.data?.message ||
      error.response?.data?.msg ||
      error.response?.data?.description ||
      error.message ||
      "网络请求失败";

    throw new HttpError(message, error.response?.status);
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T> | T>(config);
  const body = response.data;

  if (body && typeof body === "object") {
    const envelope = body as ApiEnvelope<T>;
    if ("data" in envelope) return envelope.data as T;
    if ("result" in envelope) return envelope.result as T;
  }

  return body as T;
}

export async function postPage<T>(
  url: string,
  query: PageQuery,
): Promise<ApiPage<T>> {
  return request<ApiPage<T>>({
    url,
    method: "POST",
    data: query,
  });
}

export function createDemoRows(entityName: string): DemoRow[] {
  const domains = ["元数据", "指标中心", "数据资产", "调度中心", "数据开发"];
  const owners = ["Lan", "DataOps", "Platform", "BI Team", "Governance"];

  return Array.from({ length: 8 }, (_, index) => ({
    id: `${entityName}-${index + 1}`,
    code: `${entityName.toUpperCase()}_${String(index + 1).padStart(3, "0")}`,
    name: `${entityName}示例 ${index + 1}`,
    owner: owners[index % owners.length],
    domain: domains[index % domains.length],
    status: index % 3 === 0 ? "draft" : index % 2 === 0 ? "disabled" : "enabled",
    updatedAt: `2026-05-${String(18 + index).padStart(2, "0")}`,
  }));
}

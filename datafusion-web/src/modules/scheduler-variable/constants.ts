import type { VariableItem, VariablePageOption } from "./dto";

export const SCHEDULER_VARIABLE_QUERY_KEY = "scheduler-variable";

export const DEFAULT_PAGE_SIZE = 10;

export const defaultFilter: VariablePageOption = {
  code: "",
  name: "",
  type: undefined,
  valueType: undefined,
};

export const variableTypeOptions = [
  { label: "CUSTOM", value: "CUSTOM" },
  { label: "SYSTEM", value: "SYSTEM" },
];

export const valueTypeOptions = [
  { label: "STRING", value: "STRING" },
  { label: "EXPRESSION", value: "EXPRESSION" },
];

export const demoVariableRows: VariableItem[] = [
  {
    id: "var-1",
    code: "biz_date",
    name: "业务日期",
    type: "SYSTEM",
    valueType: "EXPRESSION",
    value: "${yyyyMMdd-1}",
    updater: "scheduler",
    updateTime: "2026-05-25 09:30:00",
  },
];

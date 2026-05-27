import { App } from "antd";
import type { TransferProps } from "antd";
import { useEffect, useMemo, useState } from "react";
import { datasourceApi } from "../../api";
import type { DatasourceItem, TableRegisterTableItem } from "../../dto";

interface UseRegisterOptions {
  open: boolean;
  currentRecord?: DatasourceItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export interface TransferItem {
  key: string;
  title: string;
}

export function useTableRegister({
  open,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: UseRegisterOptions) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<TableRegisterTableItem[]>([]);
  const [targetKeys, setTargetKeys] = useState<TransferProps["targetKeys"]>([]);

  useEffect(() => {
    if (!open || !currentRecord) return;
    setLoading(true);
    datasourceApi
      .getTableInfos(currentRecord.id)
      .then((data) => {
        const next = Array.isArray(data) ? data : [];
        setItems(next);
        setTargetKeys(next.filter((item) => item.registered).map((item) => item.tableName));
      })
      .catch(() => {
        const fallback = ["ods_order", "ods_product", "dim_product", "dwd_order_detail"].map((tableName) => ({
          tableName,
          registered: tableName.startsWith("dim"),
        }));
        setItems(fallback);
        setTargetKeys(fallback.filter((item) => item.registered).map((item) => item.tableName));
      })
      .finally(() => setLoading(false));
  }, [currentRecord, open]);

  const dataSource = useMemo<TransferItem[]>(
    () =>
      items.map((item) => ({
        key: item.tableName,
        title: item.tableName,
      })),
    [items],
  );

  const submit = async () => {
    if (!currentRecord) return;
    await datasourceApi.registerTables({
      datasourceId: currentRecord.id,
      tableNames: (targetKeys || []).map(String),
    });
    message.success("表登记成功");
    onSubmitSuccess();
    onClose();
  };

  return {
    loading,
    dataSource,
    targetKeys,
    setTargetKeys,
    submit,
  };
}

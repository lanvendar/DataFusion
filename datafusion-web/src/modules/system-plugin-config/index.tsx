import { App, Space } from "antd";
import { useCallback, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/page-layout";
import { pluginConfigApi } from "./api";
import { PluginConfigForm, PluginConfigListTable } from "./components";
import { SYSTEM_PLUGIN_CONFIG_QUERY_KEY } from "./constants";
import {
  PageActionEnum,
  type PluginConfigFormMode,
  type PluginConfigItem,
} from "./dto";

export default function SystemPluginConfigPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<PluginConfigFormMode>("add");
  const [currentRecord, setCurrentRecord] = useState<PluginConfigItem>();

  const refreshList = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: [SYSTEM_PLUGIN_CONFIG_QUERY_KEY] });
  }, [queryClient]);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => pluginConfigApi.delete(id),
    onSuccess: () => {
      message.success("删除成功");
      refreshList();
    },
  });

  const copyMutation = useMutation({
    mutationFn: (record: PluginConfigItem) =>
      pluginConfigApi.copy({
        pluginName: record.pluginName,
        pluginType: record.pluginType,
        runMode: record.runMode,
        description: record.description,
        pluginParam: record.pluginParam,
      }),
    onSuccess: () => {
      message.success("复制成功");
      refreshList();
    },
  });

  const openForm = useCallback((mode: PluginConfigFormMode, record?: PluginConfigItem) => {
    setFormMode(mode);
    setCurrentRecord(record);
    setFormOpen(true);
  }, []);

  const onAction = useCallback(
    (action: PageActionEnum, record?: PluginConfigItem) => {
      switch (action) {
        case PageActionEnum.ADD:
          openForm("add");
          break;
        case PageActionEnum.COPY:
          if (record) copyMutation.mutate(record);
          break;
        case PageActionEnum.EDIT:
          openForm("edit", record);
          break;
        case PageActionEnum.DELETE:
          if (record?.id) deleteMutation.mutate(record.id);
          break;
        default:
          break;
      }
    },
    [copyMutation, deleteMutation, openForm],
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: "系统配置" }, { label: "插件配置" }]}
        title="插件配置"
        description="维护系统插件的类型、运行模式、模板标记和 JSON 配置。"
      />

      <PluginConfigListTable
        loading={deleteMutation.isPending || copyMutation.isPending}
        onAction={onAction}
      />

      <PluginConfigForm
        open={formOpen}
        mode={formMode}
        currentRecord={currentRecord}
        onClose={() => setFormOpen(false)}
        onSubmitSuccess={refreshList}
      />
    </Space>
  );
}

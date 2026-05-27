import { Button, Input, Modal, Transfer } from "antd";
import type { DatasourceItem } from "../../dto";
import { useTableRegister } from "./use-register";

interface TableRegisterProps {
  open: boolean;
  currentRecord?: DatasourceItem;
  onClose: () => void;
  onSubmitSuccess: () => void;
}

export function TableRegister({
  open,
  currentRecord,
  onClose,
  onSubmitSuccess,
}: TableRegisterProps) {
  const { loading, dataSource, targetKeys, setTargetKeys, submit } = useTableRegister({
    open,
    currentRecord,
    onClose,
    onSubmitSuccess,
  });

  return (
    <Modal
      title={`数据库表登记注册${currentRecord ? `：${currentRecord.name}` : ""}`}
      open={open}
      width={760}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="submit" type="primary" onClick={submit}>
          确认
        </Button>,
      ]}
    >
      <Transfer
        disabled={loading}
        dataSource={dataSource}
        targetKeys={targetKeys}
        onChange={setTargetKeys}
        showSearch
        oneWay={false}
        render={(item) => item.title}
        titles={["可选表", "已登记表"]}
        listStyle={{ width: 320, height: 360 }}
        filterOption={(input, item) => item.title.toLowerCase().includes(input.toLowerCase())}
        selectAllLabels={[
          ({ selectedCount, totalCount }) => `已选 ${selectedCount}/${totalCount}`,
          ({ selectedCount, totalCount }) => `已选 ${selectedCount}/${totalCount}`,
        ]}
        footer={() => <Input.Search placeholder={loading ? "表列表加载中..." : "可直接使用上方搜索过滤表名"} disabled />}
      />
    </Modal>
  );
}

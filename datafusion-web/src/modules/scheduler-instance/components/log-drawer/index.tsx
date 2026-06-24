import { ExportOutlined } from "@ant-design/icons";
import { Button, Drawer, Segmented, Space } from "antd";
import { useState } from "react";
import { logTypeOptions } from "../../constants";
import type { TaskInstanceItem, TaskInstanceLogType } from "../../dto";
import { TaskLogContent } from "../log-content";

interface TaskLogDrawerProps {
  open: boolean;
  task?: TaskInstanceItem;
  onClose: () => void;
}

export function TaskLogDrawer({ open, task, onClose }: TaskLogDrawerProps) {
  const [logType, setLogType] = useState<TaskInstanceLogType>("LOG");

  const handleClose = () => {
    setLogType("LOG");
    onClose();
  };

  const openLogPage = () => {
    if (!task?.flowInstanceId || !task.id) return;
    const params = new URLSearchParams({
      flowInstanceId: task.flowInstanceId,
      taskInstanceId: task.id,
      taskName: task.taskName || task.id,
      logType,
    });
    window.open(`/scheduler-instance/log?${params.toString()}`, "_blank", "noopener,noreferrer");
  };

  return (
    <Drawer
      title="任务日志"
      open={open}
      width={820}
      onClose={handleClose}
      extra={
        <Space>
          <Segmented
            options={logTypeOptions}
            value={logType}
            onChange={(value) => setLogType(value as TaskInstanceLogType)}
          />
          <Button
            type="link"
            icon={<ExportOutlined />}
            disabled={!task}
            onClick={openLogPage}
          >
            打开页面
          </Button>
        </Space>
      }
    >
      {open ? <TaskLogContent flowInstanceId={task?.flowInstanceId} taskInstanceId={task?.id} logType={logType} /> : null}
    </Drawer>
  );
}

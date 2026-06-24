import { Drawer, Segmented } from "antd";
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

  return (
    <Drawer
      title="任务日志"
      open={open}
      width={820}
      onClose={handleClose}
      extra={
        <Segmented
          options={logTypeOptions}
          value={logType}
          onChange={(value) => setLogType(value as TaskInstanceLogType)}
        />
      }
    >
      {open ? <TaskLogContent flowInstanceId={task?.flowInstanceId} taskInstanceId={task?.id} logType={logType} /> : null}
    </Drawer>
  );
}

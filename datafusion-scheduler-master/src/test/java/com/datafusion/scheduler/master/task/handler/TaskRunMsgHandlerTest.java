package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.TaskResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link TaskRunMsgHandler}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/24
 * @since 1.0.0
 */
class TaskRunMsgHandlerTest {

    @Test
    void shouldKeepSubmittingWhenRunMessageHasNoWorkerResult() {
        RecordingTaskStorage taskStorage = new RecordingTaskStorage(taskInstance(StatusEnum.SUBMITTING));
        TaskRunMsgHandler handler = taskRunMsgHandler(taskStorage);

        handler.handleAction(taskMsg(null), null);

        assertEquals(StatusEnum.SUBMITTING, taskStorage.taskInstance.getState());
        assertNull(taskStorage.taskInstance.getStartTime());
    }

    @Test
    void shouldKeepSubmitSuccessUntilWorkerReportsRunning() {
        RecordingTaskStorage taskStorage = new RecordingTaskStorage(taskInstance(StatusEnum.SUBMITTING));
        TaskRunMsgHandler handler = taskRunMsgHandler(taskStorage);

        handler.handleAction(taskMsg(taskResult(StatusEnum.SUBMIT_SUCCESS)), null);

        assertEquals(StatusEnum.SUBMIT_SUCCESS, taskStorage.taskInstance.getState());
        assertNull(taskStorage.taskInstance.getStartTime());
    }

    @Test
    void shouldMarkRunningOnlyWhenWorkerReportsRunning() {
        RecordingTaskStorage taskStorage = new RecordingTaskStorage(taskInstance(StatusEnum.SUBMIT_SUCCESS));
        TaskRunMsgHandler handler = taskRunMsgHandler(taskStorage);

        handler.handleAction(taskMsg(taskResult(StatusEnum.RUNNING)), null);

        assertEquals(StatusEnum.RUNNING, taskStorage.taskInstance.getState());
        assertNotNull(taskStorage.taskInstance.getStartTime());
    }

    @Test
    void shouldMarkUnknownAsFinalFailureWhenWorkerReportsUnknown() {
        RecordingTaskStorage taskStorage = new RecordingTaskStorage(taskInstance(StatusEnum.RUNNING));
        TaskRunMsgHandler handler = taskRunMsgHandler(taskStorage);

        handler.handleAction(taskMsg(taskResult(StatusEnum.UNKNOWN)), null);

        assertEquals(StatusEnum.UNKNOWN, taskStorage.taskInstance.getState());
        assertNotNull(taskStorage.taskInstance.getStartTime());
        assertNotNull(taskStorage.taskInstance.getEndTime());
    }

    private TaskRunMsgHandler taskRunMsgHandler(RecordingTaskStorage taskStorage) {
        return new TaskRunMsgHandler(taskStorage, null, new NoopMasterTaskOperator());
    }

    private TaskInstance taskInstance(StatusEnum state) {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setInstanceId("task-1");
        taskInstance.setFlowInstanceId("flow-1");
        taskInstance.setState(state);
        return taskInstance;
    }

    private TaskMsg taskMsg(TaskResult taskResult) {
        return TaskMsg.builder()
                .taskInstanceId("task-1")
                .taskResult(taskResult)
                .build();
    }

    private TaskResult taskResult(StatusEnum state) {
        return TaskResult.builder()
                .taskInstanceId("task-1")
                .taskState(state)
                .build();
    }

    /**
     * Recording task storage.
     */
    private static class RecordingTaskStorage implements TaskStorage {

        /**
         * Task instance.
         */
        private TaskInstance taskInstance;

        RecordingTaskStorage(TaskInstance taskInstance) {
            this.taskInstance = taskInstance;
        }

        @Override
        public TaskInfo getTaskInfo(String taskId) {
            return null;
        }

        @Override
        public List<TaskLink> getTaskInfoLink(String flowId) {
            return Collections.emptyList();
        }

        @Override
        public List<TaskInfo> getTaskInfoByFlowId(String flowId) {
            return Collections.emptyList();
        }

        @Override
        public TaskInstance getInstanceById(String taskInsId) {
            return taskInstance;
        }

        @Override
        public void saveInstance(TaskInstance taskInstance) {
            this.taskInstance = taskInstance;
        }

        @Override
        public void removeInstanceById(String taskInsId) {
            taskInstance = null;
        }

        @Override
        public List<TaskInstance> getTaskInsIdsByFlowInsId(String flowInsId) {
            return Collections.emptyList();
        }

        @Override
        public void removeTaskInsByFlowInsId(String flowInsId) {
        }
    }

    /**
     * Noop master task operator.
     */
    private static class NoopMasterTaskOperator implements MasterTaskOperator {

        @Override
        public TaskResult submitTask(TaskInstance taskIns) {
            return null;
        }

        @Override
        public TaskResult stopTask(TaskInstance taskIns) {
            return null;
        }

        @Override
        public TaskResult killTask(TaskInstance taskIns) {
            return null;
        }

        @Override
        public boolean finishTask(TaskInstance taskIns) {
            return true;
        }
    }
}

package com.datafusion.manager.scheduler.service.impl;

import com.datafusion.manager.scheduler.dto.WorkerRegistryActiveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryUpdateDto;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import com.datafusion.scheduler.worker.WorkerOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WorkerRegistryServiceImpl}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class WorkerRegistryServiceImplTest {

    /**
     * worker 人工操作接口.
     */
    private WorkerOperator workerOperator;

    /**
     * 被测服务.
     */
    private WorkerRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        workerOperator = mock(WorkerOperator.class);
        service = spy(new WorkerRegistryServiceImpl(workerOperator));
    }

    @Test
    void shouldOnlyUpdateManagedFields() {
        UUID id = UUID.randomUUID();
        doReturn(new WorkerRegistryEntity()).when(service).getById(id);
        doReturn(true).when(service).updateById(any(WorkerRegistryEntity.class));

        WorkerRegistryUpdateDto dto = new WorkerRegistryUpdateDto();
        dto.setId(id);
        dto.setZone("zone-b");
        dto.setRemark("standby worker");

        assertTrue(service.updateWorkerRegistry(dto));
        ArgumentCaptor<WorkerRegistryEntity> captor = ArgumentCaptor.forClass(WorkerRegistryEntity.class);
        verify(service).updateById(captor.capture());
        WorkerRegistryEntity updated = captor.getValue();
        assertEquals(id, updated.getId());
        assertEquals("zone-b", updated.getZone());
        assertEquals("standby worker", updated.getRemark());
        assertNull(updated.getHost());
        assertNull(updated.getPlugins());
        assertNull(updated.getIsActive());
    }

    @Test
    void shouldEnableWorkerThroughOperator() {
        UUID id = UUID.randomUUID();
        doReturn(new WorkerRegistryEntity()).when(service).getById(id);
        when(workerOperator.active(id.toString())).thenReturn(true);

        WorkerRegistryActiveDto dto = activeDto(id, 1);

        assertTrue(service.updateWorkerActive(dto));
        verify(workerOperator).active(id.toString());
        verify(workerOperator, never()).inactive(id.toString());
    }

    @Test
    void shouldDisableWorkerThroughOperator() {
        UUID id = UUID.randomUUID();
        doReturn(new WorkerRegistryEntity()).when(service).getById(id);
        when(workerOperator.inactive(id.toString())).thenReturn(true);

        WorkerRegistryActiveDto dto = activeDto(id, 0);

        assertTrue(service.updateWorkerActive(dto));
        verify(workerOperator).inactive(id.toString());
        verify(workerOperator, never()).active(id.toString());
    }

    private WorkerRegistryActiveDto activeDto(UUID id, int isActive) {
        WorkerRegistryActiveDto dto = new WorkerRegistryActiveDto();
        dto.setId(id);
        dto.setIsActive(isActive);
        return dto;
    }
}

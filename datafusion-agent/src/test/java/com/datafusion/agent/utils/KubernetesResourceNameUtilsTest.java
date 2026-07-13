package com.datafusion.agent.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kubernetes 资源名称工具类测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
class KubernetesResourceNameUtilsTest {

    @Test
    void shouldGeneratePrimaryResourceName() {
        assertEquals("df-spark-task-1", KubernetesResourceNameUtils.resourceName("DF_Spark-", "Task_1"));
    }

    @Test
    void shouldGenerateRoleResourceName() {
        assertEquals("df-spark-job-config-task-1",
                KubernetesResourceNameUtils.resourceName("df-spark", "job-config", "task-1"));
    }

    @Test
    void shouldGenerateOrdinalResourceName() {
        assertEquals("df-spark-executor-0-task-1",
                KubernetesResourceNameUtils.resourceName("df-spark", "executor", 0, "task-1"));
    }

    @Test
    void shouldRejectInvalidSegmentAndOrdinal() {
        assertThrows(IllegalArgumentException.class,
                () -> KubernetesResourceNameUtils.resourceName("df-spark", "###", "task-1"));
        assertThrows(IllegalArgumentException.class,
                () -> KubernetesResourceNameUtils.resourceName("df-spark", "executor", -1, "task-1"));
    }

    @Test
    void shouldUseStableHashForLongNames() {
        String first = KubernetesResourceNameUtils.resourceName("df-spark",
                "task-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-0001");
        String same = KubernetesResourceNameUtils.resourceName("df-spark",
                "task-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-0001");
        String second = KubernetesResourceNameUtils.resourceName("df-spark",
                "task-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-0002");

        assertEquals(first, same);
        assertNotEquals(first, second);
        assertTrue(first.length() <= 63);
        assertTrue(first.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?"));
    }
}

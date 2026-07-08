package com.datafusion.plugin.flink.table.sink;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

/**
 * PaimonCommittableSerializer 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class PaimonCommittableSerializerTest {

    @Test
    void shouldSerializeAndDeserializeEmptyCommitMessages() throws Exception {
        PaimonCommittable committable = new PaimonCommittable();
        committable.database = "dw_dev";
        committable.tableName = "ods_test";
        committable.commitIdentifier = 12L;
        committable.commitMessages = new ArrayList<>();

        PaimonCommittableSerializer serializer = new PaimonCommittableSerializer();
        PaimonCommittable actual = serializer.deserialize(serializer.getVersion(), serializer.serialize(committable));

        Assertions.assertEquals("dw_dev", actual.database);
        Assertions.assertEquals("ods_test", actual.tableName);
        Assertions.assertEquals(12L, actual.commitIdentifier);
        Assertions.assertTrue(actual.commitMessages.isEmpty());
    }
}

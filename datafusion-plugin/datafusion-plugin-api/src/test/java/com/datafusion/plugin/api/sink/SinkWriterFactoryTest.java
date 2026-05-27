package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.sink.paimon.PaimonSinkWriter;
import com.datafusion.plugin.api.sink.starrocks.StarRocksSinkWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Sink writer factory tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class SinkWriterFactoryTest {

    private final SinkWriterFactory factory = new SinkWriterFactory();

    @Test
    public void createShouldReturnStarRocksWriter() {
        ApiExtractJobConfig.SinkConfig sink = new ApiExtractJobConfig.SinkConfig();
        sink.type = "STARROCKS";

        Assertions.assertInstanceOf(StarRocksSinkWriter.class, factory.create(sink));
    }

    @Test
    public void createShouldReturnPaimonWriter() {
        ApiExtractJobConfig.SinkConfig sink = new ApiExtractJobConfig.SinkConfig();
        sink.type = "PAIMON";

        Assertions.assertInstanceOf(PaimonSinkWriter.class, factory.create(sink));
    }

    @Test
    public void createShouldRejectUnsupportedSinkType() {
        ApiExtractJobConfig.SinkConfig sink = new ApiExtractJobConfig.SinkConfig();
        sink.type = "UNKNOWN";

        Assertions.assertThrows(ApiExtractException.class, () -> factory.create(sink));
    }
}

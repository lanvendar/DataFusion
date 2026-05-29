package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.ColumnConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Sink record normalizer tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class SinkRecordNormalizerTest {

    @Test
    public void normalizeShouldApplyColumnDefaultValue() {
        SinkConfig sink = new SinkConfig();
        sink.columns.add(column("product_id_name", false, null));
        sink.columns.add(column("product_detail_id_name", false, "UNKNOWN"));
        sink.columns.add(column("product_class", false, null));
        Record record = new Record();
        record.put("product_id_name", "1110");
        record.put("product_detail_id_name", null);
        record.put("product_class", "A");

        List<Record> records = SinkRecordNormalizer.normalize(List.of(record), sink, "Paimon");

        Assertions.assertEquals("UNKNOWN", records.get(0).get("product_detail_id_name"));
    }

    @Test
    public void normalizeShouldRejectMissingRequiredColumnWithoutDefaultValue() {
        SinkConfig sink = new SinkConfig();
        sink.columns.add(column("product_detail_id_name", false, null));
        Record record = new Record();

        ApiExtractException exception = Assertions.assertThrows(ApiExtractException.class,
                () -> SinkRecordNormalizer.normalize(List.of(record), sink, "Paimon"));

        Assertions.assertEquals("Paimon required column is empty: product_detail_id_name", exception.getMessage());
    }

    @Test
    public void normalizeShouldFormatDateColumn() {
        SinkConfig sink = new SinkConfig();
        ColumnConfig dayPt = column("day_pt", false, null);
        dayPt.type = "DATE";
        dayPt.format = "MM/dd/yyyy HH:mm:ss->yyyy-MM-dd";
        sink.columns.add(dayPt);
        Record record = new Record();
        record.put("day_pt", "05/21/2026 13:37:46");

        List<Record> records = SinkRecordNormalizer.normalize(List.of(record), sink, "Paimon");

        Assertions.assertEquals("2026-05-21", records.get(0).get("day_pt"));
    }

    private ColumnConfig column(String name, boolean nullable, Object defaultValue) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.nullable = nullable;
        column.defaultValue = defaultValue;
        return column;
    }
}

package com.datafusion.plugin.kafka.json.sink;

import org.apache.paimon.types.DataTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Paimon 字段值转换器单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class PaimonValueConverterTest {

    /**
     * 非 true/false 的布尔字符串不应静默转成 false.
     */
    @Test
    void shouldRejectInvalidBooleanText() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> PaimonValueConverter.convert("yes", DataTypes.BOOLEAN()));
    }

    /**
     * true/false 字符串可以转换为布尔值.
     */
    @Test
    void shouldConvertBooleanText() {
        Assertions.assertEquals(true, PaimonValueConverter.convert("true", DataTypes.BOOLEAN()));
        Assertions.assertEquals(false, PaimonValueConverter.convert("false", DataTypes.BOOLEAN()));
    }
}

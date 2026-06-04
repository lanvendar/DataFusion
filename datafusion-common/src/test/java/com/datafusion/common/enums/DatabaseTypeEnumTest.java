package com.datafusion.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTypeEnumTest {

    @Test
    void fromStringShouldAcceptEnumNameIgnoringCase() {
        assertEquals(DatabaseTypeEnum.POSTGRES, DatabaseTypeEnum.fromString("POSTGRES"));
        assertEquals(DatabaseTypeEnum.POSTGRES, DatabaseTypeEnum.fromString("postgres"));
    }

    @Test
    void fromStringShouldRejectUnknownDatabaseType() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseTypeEnum.fromString("POSTGRESQL"));
    }
}

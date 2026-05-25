package com.datafusion.common.type;

import com.datafusion.common.enums.DatabaseTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * TypeInfoConverterBuilder单元测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@Slf4j
public class TypeInfoConverterBuilderTest {
    @Test
    void formatter() {
        TypeInfo t1 = TypeInfoManager.parse(DatabaseTypeEnum.POSTGRES, "int");
        log.info("t1:{}", t1);
        TypeInfo t2 = TypeInfoManager.parse(DatabaseTypeEnum.POSTGRES, "DOUBLE PRECISION");
        log.info("t2:{}", t2);
    }
    
    @Test
    void formatter2() {
        TypeInfo t1 = TypeInfoManager.parse(DatabaseTypeEnum.MAXCOMPUTE, "Map<String,Map<String,String>");
        log.info("t1:{}", t1);
    }
    
    @Test
    void converterSelf() {
        TypeInfo t1 = TypeInfoManager.convert(DatabaseTypeEnum.POSTGRES, "int4", DatabaseTypeEnum.POSTGRES);
        log.info("t1:{}", t1);
        TypeInfo t2 = TypeInfoManager.convert(DatabaseTypeEnum.POSTGRES, "timestamp", DatabaseTypeEnum.POSTGRES);
        log.info("t2:{}", t2);
        TypeInfo t3 = TypeInfoManager.convert(DatabaseTypeEnum.POSTGRES, "DOUBLE PRECISION", DatabaseTypeEnum.POSTGRES);
        log.info("t3:{}", t3);
    }
    
    @Test
    void converterSwitch() {
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.POSTGRES);
        TypeInfoConverter converter = TypeInfoManager.getConverter(DatabaseTypeEnum.POSTGRES, DatabaseTypeEnum.STARROCKS);
        TypeInfo typeInfo = converter.convertTypeInfo(parser.parse("uuid"));
        TypeInfo typeInfo2 = converter.convertTypeInfo(parser.parse("timestamp"));
        log.info("typeInfo:{}", typeInfo);
        log.info("typeInfo2:{}", typeInfo2);
    }
    
    @Test
    void converterSwitch2() {
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.STARROCKS);
        TypeInfoConverter converter = TypeInfoManager.getConverter(DatabaseTypeEnum.STARROCKS, DatabaseTypeEnum.POSTGRES);
        TypeInfo typeInfo = converter.convertTypeInfo(parser.parse("datetime(12)"));
        log.info("typeInfo:{}", typeInfo);
    }
}

package com.datafusion.common.type;

import com.datafusion.common.enums.DatabaseTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * pg转starrocks类型测试.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/27
 * @since 2025/3/27
 */
@Slf4j
class PostgresTypeTest {
    private Map<String, DataType> fieldTypes;
    
    @BeforeEach
    public void init() {
        Type definition = TypeInfoManager.getDefinition(DatabaseTypeEnum.POSTGRES);
        fieldTypes = definition.getFieldType();
        for (String fieldType : fieldTypes.keySet()) {
            log.info("pg类型:{},java类型:{}", fieldType, fieldTypes.get(fieldType));
        }
        log.info("________________________________________________________");
    }
    
    @Test
    void formatter() {
        for (String fieldType : fieldTypes.keySet()) {
            TypeInfo typeInfo = TypeInfoManager.parse(DatabaseTypeEnum.POSTGRES, fieldType);
            log.info("原始类型:{} --> 格式化类型:{}", fieldType, typeInfo.getFullFieldType());
        }
    }
    
    @Test
    void converterSelf() {
        for (String fieldType : fieldTypes.keySet()) {
            TypeInfo typeInfo =  TypeInfoManager.convert(DatabaseTypeEnum.POSTGRES, fieldType, DatabaseTypeEnum.POSTGRES);
            log.info("原始类型:{} --> 自转类型:{}", fieldType, typeInfo.getFullFieldType());
        }
    }
    
    @Test
    void converterStarrocks() {
        for (String fieldType : fieldTypes.keySet()) {
            TypeInfo typeInfo =  TypeInfoManager.convert(DatabaseTypeEnum.POSTGRES, fieldType, DatabaseTypeEnum.STARROCKS);
            log.info("POSTGRES类型:{} --> STARROCKS类型:{}", fieldType, typeInfo.getFullFieldType());
        }
    }
}

package com.datafusion.common.type;

import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库类型管理类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
public class TypeInfoManager {
    
    /**
     * 缓存所有通过 SPI 加载的 Type 定义.
     */
    private static final Map<DatabaseTypeEnum, Type> TYPE_DEFINITIONS;
    
    //
    
    /**
     * 缓存解析器，因为解析器是无状态且与特定数据库绑定的.
     */
    private static final Map<DatabaseTypeEnum, TypeInfoParser> PARSER_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 缓存转换器，转换器缓存的 Key 将是 "source->target" 或 "db->db".
     */
    private static final Map<String, TypeInfoConverter> CONVERTER_CACHE = new ConcurrentHashMap<>();
    
    static {
        // 使用 ServiceLoader 在静态块中加载所有 Type 实现
        TYPE_DEFINITIONS = ServiceLoader.load(Type.class).stream().map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableMap(Type::getSupportedDatabase, Function.identity()));
    }
    
    /**
     * 私有构造函数，防止实例化.
     */
    private TypeInfoManager() {
    }
    
    /**
     * 获取指定数据库底层的 Type 定义实现.
     *
     * @param dbType 数据库类型枚举
     * @return 对应的 Type 接口实现
     * @throws IllegalArgumentException 如果不支持该数据库类型
     */
    public static Type getDefinition(DatabaseTypeEnum dbType) {
        Type type = TYPE_DEFINITIONS.get(dbType);
        if (type == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
        return type;
    }
    
    /**
     * 将来自特定数据库的原始类型字符串解析为标准化的 TypeInfo 对象.
     *
     * @param dbType 原始类型字符串所属的数据库方言。
     * @return 一个标准化的 TypeInfo 对象。
     */
    public static TypeInfoParser getParser(DatabaseTypeEnum dbType) {
        return PARSER_CACHE.computeIfAbsent(dbType, key -> new TypeInfoParserDefault(getDefinition(key)));
    }
    
    /**
     * 将来自特定数据库的原始类型字符串解析为标准化的 TypeInfo 对象,并保持原始类型.
     * 注:标准类型转换请使用 convert 方法,并设置两个一样的数据库.
     *
     * @param dbType        原始类型字符串所属的数据库方言
     * @param fullFieldType 原始类型字符串,例如 "VARCHAR(255)"
     * @return 一个标准化的 TypeInfo 对象
     */
    public static TypeInfo parse(DatabaseTypeEnum dbType, String fullFieldType) {
        TypeInfoParser parser = getParser(dbType);
        return parser.parse(fullFieldType);
    }
    
    /**
     * 将来自特定数据库的原始类型字符串解析为标准化的 TypeInfo 对象,并保持原始类型.
     * 注:标准类型转换请使用 convert 方法,并设置两个一样的数据库.
     *
     * @param dbType    原始类型字符串所属的数据库方言
     * @param fieldType 原始类型字符串
     * @param num       长度,精度或刻度的有序数组
     * @return 一个标准化的 TypeInfo 对象
     */
    public static TypeInfo parse(DatabaseTypeEnum dbType, String fieldType, Integer... num) {
        TypeInfoParser parser = getParser(dbType);
        if (num.length == 1) {
            return parser.parse(fieldType, num[0]);
        } else if (num.length == 2) {
            return parser.parse(fieldType, num[0], num[1]);
        } else if (num.length == 3) {
            return parser.parse(fieldType, num[0], num[1], num[2]);
        } else {
            throw new CommonException("长度,精度或刻度的有序数组,最大长度为3.");
        }
    }
    
    /**
     * 获取一个 TypeInfo 转换器.
     *
     * @param sourceDbType 源数据库类型.
     * @param targetDbType 目标数据库类型.
     * @return 对应的 TypeInfoConverter 实现.
     */
    public static TypeInfoConverter getConverter(DatabaseTypeEnum sourceDbType, DatabaseTypeEnum targetDbType) {
        String cacheKey = sourceDbType.name() + SystemConstant.MIDDLE_LINE + targetDbType.name();
        
        return CONVERTER_CACHE.computeIfAbsent(cacheKey, key -> {
            if (sourceDbType == targetDbType) {
                // 自转换
                return new TypeInfoConvertSelf(getDefinition(sourceDbType));
            } else {
                // 跨库转换
                return new TypeInfoConvertSwitch(getDefinition(sourceDbType), getDefinition(targetDbType));
            }
        });
    }
    
    /**
     * 获取一个类型转换器.
     * - 如果源和目标数据库相同，返回一个用于库内标准化（自转换）的转换器.
     * - 如果源和目标数据库不同，返回一个用于跨库转换的转换器.
     *
     * @param sourceDbType   源数据库类型.
     * @param sourceTypeInfo 源数据库类型对应的 TypeInfo 对象.
     * @param targetDbType   目标数据库类型.
     * @return 对应的 TypeInfoConverter 实现.
     */
    public static TypeInfo convert(DatabaseTypeEnum sourceDbType, TypeInfo sourceTypeInfo, DatabaseTypeEnum targetDbType) {
        TypeInfoConverter converter = getConverter(sourceDbType, targetDbType);
        return converter.convertTypeInfo(sourceTypeInfo);
    }
    
    /**
     * 一个便捷方法，直接将源数据库的类型字符串转换为目标数据库的 TypeInfo 对象, 封装了 "解析" 和 "转换" 两个步骤.
     *
     * @param sourceDbType        源数据库类型。
     * @param sourceFullFieldType 源数据库的原始类型字符串。
     * @param targetDbType        目标数据库类型。
     * @return 一个代表目标数据库类型的 TypeInfo 对象。
     */
    public static TypeInfo convert(DatabaseTypeEnum sourceDbType, String sourceFullFieldType, DatabaseTypeEnum targetDbType) {
        // 步骤 1: 解析源类型
        TypeInfo sourceTypeInfo = parse(sourceDbType, sourceFullFieldType);
        
        // 步骤 2: 获取相应的转换器并执行转换
        return convert(sourceDbType, sourceTypeInfo, targetDbType);
    }
}

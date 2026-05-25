package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.constant.SystemConstant;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认的 TypeInfoParser 实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
@Slf4j
public class TypeInfoParserDefault implements TypeInfoParser {
    /**
     * 匹配复杂类型，如 ARRAY, MAP 等.
     */
    protected static final Pattern COMPLEX_TYPE_PATTERN = Pattern.compile("(\\w+)\\s*<((?:[^<>]|<[^<>]*>)*)>");
    
    /**
     * 数据库类型定义.
     */
    private final Type dbTypeDefinition;
    
    /**
     * 构造函数，传入数据库类型定义.
     *
     * @param dbTypeDefinition 数据库类型定义.
     */
    public TypeInfoParserDefault(Type dbTypeDefinition) {
        this.dbTypeDefinition = dbTypeDefinition;
    }
    
    @Override
    public TypeInfo parse(String fieldType, Integer length, Integer precision, Integer scale) {
        DataType dataType = dbTypeDefinition.getFieldType().get(fieldType.toUpperCase());
        if (dataType == null) {
            log.warn("字段类型 [{}] 在当前数据库方言中不被支持，将使用默认类型。", fieldType);
            // 额外设置原始类型，便于调试
            return dbTypeDefinition.getDefaultType();
        }
        TypeInfo.TypeInfoBuilder builder = TypeInfo.builder()
                .dataType(dataType)
                .javaType(dbTypeDefinition.getJavaType(dataType))
                .fieldType(fieldType)
                .length(length)
                .precision(precision)
                .scale(scale);
        TypeInfo typeInfo = builder.build();
        typeInfo.setOriginalFieldType(typeInfo.getFullFieldType());
        return typeInfo;
    }
    
    @Override
    public TypeInfo parse(String fieldType, Integer precision, Integer scale) {
        return parse(fieldType, null, precision, scale);
    }
    
    @Override
    public TypeInfo parse(String fieldType, Integer length) {
        return parse(fieldType, length, null, null);
    }
    
    @Override
    public TypeInfo parse(String fullFieldType) {
        // 1. 使用父类方法解析出类型名称和(精度/长度, 刻度)
        Pair<String, Pair<Integer, Integer>> pair = analyseType(fullFieldType);
        String typeName = pair.getKey();
        
        // 2. 根据类型名称查找标准的 DataType
        DataType dataType = dbTypeDefinition.getFieldType().get(typeName);
        
        // 3. 如果找不到对应的 DataType，说明是不支持的类型，返回默认类型
        if (dataType == null) {
            log.warn("字段类型 [{}] 在当前数据库方言中不被支持，将使用默认类型。", fullFieldType);
            // 额外设置原始类型，便于调试
            return dbTypeDefinition.getDefaultType();
        }
        
        // 4. 构建 TypeInfo 对象
        TypeInfo.TypeInfoBuilder builder = TypeInfo.builder()
                .dataType(dataType)
                .javaType(dbTypeDefinition.getJavaType(dataType))
                .fieldType(typeName) // 使用解析出的、未经转换的原始类型名
                .originalFieldType(fullFieldType.trim()); // 始终保留原始完整类型
        
        // 5. 处理长度、精度和刻度
        Pair<Integer, Integer> dimensions = pair.getValue();
        if (dimensions != null) {
            Integer left = dimensions.getKey();   // 可能是长度或精度
            Integer right = dimensions.getValue(); // 可能是刻度
            
            // 根据 DataType 的签名（signatures）判断是长度还是精度/刻度
            // signatures == 1 -> 只有长度
            if (dataType.getSignatures() == 1 && left > 0) {
                // 应用数据库特定的长度规则
                builder.length(dbTypeDefinition.getLength(dataType, left));
            } else if (dataType.getSignatures() > 1) {
                // signatures > 1 -> 有精度/刻度,应用数据库特定的精度和刻度规则
                Pair<Integer, Integer> precisionAndScale = dbTypeDefinition.getPrecisionAndScale(dataType, left, right);
                builder.precision(precisionAndScale.getKey());
                builder.scale(precisionAndScale.getValue());
            }
        }
        
        return builder.build();
    }
    
    /**
     * 解析字段类型和长度.
     * 如果无长度,精度,小数,则 Pair 的 value 为 null .
     * 如果只有长度, 刻度为 0 .
     * 如果有精度,小数, 则 Pair 的 value 为 精度,小数 .
     *
     * @param fullFieldType 数据库字段类型，例如 "VARCHAR(255)", "DECIMAL(38, 10)", "INT"
     * @return {@code Pair<String, Pair<String, String>>,<字段类型,<精度/长度,刻度>>}
     */
    protected Pair<String, Pair<Integer, Integer>> analyseType(String fullFieldType) {
        if (fullFieldType == null || fullFieldType.trim().isEmpty()) {
            throw new IllegalArgumentException("Field type cannot be null or empty.");
        }
        // 统一转换为大写，并去除首尾空格
        String normalizedType = fullFieldType.trim().toUpperCase();
        
        Matcher complexMatcher = COMPLEX_TYPE_PATTERN.matcher(normalizedType);
        if (complexMatcher.find()) {
            // 提取出 ARRAY, MAP 等
            String mainType = complexMatcher.group(1);
            // 提取出 <...> 内部的内容
            String subTypes = complexMatcher.group(2);
            log.debug("识别到复杂类型: [{}], 主类型: [{}], 子类型: [{}]", normalizedType, mainType, subTypes);
            // 对于复杂类型，当前设计只关心主类型，不处理长度和精度
            return Pair.of(mainType, null);
        }
        
        int openParen = normalizedType.indexOf(SystemConstant.LEFT_PARENTHESIS);
        
        // Case1:类型没有括号，如 "INT", "TEXT"
        if (openParen == -1) {
            return Pair.of(normalizedType, null);
        }
        
        int closeParen = normalizedType.lastIndexOf(SystemConstant.RIGHT_PARENTHESIS);
        // 括号不匹配或格式错误
        if (closeParen < openParen) {
            // 认为括号是类型名称的一部分，例如某些数据库的特殊类型
            return Pair.of(normalizedType, null);
        }
        
        // Case2:类型有括号，如 "VARCHAR(255)", "DECIMAL(38, 10)"
        String typeName = normalizedType.substring(0, openParen).trim();
        
        // 提取括号内的内容
        String content = normalizedType.substring(openParen + 1, closeParen).trim();
        if (content.isEmpty()) {
            // 处理 "VARCHAR()" 这样的空括号情况
            return Pair.of(typeName, Pair.of(0, 0));
        }
        
        // 使用逗号分割括号内的内容
        String[] parts = content.split(SystemConstant.COMMA);
        try {
            if (parts.length == 1) {
                // Case2a: 只有一个参数，如 "VARCHAR(255)"
                Integer lengthOrPrecision = Integer.parseInt(parts[0].trim());
                // 约定：对于单个参数，我们将其视为精度/长度，刻度为0
                return Pair.of(typeName, Pair.of(lengthOrPrecision, 0));
            } else if (parts.length == 2) {
                // Case2b: 有两个参数，如 "DECIMAL(38, 10)"
                Integer precision = Integer.parseInt(parts[0].trim());
                Integer scale = Integer.parseInt(parts[1].trim());
                return Pair.of(typeName, Pair.of(precision, scale));
            } else {
                // 参数数量超过2个，格式不支持
                // 降级处理，只返回类型名称
                log.warn("Unsupported format with more than 2 parameters in parentheses: {}", fullFieldType);
                return Pair.of(typeName, null);
            }
        } catch (NumberFormatException e) {
            // 括号内不是数字，格式错误，例如 VARCHAR(MAX)
            // 降级处理，只返回类型名称
            log.warn("Non-numeric content inside parentheses for type: {}", fullFieldType);
            return Pair.of(typeName, null);
        }
    }
}

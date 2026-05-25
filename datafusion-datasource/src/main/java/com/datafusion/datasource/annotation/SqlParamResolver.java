package com.datafusion.datasource.annotation;

import cn.hutool.core.bean.BeanUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.template.JFinalSqlBuilder;
import com.datafusion.datasource.model.ExecuteParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlParam参数解析器{@link SqlParam}.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/25
 * @since 2025/7/25
 */
@Slf4j
public class SqlParamResolver {
    
    /**
     * 解析单个 @SqlParam 注解的参数，并将其添加到目标参数Map中
     * 参数 `arg` 可以是 JFinal 模板引擎能处理的任何类型，
     * 包括基本类型、字符串、POJO、Map、集合（List、Set）和数组.
     *
     * @param paramName aSQL模板中的参数名 (来自 {@link SqlParam#value()})
     * @param arg       方法参数的实际值
     * @param paramsMap 正在为单次SQL执行构建的参数Map
     */
    public void resolve(String paramName, Object arg, Map<String, Object> paramsMap) {
        if (paramsMap.containsKey(paramName)) {
            // 抛出异常比覆盖值更安全，因为它很可能指示了开发者的一个错误。
            log.warn("发现重复的参数名 '{}'。现有值将被覆盖。请检查您的 @SqlParam 注解。", paramName);
        }
        paramsMap.put(paramName, arg);
    }
    
    /**
     * 根据单次或批量模式，适配并验证最终的SQL参数.
     *
     * @param executeParam 要填充的执行参数对象。
     * @param sqlParamMap  从 @SqlParam 注解收集的参数Map。
     * @param sqlParams    从 @SqlParams 注解获取的对象 (通常是 List)。
     */
    public void adaptFinalSqlParams(ExecuteParam executeParam, Map<String, Object> sqlParamMap, Object sqlParams) {
        boolean hasSingleParams = !sqlParamMap.isEmpty();
        boolean hasBatchParams = sqlParams != null;
        
        if (hasSingleParams && hasBatchParams) {
            // 当冲突时，@SqlParams 优先级更高
            log.warn("不能在同一个方法签名中同时使用 @SqlParam 和 @SqlParams 注解, @SqlParams 会使 @SqlParam 失效!");
        }
        
        // 1.批量sql模式
        if (executeParam.isBatch()) {
            if (hasBatchParams) {
                executeParam.setParams(convertToMapList(sqlParams));
            } else {
                // 批量模式下，如果没有 @SqlParams，则参数为空
                executeParam.setParams(Collections.emptyList());
            }
        } else {
            // 2.单条sql模式
            if (hasBatchParams) {
                // 单次执行，但参数是 @SqlParams，这对应于特殊的批量INSERT场景
                Map<String, Object> finalParam = new HashMap<>();
                // 使用一个明确的,让JFinalSqlBuilder知道如何处理,内部约定的key="_symbol_insert_rows_"
                finalParam.put(JFinalSqlBuilder.SYMBOL_INSERT_ROWS, convertToMapList(sqlParams));
                executeParam.setParam(finalParam);
            } else {
                // 正常的单次执行
                executeParam.setParam(sqlParamMap);
            }
        }
    }
    
    /**
     * 主入口方法，使用 instanceof 进行类型分派.
     *
     * @param input 可能是 {@code List<Map<String, Object>>, List<T>, Set<T>, T[]}
     * @return 转换后的 {@code List<Map<String, Object>>}
     */
    private List<Map<String, Object>> convertToMapList(Object input) {
        if (input == null) {
            return Collections.emptyList();
        }
        
        // 如果第一个元素是 Map，则返回原始列表
        if (input instanceof List) {
            List<?> list = (List<?>) input;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) list;
            }
        }
        
        // 针对最常见的 Collection 类型进行分派
        if (input instanceof Collection) {
            return convertCollectionToMapList((Collection<?>) input);
        }
        
        // 针对数组类型
        if (input.getClass().isArray()) {
            // 注意：这里只处理对象数组。基本类型数组需要额外代码，但通常不作为批量参数。
            if (input instanceof Object[]) {
                return convertArrayToMapList((Object[]) input);
            }
            // 如果需要支持基本类型数组，可以在这里添加逻辑
            // e.g., if (input instanceof int[]) { ... }
        }
        
        throw new CommonException("@SqlParams 必须是一个集合存在形式,支持[List<Map<String, Object>>, List<T>, Set<T>, T[]的形式类型]"
                + String.format("当前类型{ %s }", input.getClass().getName()));
    }
    
    /**
     * 将 Collection {@code List<POJO>, Set<POJO>} 转换为 Map 列表.
     *
     * @param collection 待转换的 Collection
     * @return Map 列表
     */
    private List<Map<String, Object>> convertCollectionToMapList(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 优化1: 预分配 ArrayList 容量
        List<Map<String, Object>> mapList = new ArrayList<>(collection.size());
        
        // 优化2: 使用 for-each 循环代替 Stream API
        for (Object element : collection) {
            mapList.add(beanToMap(element));
        }
        return mapList;
    }
    
    /**
     * 将对象数组 {@code User[]} 转换为 Map 列表.
     *
     * @param array 待转换的数组
     * @return Map 列表
     */
    private List<Map<String, Object>> convertArrayToMapList(Object[] array) {
        if (array == null || array.length == 0) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> mapList = new ArrayList<>(array.length);
        for (Object element : array) {
            mapList.add(beanToMap(element));
        }
        return mapList;
    }
    
    /**
     * 核心转换逻辑：将单个元素 POJO 或 Map 转换为 Map.
     *
     * @param element 单个元素
     * @return 转换后的 Map
     */
    private Map<String, Object> beanToMap(Object element) {
        if (element == null) {
            // 确保返回不可变的空Map，更安全
            return Collections.emptyMap();
        }
        // 优化：如果元素本身就是 Map, 直接类型转换返回，避免不必要的 beanToMap 操作
        if (element instanceof Map) {
            return (Map<String, Object>) element;
        }
        return BeanUtil.beanToMap(element);
    }
}

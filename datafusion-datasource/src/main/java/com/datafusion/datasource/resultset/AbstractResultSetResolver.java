package com.datafusion.datasource.resultset;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;
import lombok.extern.slf4j.Slf4j;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 抽象结果集解析器（模板方法模式实现）.
 * 定义了结果集解析的通用算法骨架,子类只需实现与具体数据源相关的抽象方法，
 *
 * @param <R> 结果集的泛型
 * @author lanvendar
 * @version 1.0.0, 2025/7/10
 * @since 2025/7/7
 */
@Slf4j
public abstract class AbstractResultSetResolver<R> implements ResultSetResolver<R> {
    
    /**
     * 反射拼装字符串,用于 map 对象反射.
     */
    private static final String PUT_PREFIX = "put";
    
    /**
     * 反射拼装字符串,用于 bean 对象反射.
     */
    private static final String SET_PREFIX = "set";
    
    /**
     * 集合拼装字符串,用于 list 集合对象反射.
     */
    private static final String ADD_PREFIX = "add";
    
    /**
     * 类型处理器工厂.
     */
    protected TypeHandlerFactory<? extends TypeHandler<R, ?>> typeHandlerFactory;
    
    /**
     * 提供一个 getter，用于延迟加载 TypeHandlerFactory.
     * 如果 factory 尚未初始化，就调用一个抽象方法来创建它。
     *
     * @return 类型处理器工厂
     */
    protected TypeHandlerFactory<? extends TypeHandler<R, ?>> getTypeHandlerFactory() {
        if (this.typeHandlerFactory == null) {
            this.typeHandlerFactory = createTypeHandlerFactory();
        }
        return this.typeHandlerFactory;
    }
    
    @Override
    public Object getResultSet(R rs, Type type) {
        if (rs == null) {
            return null;
        }
        
        ReturnType returnType = parseReturnType(type);
        if (returnType == null) {
            return null;
        }
        
        try {
            if (isCollection(returnType.getRawClass()) || isArray(returnType.getRawClass())) {
                // 多行结果集
                return handleMultiple(rs, returnType);
            } else {
                // 单行结果集
                return handleSingle(rs, returnType);
            }
        } catch (Exception e) {
            log.error("结果集解析失败, 返回类型: {}", type.getTypeName(), e);
            throw new CommonException("结果集解析时发生严重错误", e);
        }
    }
    
    /**
     * 为多行结果集（集合或数组）赋值.
     *
     * @param rs         数据源结果集
     * @param returnType 返回对象类型,如 @{@code List<User>, User[]}
     * @return 解析后的对象 (Collection 或 Array)
     * @throws Exception 如果操作失败
     */
    private Object handleMultiple(R rs, ReturnType returnType) throws Exception {
        final Class<?> rawClass = returnType.getRawClass();
        
        if (isArray(rawClass)) {
            //数组处理
            Class<?> componentType = rawClass.getComponentType();
            ReturnType rowType = parseReturnType(componentType);
            
            // 1. 先将所有结果收集到临时的 List 中
            List<Object> resultsList = new ArrayList<>();
            List<ResultSetMapping<R>> mappings = getMappings(rs, rowType);
            while (hasNext(rs)) {
                Object rowObject = assignRecord(rs, rowType, mappings);
                if (rowObject != null) {
                    resultsList.add(rowObject);
                }
            }
            
            // 2. 使用更优雅的方式将 List 转换为目标类型的数组
            Object[] template = (Object[]) Array.newInstance(componentType, 0);
            return resultsList.toArray(template);
            
        } else if (isCollection(rawClass)) {
            // 集合处理
            if (returnType.getGenericParameters().isEmpty()) {
                throw new CommonException("无法确定集合的泛型类型: " + rawClass.getName());
            }
            ReturnType rowType = returnType.getGenericParameters().get(0);
            
            // 1. 直接创建最终的集合实例
            Collection<Object> collectionInstance = (Collection<Object>) createInstance(rawClass);
            
            // 2. 直接在循环中向最终集合添加元素，无需中间 List
            List<ResultSetMapping<R>> mappings = getMappings(rs, rowType);
            while (hasNext(rs)) {
                Object rowObject = assignRecord(rs, rowType, mappings);
                if (rowObject != null) {
                    collectionInstance.add(rowObject);
                }
            }
            return collectionInstance;
            
        } else {
            // 此路径理论上不可达
            throw new CommonException("handleMultiple 仅支持集合或数组类型, 收到类型: " + rawClass.getName());
        }
    }
    
    /**
     * 为单行结果集赋值.
     *
     * @param rs         数据源结果集
     * @param returnType 返回对象类型
     * @return 解析后的对象
     * @throws Exception 如果操作失败
     */
    private Object handleSingle(R rs, ReturnType returnType) throws Exception {
        // 移动到第一行（或检查是否存在）
        if (hasNext(rs)) {
            List<ResultSetMapping<R>> mappings = getMappings(rs, returnType);
            Object result = assignRecord(rs, returnType, mappings);
            
            // 警告：如果还有更多行，但期望的是单行
            if (hasNext(rs)) {
                log.warn("结果集包含多行,只返回第一行.");
            }
            return result;
        }
        log.warn("结果集为空");
        return null;
    }
    
    /**
     * 它定义了为任何类型的单行记录赋值的通用算法.
     *
     * @param rs         数据源结果集（现在仅作为上下文传递）
     * @param returnType 映射对象类型
     * @param mappings   列映射关系
     * @return 解析后的对象
     * @throws Exception 如果操作失败
     */
    protected Object assignRecord(R rs, ReturnType returnType, List<ResultSetMapping<R>> mappings) throws Exception {
        Class<?> rawClass = returnType.getRawClass();
        
        if (isSimpleType(rawClass)) {
            // 警告：如果结果集有多列，只取第一列
            List<String> columnLabels = getColumnLabels(rs);
            if (columnLabels.size() > 1) {
                log.warn("Query returned {} columns, but a simple type [{}] was expected. Only the first column will be used.",
                        columnLabels.size(), rawClass.getSimpleName());
            }
            // 从工厂获取正确的处理器
            TypeHandler<R, ?> typeHandler = this.getTypeHandlerFactory().getHandler(rawClass);
            // 简单类型假定只有一列
            return typeHandler.getResult(rs, 1);
        }
        
        // 为 Bean, Map, Collection 创建实例
        Object instance = createInstance(rawClass);
        for (ResultSetMapping<R> mapping : mappings) {
            try {
                // 从 mapping 中获取预先准备好的 TypeHandler
                TypeHandler<R, ?> typeHandler = mapping.getTypeHandler();
                // 调用 getResult 从数据源获取值
                Object val = typeHandler.getResult(rs, mapping.getColumnLabel());
                // 调用 assignField 将值赋给实例
                assignField(mapping, instance, val);
            } catch (Exception e) {
                log.error("为属性 '{}' 从列 '{}' 赋值时失败", mapping.getSetterMethod().getName(), mapping.getColumnLabel(), e);
                // 根据业务决定是跳过还是抛出异常
            }
        }
        return instance;
    }
    // region 抽象方法,子类必须实现
    
    /**
     * 新的抽象方法，强制子类提供创建其特定工厂的逻辑.
     *
     * @return 一个具体的 TypeHandlerFactory 实例
     */
    protected abstract TypeHandlerFactory<? extends TypeHandler<R, ?>> createTypeHandlerFactory();
    
    /**
     * 从结果集中获取所有列的标签（或字段名）.
     *
     * @param rs 数据源结果集
     * @return 列标签的列表
     * @throws Exception 如果操作失败
     */
    protected abstract List<String> getColumnLabels(R rs) throws Exception;
    
    /**
     * 检查结果集是否还有下一条记录，并可能需要移动指针.
     * 对于JDBC ResultSet，这将调用 rs.next()。
     * 对于迭代器，这将调用 iterator.hasNext() 和 iterator.next()。
     *
     * @param rs 数据源结果集
     * @return 如果存在下一条记录则返回 true
     * @throws Exception 如果操作失败
     */
    protected abstract boolean hasNext(R rs) throws Exception;
    //endregion
    //region 工具方法
    
    /**
     * 获取列映射关系.
     *
     * @param rs         数据源结果集
     * @param returnType 行对象类型
     * @return 列映射关系列表
     * @throws Exception 如果操作失败
     */
    protected List<ResultSetMapping<R>> getMappings(R rs, ReturnType returnType) throws Exception {
        List<String> columnLabels = getColumnLabels(rs);
        Class<?> rawClass = returnType.getRawClass();
        
        if (isBean(rawClass)) {
            return getMappingForBean(columnLabels, returnType);
        }
        if (isMap(rawClass)) {
            return getMappingForMap(columnLabels, returnType);
        }
        if (isCollection(rawClass)) {
            return getMappingForCollection(columnLabels, returnType);
        }
        if (isSimpleType(rawClass)) {
            // 简单类型不需要复杂映射，返回空列表
            return Collections.emptyList();
        }
        throw new CommonException("不支持的行映射类型: " + rawClass.getName());
    }
    
    /**
     * 赋值对象实例.
     *
     * @param mapping 映射关系
     * @param obj     返回对象实例
     * @param val     值
     */
    protected void assignField(ResultSetMapping mapping, Object obj, Object val) {
        try {
            Method method = mapping.getSetterMethod();
            if (method.getName().startsWith(SET_PREFIX)) {
                method.invoke(obj, val);
            } else if (method.getName().startsWith(PUT_PREFIX)) {
                method.invoke(obj, mapping.getColumnLabel(), val);
            }
        } catch (Exception e) {
            throw new CommonException("通过反射赋值失败: " + mapping, e);
        }
    }
    
    /**
     * 创建返回对象实例.
     *
     * @param clazz 返回对象类型
     * @return 返回对象实例
     */
    protected Object createInstance(Class<?> clazz) {
        // 1. 解析出真正需要实例化的具体类
        Class<?> classToCreate = resolveInterface(clazz);
        // 2. 使用 Hutool 的 ReflectUtil 进行实例化
        return ReflectUtil.newInstance(classToCreate);
    }
    
    /**
     * 如果传入的类型不是接口，则直接返回原类型.
     *
     * @param type 可能为接口的类型
     * @return 一个具体的、可实例化的类
     */
    private Class<?> resolveInterface(Class<?> type) {
        if (type == List.class || type == Collection.class) {
            // 如果是 List 或 Collection 接口，默认使用 ArrayList
            return ArrayList.class;
        } else if (type == Map.class) {
            // 如果是 Map 接口，默认使用 HashMap
            return HashMap.class;
        } else if (type == Set.class) {
            // 如果是 Set 接口，默认使用 HashSet
            return HashSet.class;
        } else if (type == SortedSet.class) {
            // 更具体的Set，默认使用 TreeSet
            return TreeSet.class;
        } else {
            // 如果是其他类型或具体类,直接使用它自己
            return type;
        }
    }
    
    /**
     * 解析返回对象类型.
     *
     * @param type 返回对象类型
     * @return 解析后的返回对象类型
     */
    private ReturnType parseReturnType(Type type) {
        return ReturnTypeParser.parseType(type);
    }
    
    /**
     * 判断一个类是否为数组类.
     *
     * @param clazz class
     * @return true/false
     */
    protected boolean isArray(Class<?> clazz) {
        return clazz.isArray();
    }
    
    /**
     * 判断一个类是否为集合类.
     *
     * @param clazz class
     * @return true/false
     */
    protected boolean isCollection(Class<?> clazz) {
        return Collection.class.isAssignableFrom(clazz);
    }
    
    /**
     * 判断一个类是否为Map类.
     *
     * @param clazz class
     * @return true/false
     */
    protected boolean isMap(Class<?> clazz) {
        return Map.class.isAssignableFrom(clazz);
    }
    
    /**
     * 判断一个类是否为普通类, 参考hutool.
     *
     * @param clazz class
     * @return true/false
     */
    protected boolean isBean(Class<?> clazz) {
        return !isSimpleType(clazz) && !isCollection(clazz) && !isMap(clazz) && !clazz.isInterface() && !clazz.isArray();
    }
    
    /**
     * 判断给定的类是否为简单类型.
     *
     * @param clazz 要判断的类
     * @return 如果给定的类为简单类型，则返回true；否则返回false
     */
    protected boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() // 原始类型
                || CharSequence.class.isAssignableFrom(clazz) // CharSequence 涵盖 String, StringBuilder等
                || Number.class.isAssignableFrom(clazz) // Number 涵盖 Integer, Long, Double, BigDecimal等
                || Date.class.isAssignableFrom(clazz) // java.util.Date及其子类
                || Temporal.class.isAssignableFrom(clazz) // java.time 中的时间/日期类型
                || Boolean.class.isAssignableFrom(clazz) // 布尔类型
                || UUID.class.isAssignableFrom(clazz) // UUID
                || clazz.isEnum(); // 枚举类型
    }
    
    /**
     * 获取 Collection 类型的映射关系.
     * 这种映射比较特殊，它将所有列共享一个 "add" 方法。
     *
     * @param returnType 行类型，如 {@code List<String>, Set<String>}
     * @return 集合的映射关系
     */
    protected List<ResultSetMapping<R>> getMappingForCollection(List<String> columnLabels, ReturnType returnType) {
        Method addMethod = ReflectUtil.getMethodByName(returnType.getRawClass(), ADD_PREFIX);
        if (addMethod == null) {
            throw new CommonException("在集合类型 " + returnType.getRawClass().getName() + " 中未找到 'add' 方法");
        }
        // 默认Object类型
        Class<?> elementType = Object.class;
        if (!returnType.getGenericParameters().isEmpty()) {
            elementType = returnType.getGenericParameters().get(0).getRawClass();
        }
        
        TypeHandler<R, ?> typeHandler = this.getTypeHandlerFactory().getHandler(elementType);
        
        List<ResultSetMapping<R>> mappings = new ArrayList<>();
        for (String columnLabel : columnLabels) {
            // 为每一列都创建一个映射，目标方法都是 add
            mappings.add(new ResultSetMapping<>(columnLabel, addMethod, typeHandler));
        }
        return mappings;
    }
    
    /**
     * 获取Map类型的映射关系.
     *
     * @param columnLabels 列标签列表
     * @param returnType   原始类
     * @return Map类型的映射关系
     */
    protected List<ResultSetMapping<R>> getMappingForMap(List<String> columnLabels, ReturnType returnType) {
        List<ResultSetMapping<R>> mappings = new ArrayList<>();
        Method putMethod = ReflectUtil.getMethodByName(returnType.getRawClass(), PUT_PREFIX);
        if (putMethod == null) {
            throw new CommonException("在Map类型 " + returnType.getRawClass().getName() + " 中未找到 'put' 方法");
        }
        // 默认Object类型
        Class<?> elementType = Object.class;
        if (returnType.isGeneric() && returnType.getGenericParameters().size() > 1) {
            elementType = returnType.getGenericParameters().get(1).getRawClass();
        }
        
        TypeHandler<R, ?> typeHandler = this.getTypeHandlerFactory().getHandler(elementType);
        for (String columnLabel : columnLabels) {
            mappings.add(new ResultSetMapping<>(columnLabel, putMethod, typeHandler));
        }
        return mappings;
    }
    
    /**
     * 获取Bean类型的映射关系.
     *
     * @param columnLabels 列标签列表
     * @param returnType   原始类
     * @return Bean类型的映射关系
     */
    protected List<ResultSetMapping<R>> getMappingForBean(List<String> columnLabels, ReturnType returnType) {
        List<ResultSetMapping<R>> mappings = new ArrayList<>();
        Class<?> rawClass = returnType.getRawClass();
        Map<String, PropertyDescriptor> propertyDescriptorMap = getPropertyDescriptors(rawClass);
        
        for (String columnLabel : columnLabels) {
            String propertyName = StrUtil.toCamelCase(columnLabel.toLowerCase(Locale.ROOT));
            PropertyDescriptor pd = propertyDescriptorMap.get(propertyName);
            
            if (pd != null) {
                Method setter = pd.getWriteMethod();
                if (setter != null && setter.getName().startsWith(SET_PREFIX)) {
                    Class<?> parameterType = setter.getParameterTypes()[0];
                    TypeHandler<R, ?> typeHandler = this.getTypeHandlerFactory().getHandler(parameterType);
                    mappings.add(new ResultSetMapping<>(columnLabel, setter, typeHandler));
                }
            } else {
                log.trace("在类 {} 中未找到与列 '{}' 匹配的属性 '{}'，已跳过。", rawClass.getSimpleName(), columnLabel, propertyName);
            }
        }
        return mappings;
    }
    
    /**
     * 获取类的属性描述符.
     *
     * @param clazz 类
     * @return 属性描述符映射
     */
    private Map<String, PropertyDescriptor> getPropertyDescriptors(Class<?> clazz) {
        Map<String, PropertyDescriptor> map = new HashMap<>();
        try {
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(clazz, Object.class).getPropertyDescriptors();
            for (PropertyDescriptor descriptor : descriptors) {
                map.put(descriptor.getName(), descriptor);
            }
        } catch (IntrospectionException e) {
            log.error("获取类 {} 的属性描述符失败。", clazz.getName(), e);
        }
        return map;
    }
    //endregion
}
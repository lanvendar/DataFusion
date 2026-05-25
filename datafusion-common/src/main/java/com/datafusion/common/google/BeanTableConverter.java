package com.datafusion.common.google;

import com.datafusion.common.constant.SystemConstant;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体类转google的 bigtable 格式.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/9/13
 * @since 2024/9/13
 */
@Slf4j
public class BeanTableConverter {

    /**
     * 将对象转换为 Table[String, String, Object].
     *
     * @param obj        待转换的对象
     * @param primaryKey 主键字段名
     * @param <T>        转换后的 Table
     * @return 转换后的 Table
     */
    protected static <T> Table<String, String, Object> toTable(T obj, String... primaryKey) {
        if (obj == null || primaryKey == null || primaryKey.length == 0) {
            throw new IllegalArgumentException("primaryKey cannot be null or empty");
        }

        Table<String, String, Object> table = HashBasedTable.create();
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();

        try {
            StringBuilder primaryKeyBuilder = new StringBuilder();
            Map<String, Object> primaryKeyValues = new HashMap<>();

            // 获取所有主键字段的值
            for (String key : primaryKey) {
                Field field = clazz.getDeclaredField(key);
                field.setAccessible(true);
                Object value = field.get(obj);
                primaryKeyValues.put(key, value);
                //TODO 主键数据中应该避免"_"符号,如果存在,此处应转义
                primaryKeyBuilder.append(value).append(SystemConstant.UNDER_LINE);
            }

            String primaryKeyString = primaryKeyBuilder.toString();

            // 去掉最后一个下划线
            if (primaryKeyString.endsWith(SystemConstant.UNDER_LINE)) {
                primaryKeyString = primaryKeyString.substring(0, primaryKeyString.length() - 1);
            }

            // 将字段的值存储到 Table 中, 包含主键字段
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                table.put(primaryKeyString, field.getName(), value);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access field values", e);
        }

        return table;
    }

    /**
     * 将 Table[String, String, Object] 转换为对象.
     *
     * @param table         表格数据
     * @param clazz         目标对象的类
     * @param primaryKeyVal 主键值
     * @param <T>           目标对象的类型
     * @return 转换后的对象
     */
    protected static <T> T toBean(Table<String, String, Object> table, Class<T> clazz, String primaryKeyVal) {
        if (table == null || clazz == null || primaryKeyVal == null) {
            throw new IllegalArgumentException("Arguments cannot be null or empty");
        }

        try {
            // 按主键查询
            if (!table.containsRow(primaryKeyVal)) {
                return null;
            } else {
                Map<String, Object> row = table.row(primaryKeyVal);

                T obj = clazz.getDeclaredConstructor().newInstance();

                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String fieldName = entry.getKey();
                    Object fieldValue = entry.getValue();

                    Field field = findField(clazz, fieldName);
                    if (field != null) {
                        int modifiers = field.getModifiers();
                        // 跳过 static 或 final 字段,TODO 以后可能排除更多.
                        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                            continue;
                        }
                        field.setAccessible(true);
                        field.set(obj, fieldValue);
                    } else {
                        // 字段未找到，不进行赋值操作
                        log.warn("Field " + fieldName + " not found in class hierarchy of " + clazz.getName());
                    }
                }

                return obj;
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                 | InvocationTargetException e) {
            throw new RuntimeException("Failed to create object from table", e);
        }
    }

    /**
     * 在类中查找字段.
     *
     * @param clazz     类
     * @param fieldName 字段名
     * @return 字段对象
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 将主键值转换为字符串.
     *
     * @param primaryKeyVal 主键值数组
     * @return 主键值字符串
     */
    public static String getPrimaryKeyVal(String... primaryKeyVal) {
        return String.join(SystemConstant.UNDER_LINE, primaryKeyVal);
    }

    /**
     * 将对象添加到表格中.
     *
     * @param table      表格对象
     * @param obj        行记录数据
     * @param primaryKey 主键字段名
     */
    public static void addData(Table<String, String, Object> table, Object obj, String... primaryKey) {
        Table<String, String, Object> tempTable = toTable(obj, primaryKey);
        for (Map.Entry<String, Map<String, Object>> entry : tempTable.rowMap().entrySet()) {
            for (Map.Entry<String, Object> subEntry : entry.getValue().entrySet()) {
                table.put(entry.getKey(), subEntry.getKey(), subEntry.getValue());
            }
        }
    }

    /**
     * 从表格中删除数据.
     *
     * @param table         表格数据
     * @param primaryKeyVal 主键值
     */
    public static void removeRow(Table<String, String, Object> table, String primaryKeyVal) {
        table.row(primaryKeyVal).clear();
    }

    /**
     * 从表格中删除数据.
     *
     * @param table         表格数据
     * @param primaryKeyVal 主键值
     * @param fields        要删除的字段名
     */
    public static void removeFields(Table<String, String, Object> table, String primaryKeyVal, String... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields cannot be null or empty");
        }

        for (String field : fields) {
            table.remove(primaryKeyVal, field);
        }
    }

    /**
     * 更新表格中的数据.
     *
     * @param table         表格数据
     * @param obj           对象
     * @param primaryKeyVal 主键值
     * @param primaryKey    主键字段名
     */
    public static void updateData(Table<String, String, Object> table, Object obj, String primaryKeyVal, String... primaryKey) {
        Table<String, String, Object> tempTable = toTable(obj, primaryKey);
        Map<String, Object> row = tempTable.row(primaryKeyVal);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            table.put(primaryKeyVal, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 查询表格中的数据.
     *
     * @param table         表格数据
     * @param clazz         目标对象的类
     * @param primaryKeyVal 主键值
     * @param <T>           目标对象的类型
     * @return 目标对象
     */
    public static <T> T queryData(Table<String, String, Object> table, Class<T> clazz, String primaryKeyVal) {
        return toBean(table, clazz, primaryKeyVal);
    }

    /**
     * 根据一个字段查询表格中的数据.
     *
     * @param table       表格数据
     * @param clazz       目标对象的类
     * @param columnName  字段名
     * @param columnValue 字段值
     * @param <T>         目标对象的类型
     * @return 目标对象列表
     */
    public static <T> List<T> queryDataByOneColumn(Table<String, String, Object> table, Class<T> clazz, String columnName, String columnValue) {
        List<T> resultList = new ArrayList<>();
        Map<String, Object> columns = table.column(columnName);
        for (String rowKey : columns.keySet()) {
            if (columns.get(rowKey).equals(columnValue)) {
                resultList.add(queryData(table, clazz, rowKey));
            }
        }
        return !resultList.isEmpty() ? resultList : null;
    }

    /**
     * 查询 Table 中的所有数据并转换为对象列表.
     *
     * @param table 表格数据
     * @param clazz 目标对象的类
     * @param <T>   目标对象的类型
     * @return 转换后的对象列表
     */
    public static <T> List<T> queryAllData(Table<String, String, Object> table, Class<T> clazz) {
        List<T> resultList = new ArrayList<>();
        Set<String> rowKeySet = table.rowKeySet();
        for (String rowKey : rowKeySet) {
            T obj = queryData(table, clazz, rowKey);
            if (obj != null) {
                resultList.add(queryData(table, clazz, rowKey));
            }
        }
        return !resultList.isEmpty() ? resultList : null;
    }
}

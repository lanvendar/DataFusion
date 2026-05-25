package com.datafusion.manager.asset.common.service;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service 基础实现类，提供通用的业务逻辑方法.
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * public class UserServiceImpl extends BaseServiceImpl<UserMapper, UserEntity> {
 *
 *     public void batchSaveOrUpdateByUsername(List<UserEntity> userList) {
 *         this.batchSaveOrUpdateByUniqueField(
 *             userList,
 *             UserEntity::getUsername,      // 唯一字段
 *             UserEntity::getId,            // ID getter
 *             UserEntity::setId,            // ID setter
 *             entity -> UUID.randomUUID()   // ID 生成器
 *         );
 *     }
 * }
 * }
 * </pre>
 *
 * @param <M> Mapper 类型，必须继承 BaseMapper
 * @param <T> 实体类型
 * @author wei.bowen
 * @version 1.0.0, 2026/1/23
 * @since 2026-01-23
 */
@Slf4j
public abstract class BaseServiceImpl<M extends BaseMapper<T>, T>
        extends ServiceImpl<M, T> {

    /**
     * 根据指定的唯一字段批量保存或更新实体.
     *
     * @param entityList        实体列表，不能为 null
     * @param uniqueFieldGetter 唯一字段的 getter 方法引用（如 Entity::getName）
     * @param idGetter          主键 ID 的 getter 方法引用（如 Entity::getId）
     * @param idSetter          主键 ID 的 setter 方法（如 Entity::setId）
     * @param idGenerator       ID 生成器函数（当不存在时生成新 ID）
     * @param <U>               唯一字段的类型（如 String、Long 等）
     * @param <D>               主键 D 的类型（如 UUID、Long 等）
     * @return 保存或更新是否成功
     * @throws IllegalArgumentException 如果 entityList 为空或唯一字段值全为 null
     * @throws RuntimeException         如果批量保存失败
     */
    @Transactional(rollbackFor = Exception.class)
    public <U, D> boolean batchSaveOrUpdateByUniqueField(
            List<T> entityList,
            SFunction<T, U> uniqueFieldGetter,
            SFunction<T, D> idGetter,
            BiConsumer<T, D> idSetter,
            Function<T, D> idGenerator) {

        // 1. 参数校验
        if (CollectionUtil.isEmpty(entityList)) {
            log.warn("实体列表为空，跳过批量保存或更新操作");
            return true;
        }

        log.info("开始批量保存或更新，数量: {}", entityList.size());

        // 2. 提取所有唯一字段值
        Set<U> uniqueFieldValues = entityList.stream()
                .map(uniqueFieldGetter::apply)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uniqueFieldValues.isEmpty()) {
            log.warn("所有实体的唯一字段值为空，无法进行批量保存或更新");
            throw new IllegalArgumentException("所有实体的唯一字段值为空");
        }

        log.debug("提取到 {} 个唯一字段值", uniqueFieldValues.size());

        // 3. 批量查询已存在的记录（只查询 D 和唯一字段，优化性能）
        LambdaQueryWrapper<T> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(idGetter, uniqueFieldGetter)
                .in(uniqueFieldGetter, uniqueFieldValues);

        List<T> existingList = this.list(queryWrapper);

        // 4. 构建唯一字段值 -> D 的映射
        Map<U, D> existingIdMap = existingList.stream()
                .collect(Collectors.toMap(
                        uniqueFieldGetter::apply,
                        idGetter::apply,
                        (oldId, newId) -> {
                            // 如果有重复的唯一字段值（理论上不应该发生），保留第一个
                            log.warn("发现重复的唯一字段值，保留第一个 D: {}", oldId);
                            return oldId;
                        }
                ));

        log.debug("查询到数据库中已存在 {} 条记录", existingIdMap.size());

        // 5. 为每条数据设置正确的 D
        // 5. 分离需要插入和更新的实体
        List<T> toInsertList = new ArrayList<>();
        List<T> toUpdateList = new ArrayList<>();

        for (T entity : entityList) {
            U uniqueFieldValue = uniqueFieldGetter.apply(entity);

            // 跳过唯一字段值为 null 的实体
            if (uniqueFieldValue == null) {
                log.warn("实体的唯一字段值为 null，跳过处理: {}", entity);
                continue;
            }

            D existingId = existingIdMap.get(uniqueFieldValue);

            if (existingId != null) {
                // 场景 1：数据库中已存在该唯一字段值
                // 操作：使用数据库中的 D 替换实体的 D（后续执行 UPDATE）
                idSetter.accept(entity, existingId);
                toUpdateList.add(entity);

                if (log.isDebugEnabled()) {
                    log.debug("记录已存在，将执行更新 - 唯一字段值: {}, D: {}", uniqueFieldValue, existingId);
                }
            } else {
                // 场景 2：数据库中不存在该唯一字段值
                // 操作：确保实体有 D（如果没有则生成新 D），后续会执行 INSERT
                D currentId = idGetter.apply(entity);
                if (currentId == null) {
                    D newId = idGenerator.apply(entity);
                    idSetter.accept(entity, newId);

                    if (log.isDebugEnabled()) {
                        log.debug("记录不存在，生成新 D - 唯一字段值: {}, 新 D: {}", uniqueFieldValue, newId);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("记录不存在，使用已有 D - 唯一字段值: {}, D: {}", uniqueFieldValue, currentId);
                    }
                }
                toInsertList.add(entity);
            }
        }

        log.info("数据处理完成 - 预计插入: {} 条，预计更新: {} 条", toInsertList.size(), toUpdateList.size());

        // 6. 分别处理插入和更新
        boolean insertSuccess = true;
        boolean updateSuccess = true;

        // 6.1 批量插入新记录（插入所有字段）
        if (!toInsertList.isEmpty()) {
            insertSuccess = this.saveBatch(toInsertList);
            if (insertSuccess) {
                log.info("批量插入成功 - 插入: {} 条", toInsertList.size());
            } else {
                log.error("批量插入失败");
                throw new RuntimeException("批量插入失败");
            }
        }

        // 6.2 逐个更新已有记录（只更新非null字段）
        if (!toUpdateList.isEmpty()) {
            updateSuccess = updateBatchByIdSelectivelyWithNonNull(toUpdateList, idGetter);
            if (updateSuccess) {
                log.info("批量更新成功 - 更新: {} 条", toUpdateList.size());
            } else {
                log.error("批量更新失败");
                throw new RuntimeException("批量更新失败");
            }
        }

        boolean success = insertSuccess && updateSuccess;
        if (success) {
            log.info("批量保存或更新成功 - 插入: {} 条，更新: {} 条", toInsertList.size(), toUpdateList.size());
        }
        return success;
    }

    /**
     * 根据唯一字段批量保存或更新（带批次大小控制）.
     *
     * <p>
     * 当数据量特别大时（如 > 1000 条），可以分批次处理以避免内存溢出和数据库压力
     * </p>
     *
     * @param entityList        实体列表
     * @param uniqueFieldGetter 唯一字段 getter
     * @param idGetter          D getter
     * @param idSetter          D setter
     * @param idGenerator       D 生成器
     * @param batchSize         每批次处理的数量（建议 500-1000）
     * @param <U>               唯一字段类型
     * @param <D>               D 类型
     */
    @Transactional(rollbackFor = Exception.class)
    public <U, D> boolean batchSaveOrUpdateByUniqueFieldWithBatch(
            List<T> entityList,
            SFunction<T, U> uniqueFieldGetter,
            SFunction<T, D> idGetter,
            BiConsumer<T, D> idSetter,
            Function<T, D> idGenerator,
            int batchSize) {

        if (CollectionUtil.isEmpty(entityList)) {
            log.warn("实体列表为空，跳过批量保存或更新操作");
            return true;
        }

        log.info("开始分批保存或更新，总数: {}，批次大小: {}", entityList.size(), batchSize);

        // 分批处理
        int totalBatches = (int) Math.ceil((double) entityList.size() / batchSize);
        boolean res = false;
        for (int i = 0; i < totalBatches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min((i + 1) * batchSize, entityList.size());
            List<T> batchList = entityList.subList(fromIndex, toIndex);

            log.info("处理第 {}/{} 批，数量: {}", i + 1, totalBatches, batchList.size());

            // 调用基础方法处理每一批
            res = batchSaveOrUpdateByUniqueField(batchList, uniqueFieldGetter, idGetter, idSetter, idGenerator);
        }

        log.info("分批保存或更新完成，总批次: {}", totalBatches);
        return res;
    }

    /**
     * 根据多个字段组合批量保存或更新（支持可变数量的唯一字段）.
     *
     * <p>使用示例：</p>
     * <pre>
     * {@code
     * // 示例：根据 sourceUrn + targetUrn + edgeType 组合判断唯一性
     * this.batchSaveOrUpdateByMultipleFields(
     *     edgeList,
     *     Arrays.asList(
     *         AssetLineageEdgeEntity::getSourceUrn,
     *         AssetLineageEdgeEntity::getTargetUrn,
     *         AssetLineageEdgeEntity::getEdgeType
     *     ),
     *     AssetLineageEdgeEntity::getId,
     *     AssetLineageEdgeEntity::setId,
     *     entity -> UUD.randomUUD()
     * );
     * }
     * </pre>
     *
     * @param entityList         实体列表
     * @param uniqueFieldGetters 唯一字段的 getter 列表（支持多个字段）
     * @param idGetter           ID getter
     * @param idSetter           ID setter
     * @param idGenerator        ID 生成器
     * @param <D>                ID 类型
     * @return true 表示成功，false 表示失败
     */
    @Transactional(rollbackFor = Exception.class)
    public <D> boolean batchSaveOrUpdateByMultipleFields(
            List<T> entityList,
            List<SFunction<T, ?>> uniqueFieldGetters,  // 支持多个字段
            SFunction<T, D> idGetter,
            BiConsumer<T, D> idSetter,
            Function<T, D> idGenerator) {

        // 1. 参数校验
        if (CollectionUtil.isEmpty(entityList)) {
            log.warn("实体列表为空，跳过批量保存或更新操作");
            return true;
        }

        if (CollectionUtil.isEmpty(uniqueFieldGetters)) {
            log.error("唯一字段列表为空，无法进行批量保存或更新");
            throw new IllegalArgumentException("唯一字段列表不能为空");
        }

        log.info("开始批量保存或更新，数量: {}，唯一字段数量: {}", entityList.size(), uniqueFieldGetters.size());

        // 2. 构建查询条件：使用 OR 连接多个 AND 条件组
        // 查询逻辑：(field1=v1 AND field2=v2) OR (field1=v3 AND field2=v4) OR ...
        LambdaQueryWrapper<T> queryWrapper = new LambdaQueryWrapper<>();

        // 只查询 D 和唯一字段，优化性能
        queryWrapper.select(idGetter);
        for (SFunction<T, ?> getter : uniqueFieldGetters) {
            queryWrapper.select(getter);
        }

        // 为每条数据构建一个 AND 条件组，然后用 OR 连接
        queryWrapper.and(wrapper -> {
            boolean isFirst = true;
            for (T entity : entityList) {
                // 提取当前实体的所有唯一字段值
                List<Object> fieldValues = uniqueFieldGetters.stream()
                        .map(getter -> getter.apply(entity))
                        .collect(Collectors.toList());

                // 如果有任何字段为 null，跳过这条数据
                if (fieldValues.stream().anyMatch(Objects::isNull)) {
                    continue;
                }

                // 构建单条数据的 AND 条件：field1=v1 AND field2=v2 AND field3=v3
                if (isFirst) {
                    // 第一组条件不需要 or()
                    wrapper.nested(w -> buildAndCondition(w, uniqueFieldGetters, fieldValues));
                    isFirst = false;
                } else {
                    // 后续条件用 or() 连接
                    wrapper.or(w -> buildAndCondition(w, uniqueFieldGetters, fieldValues));
                }
            }
            //return wrapper;
        });

        // 3. 执行查询
        List<T> existingList = this.list(queryWrapper);
        log.debug("查询到数据库中已存在 {} 条记录", existingList.size());

        // 4. 构建复合键 -> D 的映射
        // 复合键格式：field1Value|field2Value|field3Value
        Map<String, D> existingIdMap = new HashMap<>();
        for (T existing : existingList) {
            String compositeKey = buildCompositeKey(existing, uniqueFieldGetters);
            D id = idGetter.apply(existing);
            if (compositeKey != null && id != null) {
                existingIdMap.put(compositeKey, id);
            }
        }

        log.debug("构建了 {} 个复合键映射", existingIdMap.size());

        // 5. 为每条数据设置正确的 D
        int updateCount = 0;
        int insertCount = 0;

        for (T entity : entityList) {
            String compositeKey = buildCompositeKey(entity, uniqueFieldGetters);

            if (compositeKey == null) {
                log.warn("实体的复合键为 null，跳过处理: {}", entity);
                continue;
            }

            D existingId = existingIdMap.get(compositeKey);

            if (existingId != null) {
                // 数据库中已存在，使用已有 D（执行 UPDATE）
                idSetter.accept(entity, existingId);
                updateCount++;
                if (log.isDebugEnabled()) {
                    log.debug("记录已存在，将执行更新 - 复合键: {}, D: {}", compositeKey, existingId);
                }
            } else {
                // 数据库中不存在，生成新 D（执行 INSERT）
                D currentId = idGetter.apply(entity);
                if (currentId == null) {
                    D newId = idGenerator.apply(entity);
                    idSetter.accept(entity, newId);
                    if (log.isDebugEnabled()) {
                        log.debug("记录不存在，生成新 D - 复合键: {}, 新 D: {}", compositeKey, newId);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("记录不存在，使用已有 D - 复合键: {}, D: {}", compositeKey, currentId);
                    }
                }
                insertCount++;
            }
        }

        log.info("数据处理完成 - 预计插入: {} 条，预计更新: {} 条", insertCount, updateCount);

        // 6. 批量保存或更新
        boolean success = this.saveOrUpdateBatch(entityList);

        if (success) {
            log.info("批量保存或更新成功 - 插入: {} 条，更新: {} 条", insertCount, updateCount);
        } else {
            log.error("批量保存或更新失败");
            throw new RuntimeException("批量保存或更新失败");
        }
        return success;
    }

    /**
     * 构建单条记录的 AND 条件.
     *
     * @param wrapper 查询构造器
     * @param getters 字段 getter 列表
     * @param values  字段值列表
     */
    private void buildAndCondition(
            LambdaQueryWrapper<T> wrapper,
            List<SFunction<T, ?>> getters,
            List<Object> values) {

        for (int i = 0; i < getters.size(); i++) {
            SFunction<T, ?> getter = getters.get(i);
            Object value = values.get(i);
            // 特殊处理：如果值是 UUD 类型，转换为字符串
            wrapper.eq(getter, value);
        }
    }

    /**
     * 构建复合键字符串.
     *
     * @param entity  实体
     * @param getters 字段 getter 列表
     * @return 复合键字符串（格式：value1|value2|value3），如果有字段为 null 则返回 null
     */
    private String buildCompositeKey(T entity, List<SFunction<T, ?>> getters) {
        List<String> parts = new ArrayList<>();
        for (SFunction<T, ?> getter : getters) {
            Object value = getter.apply(entity);
            if (value == null) {
                return null;  // 如果有任何字段为 null，整个复合键为 null
            }
            parts.add(String.valueOf(value));
        }
        return String.join("|", parts);
    }

    /**
     * 批量更新记录，只更新非 null 字段.
     *
     * <p>
     * 实现原理：使用反射获取实体中值不为 null 的字段，然后使用 UpdateWrapper 动态构建更新语句
     * </p>
     *
     * <p>
     * 优点：只更新传入实体中的非null字段，不会覆盖数据库中的其他字段
     * </p>
     *
     * @param entityList 要更新的实体列表
     * @param idGetter   D getter
     * @param <D>        D 类型
     * @return 是否全部更新成功
     */
    private <D> boolean updateBatchByIdSelectivelyWithNonNull(List<T> entityList, SFunction<T, D> idGetter) {
        if (CollectionUtil.isEmpty(entityList)) {
            return true;
        }

        int successCount = 0;
        for (T entity : entityList) {
            try {
                D id = idGetter.apply(entity);
                if (id == null) {
                    log.warn("实体 D 为 null，跳过更新: {}", entity);
                    continue;
                }

                // 使用 UpdateWrapper（而非 LambdaUpdateWrapper）来支持字符串列名
                UpdateWrapper<T> updateWrapper = new UpdateWrapper<>();

                // 获取实体的所有字段，并提取非null字段
                Map<String, Object> updateFields = extractNonNullFields(entity);

                if (updateFields.isEmpty()) {
                    log.warn("实体没有非 null 字段可更新 - D: {}", id);
                    successCount++; // 视为成功（因为没有需要更新的内容）
                    continue;
                }

                // 构建更新条件：根据 D 更新
                updateWrapper.eq(getIdColumnName(entity.getClass()), id);

                // 设置要更新的字段
                for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                    updateWrapper.set(entry.getKey(), entry.getValue());
                }

                // 执行更新
                boolean updated = this.update(updateWrapper);
                if (updated) {
                    successCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("更新成功 - D: {}, 更新字段: {}", id, updateFields.keySet());
                    }
                } else {
                    log.warn("更新失败 - D: {}", id);
                }

            } catch (Exception e) {
                log.error("更新实体时发生异常: {}", entity, e);
                throw new RuntimeException("更新实体失败", e);
            }
        }

        return successCount == entityList.size();
    }

    /**
     * 提取实体中所有非 null 字段及其值.
     *
     * @param entity 实体对象
     * @return 字段名 -> 字段值的映射（字段名已转换为数据库列名）
     */
    private Map<String, Object> extractNonNullFields(T entity) {
        Map<String, Object> fieldMap = new HashMap<>();
        Class<?> clazz = entity.getClass();

        // 遍历所有字段（包括父类字段）
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    // 跳过特殊字段和null值
                    if (value == null || field.getName().equals("serialVersionUD") || isIdField(field)) {
                        continue;
                    }
                    // 获取数据库列名
                    String columnName = getColumnName(field);
                    fieldMap.put(columnName, value);

                } catch (IllegalAccessException e) {
                    log.warn("访问字段失败: {}", field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return fieldMap;
    }

    /**
     * 判断字段是否为 ID 字段.
     *
     * @param field 字段
     * @return 是否为 ID 字段
     */
    private boolean isIdField(Field field) {
        // 检查是否有 @TableId 注解
        if (field.isAnnotationPresent(TableId.class)) {
            return true;
        }
        // 检查字段名是否为常见的 D 字段名
        String fieldName = field.getName().toLowerCase();
        return fieldName.equals("id");
    }

    /**
     * 获取字段对应的数据库列名.
     *
     * @param field 字段
     * @return 数据库列名
     */
    private String getColumnName(Field field) {
        // 检查是否有 @TableField 注解
        if (field.isAnnotationPresent(TableField.class)) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField.value() != null && !tableField.value().isEmpty()) {
                return tableField.value();
            }
        }

        // 默认：驼峰转下划线
        return camelToUnderscore(field.getName());
    }

    /**
     * 获取实体类的 D 列名.
     *
     * @param entityClass 实体类
     * @return D 列名
     */
    private String getIdColumnName(Class<?> entityClass) {
        // 查找带有 @TableId 注解的字段
        while (entityClass != null && entityClass != Object.class) {
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(TableId.class)) {
                    TableId tableId = field.getAnnotation(TableId.class);
                    if (tableId.value() != null && !tableId.value().isEmpty()) {
                        return tableId.value();
                    }
                    return camelToUnderscore(field.getName());
                }
                // 如果没有注解，默认查找名为 id 的字段
                if (field.getName().equalsIgnoreCase("id")) {
                    return camelToUnderscore(field.getName());
                }
            }
            entityClass = entityClass.getSuperclass();
        }

        // 默认返回 "id"
        return "id";
    }

    /**
     * 驼峰命名转下划线命名.
     *
     * <p>
     * 例如：userName -> user_name, userId -> user_id
     * </p>
     *
     * @param camelCase 驼峰命名字符串
     * @return 下划线命名字符串
     */
    private String camelToUnderscore(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(camelCase.charAt(0)));

        for (int i = 1; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

}

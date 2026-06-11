package com.datafusion.scheduler.model;

import cn.hutool.core.util.StrUtil;
import com.datafusion.scheduler.enums.VarType;
import lombok.Data;

import java.util.Objects;

/**
 * 变量类.
 *
 * @author 李正凯
 * @version 3.0 2022/4/28
 * @since 2022/4/28
 */
@Data
public class Variable {

    /**
     * 名称.
     */
    private String name;

    /**
     * 类型.
     */
    private VarType type;

    /**
     * 允许为null，字面量，#[biz_date]等内置变量. null可能要从上游传递.
     */
    private String value;

    /**
     * 值是否为空.
     *
     * @return 结果
     */
    public boolean isEmpty() {
        return StrUtil.isEmpty(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Variable variable = (Variable) o;
        return name.equals(variable.name) && type == variable.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}

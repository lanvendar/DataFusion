/*
 * Copyright © 2020-2022 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

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

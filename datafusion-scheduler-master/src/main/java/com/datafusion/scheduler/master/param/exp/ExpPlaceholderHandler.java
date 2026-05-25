package com.datafusion.scheduler.master.param.exp;

import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.master.param.PlaceholderHandler;

/**
 * 表达式占位符处理器.
 * 处理 #[表达式] 格式的表达式替换.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
public class ExpPlaceholderHandler implements PlaceholderHandler {

    @Override
    public String replacePlaceholders(String value, PlaceholderContext context) {
        return ExpPlaceholderUtils.replacePlaceholders(value, context);
    }

    @Override
    public String getPrefix() {
        return ExpPlaceholderUtils.PLACEHOLDER_PREFIX;
    }

    @Override
    public String getSuffix() {
        return ExpPlaceholderUtils.PLACEHOLDER_SUFFIX;
    }
}

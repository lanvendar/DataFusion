package com.datafusion.scheduler.master.param;

import com.datafusion.scheduler.master.param.builtin.BuiltinParamResolver;
import com.datafusion.scheduler.master.param.exp.ExpPlaceholderHandler;
import com.datafusion.scheduler.master.param.var.VarPlaceholderHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 占位符处理门面类.
 * 统一处理变量和表达式占位符，按照变量优先、表达式在后的顺序处理.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class PlaceholderFacade {

    /**
     * 处理器列表.
     * 按照顺序执行：先内置参数解析 → 变量处理器 → 表达式处理器.
     */
    private final List<PlaceholderHandler> handlers = new ArrayList<>();

    /**
     * 内置参数解析器.
     */
    private final BuiltinParamResolver builtinParamResolver = new BuiltinParamResolver();

    /**
     * 单例实例.
     */
    private static final PlaceholderFacade INSTANCE = new PlaceholderFacade();

    /**
     * 私有构造函数，初始化处理器列表.
     */
    private PlaceholderFacade() {
        // 添加变量处理器
        handlers.add(new VarPlaceholderHandler());
        // 添加表达式处理器
        handlers.add(new ExpPlaceholderHandler());
    }

    /**
     * 获取单例实例.
     *
     * @return PlaceholderFacade实例
     */
    public static PlaceholderFacade getInstance() {
        return INSTANCE;
    }

    /**
     * 替换字符串中的所有占位符.
     * 处理顺序：
     * 1. 解析内置参数并填充到 context.variables
     * 2. 变量处理器处理 #{变量名}
     * 3. 表达式处理器处理 #[表达式]
     *
     * @param value   包含占位符的字符串
     * @param context 占位符处理上下文
     * @return 替换后的字符串
     */
    public String replacePlaceholders(String value, PlaceholderContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Step 1: 解析内置参数
        builtinParamResolver.resolveBuiltinParams(context);

        // Step 2-3: 按顺序执行处理器
        String result = value;
        for (PlaceholderHandler handler : handlers) {
            result = handler.replacePlaceholders(result, context);
        }
        return result;
    }

    /**
     * 添加自定义处理器.
     * 处理器会被添加到处理器列表的末尾.
     *
     * @param handler 占位符处理器
     */
    public void addHandler(PlaceholderHandler handler) {
        handlers.add(handler);
    }

    /**
     * 在指定位置插入处理器.
     *
     * @param index   插入位置
     * @param handler 占位符处理器
     */
    public void addHandler(int index, PlaceholderHandler handler) {
        handlers.add(index, handler);
    }

    /**
     * 获取处理器列表.
     *
     * @return 处理器列表
     */
    public List<PlaceholderHandler> getHandlers() {
        return new ArrayList<>(handlers);
    }
}

package com.datafusion.plugin.api.template;

import com.datafusion.plugin.api.cache.IntermediateCache;
import com.datafusion.plugin.api.core.ApiExtractContext;
import com.datafusion.plugin.api.util.JsonUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateResolver {
    /** 变量标记正则表达式. */
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    /** 中间缓存实例. */
    private final IntermediateCache cache;

    /**
     * 构造无缓存的模板解析器.
     */
    public TemplateResolver() {
        this(null);
    }

    /**
     * 构造带缓存的模板解析器.
     *
     * @param cache 中间缓存实例
     */
    public TemplateResolver(IntermediateCache cache) {
        this.cache = cache;
    }

    /**
     * 解析模板字符串中的变量.
     *
     * @param value 包含变量的模板字符串
     * @param context 抽取上下文
     * @return 解析后的字符串
     */
    public String resolve(String value, ApiExtractContext context) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher matcher = TOKEN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object replacement = lookup(matcher.group(1), context);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? "" : String.valueOf(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 递归解析对象中的模板变量.
     *
     * @param value 待解析的对象
     * @param context 抽取上下文
     * @return 解析后的对象
     */
    @SuppressWarnings("unchecked")
    public Object resolveObject(Object value, ApiExtractContext context) {
        if (value instanceof String) {
            return resolve((String) value, context);
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            source.forEach((key, item) -> copy.put(String.valueOf(key), resolveObject(item, context)));
            return copy;
        }
        if (value instanceof Iterable<?> source) {
            java.util.List<Object> copy = new java.util.ArrayList<>();
            source.forEach(item -> copy.add(resolveObject(item, context)));
            return copy;
        }
        return value;
    }

    private Object lookup(String token, ApiExtractContext context) {
        if (token.startsWith("date:")) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(token.substring("date:".length())));
        }
        if (token.startsWith("datetime:")) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(token.substring("datetime:".length())));
        }
        if (token.startsWith("env.")) {
            return System.getenv(token.substring("env.".length()));
        }
        if (token.startsWith("secrets.")) {
            return System.getenv(token.substring("secrets.".length()));
        }
        if ("job.id".equals(token)) {
            return context.getConfig().job.id;
        }
        if ("run.id".equals(token)) {
            return context.getRunId();
        }
        if (token.startsWith("inputVars.")) {
            Object value = context.getVars().get(token.substring("inputVars.".length()));
            if (value instanceof String text && text.contains("${")) {
                return resolve(text, context);
            }
            return value;
        }
        if (token.startsWith("steps.")) {
            return lookupStepOutput(token, context);
        }
        if (token.startsWith("redis.")) {
            return lookupRedis(token, context);
        }
        return "";
    }

    private Object lookupStepOutput(String token, ApiExtractContext context) {
        String prefix = "steps.";
        String suffix = ".outputVars.";
        int suffixIndex = token.indexOf(suffix);
        if (suffixIndex < 0) {
            return "";
        }
        String stepId = token.substring(prefix.length(), suffixIndex);
        String field = token.substring(suffixIndex + suffix.length());
        Object value = context.getStepOutput(stepId).get(field);
        if (value instanceof Map || value instanceof Iterable) {
            return JsonUtils.write(value);
        }
        return value;
    }

    private Object lookupRedis(String token, ApiExtractContext context) {
        if (cache == null) {
            return "";
        }
        String customKey = token.substring("redis.".length());
        String prefix = context.getConfig().redis == null
                ? "" : context.getConfig().redis.optionString("keyPrefix", "datafusion:plugin:api") + ":";
        Object value = cache.get(prefix + customKey);
        if (value instanceof Map || value instanceof Iterable) {
            return JsonUtils.write(value);
        }
        return value;
    }
}

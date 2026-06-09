package com.datafusion.agent.runtime.worker.plugin.template;

import com.datafusion.common.utils.JacksonUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAML template renderer for plugin execution specs.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/9
 * @since 1.0.0
 */
@Component
public class TemplateSpecRenderer {

    /**
     * Unresolved placeholder pattern.
     */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[^}]+}}");

    /**
     * Render template to text.
     *
     * @param templatePath classpath template path
     * @param values       render values
     * @return rendered text
     */
    public String renderText(String templatePath, Map<String, String> values) {
        String result = loadTemplate(templatePath);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        Matcher matcher = UNRESOLVED_PLACEHOLDER.matcher(result);
        if (matcher.find()) {
            throw new IllegalArgumentException("模板占位符未渲染: " + matcher.group());
        }
        return result;
    }

    /**
     * Render YAML template to target spec.
     *
     * @param templatePath classpath template path
     * @param values       render values
     * @param targetClass  target spec class
     * @param <T>          target type
     * @return target spec
     */
    public <T> T renderYaml(String templatePath, Map<String, String> values, Class<T> targetClass) {
        Object loaded = new Yaml().load(renderText(templatePath, values));
        return JacksonUtils.tryObj2Bean(loaded, targetClass);
    }

    private String loadTemplate(String templatePath) {
        try (InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(templatePath)) {
            if (stream == null) {
                throw new IllegalStateException("模板不存在: " + templatePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取模板失败: " + templatePath, e);
        }
    }
}

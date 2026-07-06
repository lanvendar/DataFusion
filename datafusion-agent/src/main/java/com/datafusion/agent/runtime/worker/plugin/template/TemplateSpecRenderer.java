package com.datafusion.agent.runtime.worker.plugin.template;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.utils.JacksonUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Agent properties.
     */
    private final AgentProperties properties;

    /**
     * Constructor.
     *
     * @param properties agent properties
     */
    public TemplateSpecRenderer(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Render template to text.
     *
     * @param templatePath plugin template path relative to plugins root dir
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
     * @param templatePath plugin template path relative to plugins root dir
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
        String pluginsRootDir = properties.getPluginsRootDir();
        if (pluginsRootDir == null || pluginsRootDir.isBlank()) {
            throw new IllegalStateException("插件根目录未配置");
        }
        Path relativeTemplatePath = Path.of(templatePath);
        if (relativeTemplatePath.isAbsolute()) {
            throw new IllegalArgumentException("模板路径必须是插件根目录下的相对路径: " + templatePath);
        }
        Path pluginsRootPath = Path.of(pluginsRootDir).normalize();
        Path templateFile = pluginsRootPath.resolve(relativeTemplatePath).normalize();
        if (!templateFile.startsWith(pluginsRootPath)) {
            throw new IllegalArgumentException("外部模板路径越界: " + templatePath);
        }
        if (!Files.isRegularFile(templateFile)) {
            throw new IllegalStateException("模板不存在: " + templateFile);
        }
        try {
            return Files.readString(templateFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取模板失败: " + templateFile, e);
        }
    }
}

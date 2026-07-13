package com.datafusion.agent.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Kubernetes 资源名称工具类.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
public final class KubernetesResourceNameUtils {

    /**
     * Kubernetes 资源名称最大长度.
     */
    private static final int MAX_NAME_LENGTH = 63;

    /**
     * 名称 hash 长度.
     */
    private static final int HASH_LENGTH = 10;

    /**
     * 非法字符匹配规则.
     */
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-z0-9-]");

    /**
     * 连续横线匹配规则.
     */
    private static final Pattern REPEATED_HYPHENS = Pattern.compile("-+");

    /**
     * 首尾横线匹配规则.
     */
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private KubernetesResourceNameUtils() {
    }

    /**
     * 生成任务主资源名称.
     *
     * @param namePrefix    名称前缀
     * @param taskInstanceId 任务实例 ID
     * @return Kubernetes 资源名称
     */
    public static String resourceName(String namePrefix, String taskInstanceId) {
        return resourceName(new String[] {namePrefix, taskInstanceId});
    }

    /**
     * 生成带角色的任务资源名称.
     *
     * @param namePrefix    名称前缀
     * @param role          资源角色
     * @param taskInstanceId 任务实例 ID
     * @return Kubernetes 资源名称
     */
    public static String resourceName(String namePrefix, String role, String taskInstanceId) {
        return resourceName(new String[] {namePrefix, role, taskInstanceId});
    }

    /**
     * 生成带角色和序号的任务资源名称.
     *
     * @param namePrefix    名称前缀
     * @param role          资源角色
     * @param ordinal       资源序号，从 0 开始
     * @param taskInstanceId 任务实例 ID
     * @return Kubernetes 资源名称
     */
    public static String resourceName(String namePrefix, String role, int ordinal, String taskInstanceId) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal不能小于0");
        }
        return resourceName(new String[] {namePrefix, role, String.valueOf(ordinal), taskInstanceId});
    }

    private static String resourceName(String[] segments) {
        StringJoiner joiner = new StringJoiner("-");
        for (String segment : segments) {
            String normalized = normalize(segment);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Kubernetes资源名称片段不能为空");
            }
            joiner.add(normalized);
        }
        String resourceName = joiner.toString();
        if (resourceName.length() <= MAX_NAME_LENGTH) {
            return resourceName;
        }
        String hash = hash(resourceName).substring(0, HASH_LENGTH);
        int prefixLength = MAX_NAME_LENGTH - HASH_LENGTH - 1;
        String readablePrefix = EDGE_HYPHENS.matcher(resourceName.substring(0, prefixLength)).replaceAll("");
        return readablePrefix + '-' + hash;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = INVALID_CHARACTERS.matcher(normalized).replaceAll("-");
        normalized = REPEATED_HYPHENS.matcher(normalized).replaceAll("-");
        return EDGE_HYPHENS.matcher(normalized).replaceAll("");
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}

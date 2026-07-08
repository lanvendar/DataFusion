package com.datafusion.plugin.flink.table.resolve;

import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.core.SystemFieldNames;
import com.datafusion.plugin.flink.table.core.enums.ProxyPrimaryKeyType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 代理主键生成器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ProxyPrimaryKeyGenerator {

    /**
     * 代理主键字段名.
     */
    public static final String FIELD_NAME = SystemFieldNames.PROXY_PRIMARY_KEY_FIELD;

    /**
     * 拼接分隔符.
     */
    private static final String SEPARATOR = "_";

    /**
     * 十六进制字符表.
     */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProxyPrimaryKeyGenerator() {
    }

    /**
     * 生成代理主键.
     *
     * @param record 记录
     * @param keyFields 代理主键来源字段
     * @param type 代理主键生成类型
     * @return 代理主键
     */
    public static String generate(Map<String, Object> record, List<String> keyFields, ProxyPrimaryKeyType type) {
        String rawKey = keyFields.stream()
                .map(field -> String.valueOf(record == null ? null : record.get(field)))
                .collect(Collectors.joining(SEPARATOR));
        if (type == ProxyPrimaryKeyType.UUID) {
            return UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8)).toString();
        }
        return hexDigest(rawKey, algorithm(type));
    }

    private static String algorithm(ProxyPrimaryKeyType type) {
        if (type == ProxyPrimaryKeyType.SHA_256) {
            return "SHA-256";
        }
        if (type == ProxyPrimaryKeyType.SHA_512) {
            return "SHA-512";
        }
        throw new FlinkTableException("Unsupported proxyPrimaryKeyType: " + type);
    }

    private static String hexDigest(String value, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new FlinkTableException("Unsupported digest algorithm: " + algorithm, e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}

package com.datafusion.common.uuid;

import cn.hutool.core.util.StrUtil;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * UUID5规范的 UUID类.
 * 与UUID3的区别就是 hash使用 SHA1,而 UUID3使用 MD5.
 * UUID5 安全性较好.
 * refer1: <a href="https://rumenz.com/java-topic/java/basics/java-uuid/index.html">Java UUID</a>
 * refer2:
 * <a href="https://www.uuidtools.com/uuid-versions-explained#:~:text=Version-3%20UUIDs%20are%20based%20on%20an%20MD5%20hash,truncated.%20The%20UUID%20specification%20establishes%204%20pre-defined%20namespaces.">uuidtools</a>
 *
 * @author qiyao N0068003
 * @version 1.0.0
 * @since 2023-02-02
 */
public class Uuid5 {
    
    /**
     * 通过字符串创建UUID.
     *
     * @param nameSpace NameSpace
     * @param key       生成UUID的key
     * @return UUID
     */
    public static UUID fromUtf8(UUID nameSpace, String key) {
        return fromBytes((nameSpace + key).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 通过字符串创建UUID.
     *
     * @param key       生成UUID的key
     * @return UUID
     */
    public static UUID fromUtf8(String key) {
        return fromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 通过字符串创建UUID.
     *
     * @param nameSpace NameSpace
     * @param key       生成UUID的key
     * @return UUID
     */
    public static UUID fromUtf8(String nameSpace, String key) {
        String nameSpaceUuid;
        if (StrUtil.isBlank(nameSpace)) {
            nameSpaceUuid = "";
        } else {
            nameSpaceUuid = fromBytes(nameSpace.getBytes(StandardCharsets.UTF_8)).toString();
        }
        return fromBytes((nameSpaceUuid + key).getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * fromBytes.
     *
     * @param name name.
     * @return UUID
     */
    private static UUID fromBytes(byte[] name) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return makeUuid(md.digest(name), 5);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
    
    /**
     * makeUUID.
     *
     * @param hash    hash
     * @param version version
     * @return UUID
     */
    private static UUID makeUuid(byte[] hash, int version) {
        long msb = peekLong(hash, 0, ByteOrder.BIG_ENDIAN);
        long lsb = peekLong(hash, 8, ByteOrder.BIG_ENDIAN);
        // Set the version field
        msb &= ~(0xfL << 12);
        msb |= ((long) version) << 12;
        // Set the variant field to 2
        lsb &= ~(0x3L << 62);
        lsb |= 2L << 62;
        return new UUID(msb, lsb);
    }
    
    /**
     * peekLong.
     *
     * @param src    src
     * @param offset offset
     * @param order  order
     * @return long
     */
    private static long peekLong(final byte[] src, final int offset, final ByteOrder order) {
        long ans = 0;
        if (order == ByteOrder.BIG_ENDIAN) {
            for (int i = offset; i < offset + 8; i += 1) {
                ans <<= 8;
                ans |= src[i] & 0xffL;
            }
        } else {
            for (int i = offset + 7; i >= offset; i -= 1) {
                ans <<= 8;
                ans |= src[i] & 0xffL;
            }
        }
        return ans;
    }
}

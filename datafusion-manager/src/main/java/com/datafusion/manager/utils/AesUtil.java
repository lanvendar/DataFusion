package com.datafusion.manager.utils;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * AES加解密算法.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/9
 * @since 2025/9/9
 */
@Slf4j
public class AesUtil {

    /**
     * 固定密钥.
     */
    public static final String AES_KEY = "4318142355520";

    /**
     * KEY_ALGORITHM.
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * 默认的加密算法.
     */
    private static final String DEFAULT_CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";

    /**
     * 密文格式.
     */
    private static final String MI_FORMART = "MI_START_%s_MI_END";

    /**
     * 密文格式.
     */
    private static final Pattern MI_PATTERN = Pattern.compile("MI_START_(.*?)_MI_END");

    /**
     * 随机生成密钥.
     *
     * @return String
     */
    public static String getAesRandomKey() {
        SecureRandom random = new SecureRandom();
        long randomKey = random.nextLong();
        return String.valueOf(randomKey);
    }

    /**
     * AES 加密操作.
     *
     * @param content
     *            待加密内容
     * @param key
     *            加密密钥
     * @return 返回Base64转码后的加密数据
     */
    public static String encrypt(String content, String key) {
        try {
            // 创建密码器
            Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
            byte[] byteContent = content.getBytes("utf-8");
            // 初始化为加密模式的密码器
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(key));
            // 加密
            byte[] result = cipher.doFinal(byteContent);
            // 通过Base64转码返回
            return byte2Base64(result);
        } catch (Exception ex) {
            log.error("加密失败", ex);
        }
        return null;
    }

    /**
     * AES 加密操作.
     * @param content 加密内容
     * @return String
     * @throws Exception 异常
     */
    public static String encrypt(String content) {
        if (StrUtil.isEmpty(content)) {
            return content;
        }
        return String.format(MI_FORMART, encrypt(content, AES_KEY));
    }

    /**
     * AES 解密操作.
     *
     * @param content 密文
     * @param key 密钥
     * @return 明文
     */
    public static String decrypt(String content, String key) {
        try {
            // 实例化
            Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
            // 使用密钥初始化，设置为解密模式
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(key));
            // 执行操作
            byte[] result = cipher.doFinal(base642Byte(content));
            return new String(result, "utf-8");
        } catch (Exception ex) {
            // 记录日志，但不抛出异常，因为isSecret方法会利用此处的null判断解密失败
            log.debug("解密失败：{}", ex.getMessage()); // 使用debug级别，避免过多日志
        }

        return null;
    }

    /**
     * AES 解密操作.
     * @param content 密文
     * @return 明文
     */
    public static String decrypt(String content) {
        if (StrUtil.isEmpty(content)) {
            return content;
        }
        if (!isSecret(content)) {
            log.error("密文格式有误！", content); // 应该记录错误
            return null;
        }
        return decrypt(cleanString(content), AES_KEY);
    }

    /**
     * 截断格式化明文，得到原始密文.
     * @param input input
     * @return 原始密文
     */
    public static String cleanString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String prefix = "MI_START_";
        String suffix = "_MI_END";

        // 检查是否以 "MI_START_" 开头
        if (input.startsWith(prefix)) {
            input = input.substring(prefix.length());
        }

        // 检查是否以 "_MI_END" 结尾
        if (input.endsWith(suffix)) {
            input = input.substring(0, input.length() - suffix.length());
        }

        return input;
    }

    /**
     * 生成加密秘钥.
     *
     * @return SecretKeySpec
     */
    private static SecretKeySpec getSecretKey(final String key) {
        // 返回生成指定算法密钥生成器的 KeyGenerator 对象
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
            // 此类提供加密的强随机数生成器 (RNG)，该实现在windows上每次生成的key都相同，但是在部分linux或solaris系统上则不同。
            // SecureRandom random = new SecureRandom(key.getBytes());
            // 指定算法名称，不同的系统上生成的key是相同的。
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(key.getBytes());
            // AES 要求密钥长度为 128
            kg.init(128, random);
            // 生成一个密钥
            SecretKey secretKey = kg.generateKey();
            // 转换为AES专用密钥
            return new SecretKeySpec(secretKey.getEncoded(), KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            log.error("生成加密秘钥异常！", ex); // 应该记录错误
        }
        return null;
    }

    /**
     * 字节数组转Base64编码.
     *
     * @param bytes bytes
     * @return String
     */
    public static String byte2Base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64编码转字节数组.
     *
     * @param base64Key base64Key
     * @return byte []
     * @throws IOException IOException
     */
    public static byte[] base642Byte(String base64Key) throws IOException {
        return  Base64.getDecoder().decode(base64Key);
    }

    /**
     * 判断字符串是否是密文.
     * 如果输入字符串满足 MI_FORMART 格式，并且中间截取的部分可以解密，就算是密文，否则就算明文。
     * @param content 输入（明文|密文）
     * @return boolean
     */
    public static boolean isSecret(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        Matcher matcher = MI_PATTERN.matcher(content);
        if (matcher.matches()) { // 如果字符串完全匹配整个模式
            String encryptedPart = matcher.group(1); // 获取括号中的内容
            if (encryptedPart != null && !encryptedPart.isEmpty()) {
                // 尝试解密
                String decrypted = decrypt(encryptedPart, AES_KEY);
                // 如果解密结果不为null，则认为可以成功解密
                return decrypted != null;
            }
        }
        return false; // 不符合格式或者解密失败
    }
}
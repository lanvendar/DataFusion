package com.datafusion.manager.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author lanvendar
 * @version 1.0.0, 2025/9/9
 * @since 2025/9/9
 */
@Slf4j
public class AesUtilTest {
    private static final String MI_FORMART = "MI_START_%s_MI_END";
    @Test
    public void test() {
        String plainText = "Hello, world! This is a test message.";
        log.info("原始明文: " + plainText);
        
        // 使用固定密钥加密
        String encryptedContent = AesUtil.encrypt(plainText, AesUtil.AES_KEY);
        log.info("加密后的Base64: " + encryptedContent);
        
        // 格式化为密文模式
        String formattedSecret = String.format(MI_FORMART, encryptedContent);
        log.info("格式化后的密文: " + formattedSecret);
        
        log.info("--- 测试 isSecret 方法 ---");
        log.info("原始明文是否是密文: " + AesUtil.isSecret(plainText)); // 期望 false
        log.info("加密后的Base64是否是密文: " + AesUtil.isSecret(encryptedContent)); // 期望 false (因为它没有MI_FORMART包装)
        log.info("格式化后的密文是否是密文: " + AesUtil.isSecret(formattedSecret)); // 期望 true
        
        String wrongFormat = "MI_START_abc_MI_END";
        log.info("错误格式的字符串是否是密文: " + AesUtil.isSecret(wrongFormat)); // 期望 false (无法解密)
        
        String emptyString = "";
        log.info("空字符串是否是密文: " + AesUtil.isSecret(emptyString)); // 期望 false
        
        String nullString = null;
        log.info("null字符串是否是密文: " + AesUtil.isSecret(nullString)); // 期望 false
        
        String textWithDifferentKey = "这是用不同密钥加密的文本";
        String encryptedWithDifferentKey = AesUtil.encrypt(textWithDifferentKey, "anotherKey");
        String formattedWithDifferentKey = String.format(MI_FORMART, encryptedWithDifferentKey);
        log.info("用不同密钥加密的字符串是否是密文: " + AesUtil.isSecret(formattedWithDifferentKey)); // 期望 false (无法用AES_KEY解密)
        
        // 验证解密
        String decryptedFromFormatted = AesUtil.decrypt(encryptedContent, AesUtil.AES_KEY);
        log.info("从格式化密文中提取并解密: " + decryptedFromFormatted);
    }
    
    @Test
    public void test1(){
        String url = "https%3A%2F%2Fbeta.we.goodwe.com%2Fseeds-assetsmanage%2F%23%2Finverter-device%2F1902722918945783808";
        String decode = URLDecoder.decode(url, StandardCharsets.UTF_8);
        System.out.println(decode);
    }
}

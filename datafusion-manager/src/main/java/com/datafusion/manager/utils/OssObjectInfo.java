package com.datafusion.manager.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * OSS 按前缀列举时的对象摘要（不含敏感信息）.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/14
 * @since 2026/5/14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OssObjectInfo {

    /**
     * 对象键（完整路径）.
     */
    private String objectKey;

    /**
     * 字节大小.
     */
    private long size;

    /**
     * 最后修改时间.
     */
    private Date lastModified;

    /**
     * ETag.
     */
    private String etag;
}

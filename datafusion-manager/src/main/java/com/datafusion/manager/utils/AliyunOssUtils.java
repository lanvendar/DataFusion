package com.datafusion.manager.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.DeleteObjectsResult;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 阿里云 OSS 操作封装；客户端由 Spring 单例注入，未开启 {@code oss.enabled} 时调用将抛出业务异常.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/14
 * @since 2026/5/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOssUtils {

    /**
     * 单次批量删除对象键数量上限（阿里云限制）.
     */
    private static final int DELETE_OBJECTS_MAX_KEYS = 1000;

    /**
     * 列举时每页最大条数.
     */
    private static final int LIST_MAX_KEYS = 500;

    /**
     * OSS 客户端（未启用时为 empty）.
     */
    private final ObjectProvider<OSS> ossClientProvider;

    /**
     * OSS 配置.
     */
    private final OssProperties ossProperties;

    /**
     * 是否已装配 OSS 客户端.
     *
     * @return true 表示可调用上传/删除等接口
     */
    public boolean isOssAvailable() {
        return ossClientProvider.getIfAvailable() != null;
    }

    /**
     * 上传本地文件.
     *
     * @param bucket     桶名，可为 null 使用默认桶
     * @param objectKey  对象键
     * @param file       本地文件
     * @param contentType 可为 null
     */
    public void uploadFile(String bucket, String objectKey, File file, String contentType) {
        requireOss();
        String b = resolveBucket(bucket);
        validateKey(objectKey);
        try {
            PutObjectRequest request = new PutObjectRequest(b, objectKey, file);
            if (StringUtils.isNotBlank(contentType)) {
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentType(contentType.trim());
                request.setMetadata(meta);
            }
            ossClientProvider.getObject().putObject(request);
            log.debug("OSS putObject success bucket={} key={}", b, objectKey);
        } catch (OSSException e) {
            throw wrapOssFailure("上传文件失败", e, b, objectKey);
        } catch (ClientException e) {
            throw wrapClientFailure("上传文件失败", e, b, objectKey);
        }
    }

    /**
     * 上传字节数组.
     *
     * @param bucket      桶名，可为 null 使用默认桶
     * @param objectKey   对象键
     * @param data        数据
     * @param contentType 可为 null
     */
    public void uploadBytes(String bucket, String objectKey, byte[] data, String contentType) {
        requireOss();
        String b = resolveBucket(bucket);
        validateKey(objectKey);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(data.length);
            if (StringUtils.isNotBlank(contentType)) {
                meta.setContentType(contentType.trim());
            }
            PutObjectRequest request = new PutObjectRequest(b, objectKey, new java.io.ByteArrayInputStream(data), meta);
            ossClientProvider.getObject().putObject(request);
            log.debug("OSS putObject success bucket={} key={} bytes={}", b, objectKey, data.length);
        } catch (OSSException e) {
            throw wrapOssFailure("上传字节失败", e, b, objectKey);
        } catch (ClientException e) {
            throw wrapClientFailure("上传字节失败", e, b, objectKey);
        }
    }

    /**
     * 以流上传；须设置内容长度（与 OSS PutObject 要求一致）.
     *
     * @param bucket         桶名，可为 null 使用默认桶
     * @param objectKey      对象键
     * @param inputStream    输入流（调用方负责关闭）
     * @param contentLength  字节长度，必须大于 0
     * @param contentType    可为 null
     */
    public void uploadStream(String bucket, String objectKey, InputStream inputStream, long contentLength,
            String contentType) {
        requireOss();
        if (contentLength <= 0) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "contentLength 必须大于 0");
        }
        String b = resolveBucket(bucket);
        validateKey(objectKey);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(contentLength);
            if (StringUtils.isNotBlank(contentType)) {
                meta.setContentType(contentType.trim());
            }
            PutObjectRequest request = new PutObjectRequest(b, objectKey, inputStream, meta);
            ossClientProvider.getObject().putObject(request);
            log.debug("OSS putObject success bucket={} key={} len={}", b, objectKey, contentLength);
        } catch (OSSException e) {
            throw wrapOssFailure("上传流失败", e, b, objectKey);
        } catch (ClientException e) {
            throw wrapClientFailure("上传流失败", e, b, objectKey);
        }
    }

    /**
     * 将本地目录下所有常规文件递归上传到 OSS；对象键为 {@code ossKeyPrefix + 相对路径}（相对根目录，统一正斜杠，不含前导 {@code /}）.
     *
     * @param bucket              桶名，可为 null 使用默认桶
     * @param localDirectoryPath  本地目录路径（须存在且为目录）
     * @param ossKeyPrefix        OSS 对象键前缀，如 {@code backup/job-001/}；可为 null 或空表示桶根下仅保留相对路径
     * @return 成功上传的文件数量
     */
    public int uploadDirectory(String bucket, String localDirectoryPath, String ossKeyPrefix) {
        requireOss();
        if (StringUtils.isBlank(localDirectoryPath)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "localDirectoryPath 不能为空");
        }
        Path root;
        try {
            root = Paths.get(localDirectoryPath.trim()).toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154,
                    "本地目录不存在或无法解析: " + localDirectoryPath);
        }
        if (!Files.isDirectory(root)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "路径不是目录: " + localDirectoryPath);
        }
        final Path base = root;
        final String prefix = normalizeOssKeyPrefix(ossKeyPrefix);
        int uploaded = 0;
        try (Stream<Path> stream = Files.walk(base)) {
            List<Path> files = stream
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .collect(Collectors.toList());
            for (Path path : files) {
                Path realFile;
                try {
                    realFile = path.toRealPath();
                } catch (IOException e) {
                    log.warn("跳过无法解析的文件: {}", path);
                    continue;
                }
                if (!realFile.startsWith(base)) {
                    throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154,
                            "不允许上传根目录外的解析路径: " + path);
                }
                Path relative = base.relativize(realFile);
                String relativeKey = relative.toString().replace('\\', '/');
                if (StringUtils.isBlank(relativeKey)) {
                    continue;
                }
                String objectKey = prefix + relativeKey;
                uploadFile(bucket, objectKey, realFile.toFile(), null);
                uploaded++;
            }
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, "遍历本地目录失败: " + e.getMessage());
        }
        log.info("OSS uploadDirectory base={} prefix={} uploaded={}", base, prefix, uploaded);
        return uploaded;
    }

    /**
     * 将 OSS 指定前缀下所有对象下载到本地目录：本地路径为 {@code localDirectoryPath} + 与 OSS 对象键一致的层级（从键首段到文件名，正斜杠映射为本地分隔符）.
     * <p>仅列举键名以规范化后的 {@code ossKeyPrefix} 开头的对象；下载时在本地保留<strong>完整对象键</strong>对应的目录结构，不再去掉前缀段
     * （例如键 {@code secp-dolphin-job-script/DAG/we/sebu1/a.txt} 落到 {@code localRoot/secp-dolphin-job-script/DAG/we/sebu1/a.txt}）。</p>
     * <p>下载前若本地根目录已存在且<strong>非空</strong>，会先递归删除其下文件与子目录（<strong>保留 {@code .git} 目录</strong>，避免破坏本地 Git 元数据及 Windows 下 pack 文件被占用导致删除失败），再写入本次下载结果。</p>
     * <p>本地根目录不存在时会创建；不下载键名以 {@code /} 结尾的目录占位对象；若解析后越出本地根目录则抛参错。</p>
     *
     * @param bucket              桶名，可为 null 使用默认桶
     * @param localDirectoryPath  本地根目录路径
     * @param ossKeyPrefix        OSS 列举用对象键前缀（与 {@link #uploadDirectory(String, String, String)} 规则一致，经 {@link #normalizeOssKeyPrefix(String)} 规范化）
     * @return 成功下载的文件数量
     */
    public int downloadDirectoryFromOss(String bucket, String localDirectoryPath, String ossKeyPrefix) {
        requireOss();
        if (StringUtils.isBlank(localDirectoryPath)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "localDirectoryPath 不能为空");
        }
        if (StringUtils.isBlank(ossKeyPrefix)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "ossKeyPrefix 不能为空");
        }
        String prefix = normalizeOssKeyPrefix(ossKeyPrefix);
        if (StringUtils.isBlank(prefix)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "ossKeyPrefix 无效");
        }
        String b = resolveBucket(bucket);
        Path localRoot;
        try {
            localRoot = Paths.get(localDirectoryPath.trim()).toAbsolutePath().normalize();
            Files.createDirectories(localRoot);
            localRoot = localRoot.toRealPath();
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154,
                    "本地目录无法创建或解析: " + localDirectoryPath + " — " + e.getMessage());
        }
        if (!Files.isDirectory(localRoot)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "路径不是目录: " + localDirectoryPath);
        }
        try {
            if (!isLocalDirectoryEmpty(localRoot)) {
                log.info("OSS downloadDirectoryFromOss 本地目录非空，下载前清空: {}", localRoot);
                emptyLocalDirectoryContents(localRoot);
            }
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, "清空本地目录失败: " + e.getMessage());
        }
        List<OssObjectInfo> objects = listObjectsByPrefix(bucket, prefix, null);
        int downloaded = 0;
        for (OssObjectInfo info : objects) {
            String key = info.getObjectKey();
            if (StringUtils.isBlank(key) || isOssFolderPlaceholderKey(key)) {
                continue;
            }
            if (!key.startsWith(prefix)) {
                log.warn("跳过与列举前缀不一致的对象键: {}", key);
                continue;
            }
            String pathForLocal = key;
            while (pathForLocal.startsWith("/")) {
                pathForLocal = pathForLocal.substring(1);
            }
            if (StringUtils.isBlank(pathForLocal)) {
                continue;
            }
            Path targetFile = localRoot.resolve(relativeToLocalPath(pathForLocal)).normalize();
            if (!targetFile.startsWith(localRoot)) {
                throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154,
                        "对象键导致路径越界: " + key);
            }
            downloadObjectToPath(b, key, targetFile);
            downloaded++;
        }
        log.info("OSS downloadDirectoryFromOss bucket={} prefix={} localRoot={} downloaded={}", b, prefix, localRoot,
                downloaded);
        return downloaded;
    }

    /**
     * 判断目录是否无任何子项（不含隐藏项以外的特殊规则）.
     *
     * @param dir 目录
     * @return 是否为空
     * @throws IOException 列举失败
     */
    private static boolean isLocalDirectoryEmpty(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        }
    }

    /**
     * 删除目录下全部文件与子目录，保留该目录本身.
     * <p>不删除根目录下 {@code .git} 及其子路径（避免与 Git 客户端锁定的 pack 等文件冲突）；其余内容递归删除。</p>
     *
     * @param dir 根目录
     * @throws IOException 删除失败
     */
    private static void emptyLocalDirectoryContents(Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        List<Path> toDelete = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> !p.equals(dir))
                    .filter(p -> !isUnderGitMetadata(root, p))
                    .forEach(toDelete::add);
        }
        toDelete.sort(Comparator.reverseOrder());
        for (Path p : toDelete) {
            deleteLocalPathWithRetry(p);
        }
    }

    /**
     * 判断路径是否位于根目录下的 {@code .git} 元数据树内（任一层级名为 {@code .git}，大小写不敏感）.
     *
     * @param root      下载根目录
     * @param candidate 待删除路径
     * @return 是否应跳过删除
     */
    private static boolean isUnderGitMetadata(Path root, Path candidate) {
        Path r = root.toAbsolutePath().normalize();
        Path c = candidate.toAbsolutePath().normalize();
        if (!c.startsWith(r)) {
            return false;
        }
        Path rel = r.relativize(c);
        for (int i = 0; i < rel.getNameCount(); i++) {
            if (".git".equalsIgnoreCase(rel.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除文件或空目录；删除前尝试去掉只读属性并短暂重试（缓解 Windows 下偶发占用）.
     *
     * @param p 路径
     * @throws IOException 重试后仍失败
     */
    private static void deleteLocalPathWithRetry(Path p) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                    return;
                }
                File f = p.toFile();
                f.setWritable(true);
                Files.delete(p);
                return;
            } catch (IOException e) {
                last = e;
            }
            try {
                Thread.sleep(30L * (attempt + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("删除被中断: " + p, ie);
            }
        }
        if (last != null) {
            throw new IOException("删除失败: " + p, last);
        }
        throw new IOException("删除失败(重试用尽): " + p);
    }

    /**
     * 将 OSS 对象键（完整相对路径，正斜杠分隔）转为本地 {@link Path}，逐段 resolve，禁止 {@code .} 与 {@code ..}.
     *
     * @param relativeSlash OSS 对象键去掉前导斜杠后的路径
     * @return 相对本地根的路径
     */
    private static Path relativeToLocalPath(String relativeSlash) {
        String[] segs = relativeSlash.split("/");
        List<String> parts = new ArrayList<>();
        for (String s : segs) {
            if (StringUtils.isNotBlank(s)) {
                if (".".equals(s) || "..".equals(s)) {
                    throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154,
                            "对象键含非法路径段: " + relativeSlash);
                }
                parts.add(s);
            }
        }
        if (parts.isEmpty()) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "相对路径为空: " + relativeSlash);
        }
        Path p = Paths.get(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            p = p.resolve(parts.get(i));
        }
        return p;
    }

    /**
     * 下载单个对象到本地文件（覆盖已存在文件）.
     *
     * @param bucketName 桶名
     * @param objectKey  对象键
     * @param targetFile 目标文件绝对路径
     */
    private void downloadObjectToPath(String bucketName, String objectKey, Path targetFile) {
        validateKey(objectKey);
        Path parent = targetFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, "创建本地目录失败: " + e.getMessage());
            }
        }
        try {
            OSS oss = ossClientProvider.getObject();
            try (OSSObject ossObject = oss.getObject(bucketName, objectKey);
                    InputStream in = ossObject.getObjectContent();
                    OutputStream out = Files.newOutputStream(targetFile, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
            }
            log.debug("OSS downloadObject success bucket={} key={} target={}", bucketName, objectKey, targetFile);
        } catch (OSSException e) {
            throw wrapOssFailure("下载对象失败", e, bucketName, objectKey);
        } catch (ClientException e) {
            throw wrapClientFailure("下载对象失败", e, bucketName, objectKey);
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, "写入本地文件失败: " + e.getMessage());
        }
    }

    /**
     * 规范化 OSS 对象键前缀：去首尾空白、反斜杠转正斜杠、去掉前导斜杠；非空时保证以 {@code /} 结尾便于拼接相对路径.
     *
     * @param ossKeyPrefix 原始前缀，可为 null
     * @return 规范化后的前缀，可能为空串
     */
    private static String normalizeOssKeyPrefix(String ossKeyPrefix) {
        if (StringUtils.isBlank(ossKeyPrefix)) {
            return "";
        }
        String p = ossKeyPrefix.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (StringUtils.isEmpty(p)) {
            return "";
        }
        return p.endsWith("/") ? p : p + "/";
    }

    /**
     * 删除单个对象.
     *
     * @param bucket    桶名，可为 null 使用默认桶
     * @param objectKey 对象键
     */
    public void deleteObject(String bucket, String objectKey) {
        requireOss();
        String b = resolveBucket(bucket);
        validateKey(objectKey);
        try {
            ossClientProvider.getObject().deleteObject(b, objectKey);
            log.debug("OSS deleteObject success bucket={} key={}", b, objectKey);
        } catch (OSSException e) {
            throw wrapOssFailure("删除对象失败", e, b, objectKey);
        } catch (ClientException e) {
            throw wrapClientFailure("删除对象失败", e, b, objectKey);
        }
    }

    /**
     * 按前缀列举对象（模拟「目录」；OSS 无真实文件夹）.
     * <ul>
     *   <li>{@code delimiter} 为空或 null：扁平列举前缀下所有对象（分页），结果中不包含键名以 {@code /} 结尾的目录占位对象。</li>
     *   <li>{@code delimiter} 非空（常用 {@code /}）：按「目录」分层列举，对每个 {@code commonPrefixes} 递归继续列举，
     *   最终返回所有叶子文件对象摘要，同样不包含目录占位键。</li>
     * </ul>
     *
     * @param bucket    桶名，可为 null 使用默认桶
     * @param prefix    前缀，如 {@code data/2025/}，可为 null 表示从桶根开始（慎用）
     * @param delimiter 目录分隔符，常用 {@code /}；为 null 或空则扁平列举整棵前缀子树
     * @return 对象摘要列表（不含目录占位对象；delimiter 模式下不含仅作为前缀的「文件夹」条目）
     */
    public List<OssObjectInfo> listObjectsByPrefix(String bucket, String prefix, String delimiter) {
        requireOss();
        String b = resolveBucket(bucket);
        try {
            OSS oss = ossClientProvider.getObject();
            if (StringUtils.isBlank(delimiter)) {
                return listFlatByPrefixAllPages(oss, b, prefix);
            }
            return listRecursiveDelimitedAllPages(oss, b, prefix, delimiter);
        } catch (OSSException e) {
            throw wrapOssFailure("列举对象失败", e, b, prefix);
        } catch (ClientException e) {
            throw wrapClientFailure("列举对象失败", e, b, prefix);
        }
    }

    /**
     * 扁平列举：不分 delimiter，分页拉全量后过滤目录占位键.
     */
    private List<OssObjectInfo> listFlatByPrefixAllPages(OSS oss, String bucketName, String prefix) {
        List<OssObjectInfo> all = new ArrayList<>();
        String continuationToken = null;
        while (true) {
            ListObjectsV2Request request = new ListObjectsV2Request(bucketName);
            if (prefix != null) {
                request.setPrefix(prefix);
            }
            request.setMaxKeys(LIST_MAX_KEYS);
            if (continuationToken != null) {
                request.setContinuationToken(continuationToken);
            }
            ListObjectsV2Result result = oss.listObjectsV2(request);
            for (OSSObjectSummary s : result.getObjectSummaries()) {
                if (!isOssFolderPlaceholderKey(s.getKey())) {
                    all.add(new OssObjectInfo(s.getKey(), s.getSize(), s.getLastModified(), s.getETag()));
                }
            }
            if (!Boolean.TRUE.equals(result.isTruncated())) {
                break;
            }
            continuationToken = result.getNextContinuationToken();
        }
        log.debug("OSS listObjectsByPrefix (flat) bucket={} prefix={} count={}", bucketName, prefix, all.size());
        return all;
    }

    /**
     * 带 delimiter 递归：每层分页列举，将 commonPrefixes 入队继续列举；结果不含目录占位键.
     */
    private List<OssObjectInfo> listRecursiveDelimitedAllPages(OSS oss, String bucketName, String rootPrefix,
            String delimiter) {
        List<OssObjectInfo> all = new ArrayList<>();
        String initial = rootPrefix == null ? "" : rootPrefix;
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> scheduled = new HashSet<>();
        scheduled.add(initial);
        queue.addLast(initial);
        while (!queue.isEmpty()) {
            String p = queue.removeFirst();
            String continuationToken = null;
            while (true) {
                ListObjectsV2Request request = new ListObjectsV2Request(bucketName);
                if (StringUtils.isNotBlank(p)) {
                    request.setPrefix(p);
                }
                request.setDelimiter(delimiter);
                request.setMaxKeys(LIST_MAX_KEYS);
                if (continuationToken != null) {
                    request.setContinuationToken(continuationToken);
                }
                ListObjectsV2Result result = oss.listObjectsV2(request);
                for (OSSObjectSummary s : result.getObjectSummaries()) {
                    if (!isOssFolderPlaceholderKey(s.getKey())) {
                        all.add(new OssObjectInfo(s.getKey(), s.getSize(), s.getLastModified(), s.getETag()));
                    }
                }
                if (result.getCommonPrefixes() != null) {
                    for (String cp : result.getCommonPrefixes()) {
                        if (StringUtils.isNotBlank(cp) && scheduled.add(cp)) {
                            queue.addLast(cp);
                        }
                    }
                }
                if (!Boolean.TRUE.equals(result.isTruncated())) {
                    break;
                }
                continuationToken = result.getNextContinuationToken();
            }
        }
        log.debug("OSS listObjectsByPrefix (recursive delimiter={}) bucket={} rootPrefix={} count={}",
                delimiter, bucketName, rootPrefix, all.size());
        return all;
    }

    /**
     * OSS 上常用作「目录占位」的对象键：以 {@code /} 结尾的键不计入文件列表.
     *
     * @param objectKey 对象键
     * @return 是否为目录占位
     */
    private static boolean isOssFolderPlaceholderKey(String objectKey) {
        return StringUtils.isNotBlank(objectKey) && objectKey.endsWith("/");
    }

    /**
     * 遍历指定 OSS 逻辑路径（前缀）下所有对象，返回每个对象的完整对象键（即桶内「文件路径」，统一正斜杠）.
     * <p>前缀会经 {@link #normalizeOssKeyPrefix(String)} 规范化（去前导斜杠、非空时补尾斜杠），避免误匹配兄弟前缀；
     * 键名以 {@code /} 结尾的占位「目录对象」不会加入结果。</p>
     *
     * @param bucket        桶名，可为 null 使用默认桶
     * @param ossPathPrefix OSS 目录前缀，如 {@code etl/backup/main/} 或 {@code etl/backup/main}
     * @return 对象键列表，无对象时返回空列表（非 null）
     */
    public List<String> listObjectKeyPathsUnderPrefix(String bucket, String ossPathPrefix) {
        requireOss();
        if (StringUtils.isBlank(ossPathPrefix)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "ossPathPrefix 不能为空");
        }
        String prefix = normalizeOssKeyPrefix(ossPathPrefix);
        if (StringUtils.isBlank(prefix)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "ossPathPrefix 无效");
        }
        List<OssObjectInfo> infos = listObjectsByPrefix(bucket, prefix, null);
        return infos.stream()
                .map(OssObjectInfo::getObjectKey)
                .filter(k -> StringUtils.isNotBlank(k) && !k.endsWith("/"))
                .collect(Collectors.toList());
    }

    /**
     * 删除指定前缀下全部对象（分批，每批最多 {@value #DELETE_OBJECTS_MAX_KEYS} 条）.
     *
     * @param bucket 桶名，可为 null 使用默认桶
     * @param prefix 前缀，不可为 null（避免误删全桶；若需删全桶应显式传合法前缀）
     * @return 删除的对象键数量
     */
    public int deleteObjectsByPrefix(String bucket, String prefix) {
        requireOss();
        if (prefix == null) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "prefix 不能为 null");
        }
        String b = resolveBucket(bucket);
        List<String> keys = listObjectsByPrefix(bucket, prefix, null).stream()
                .map(OssObjectInfo::getObjectKey)
                .collect(Collectors.toList());
        if (keys.isEmpty()) {
            return 0;
        }
        OSS oss = ossClientProvider.getObject();
        int deleted = 0;
        try {
            for (int i = 0; i < keys.size(); i += DELETE_OBJECTS_MAX_KEYS) {
                int end = Math.min(i + DELETE_OBJECTS_MAX_KEYS, keys.size());
                List<String> chunk = new ArrayList<>(keys.subList(i, end));
                DeleteObjectsRequest del = new DeleteObjectsRequest(b).withKeys(chunk);
                DeleteObjectsResult delResult = oss.deleteObjects(del);
                deleted += delResult.getDeletedObjects() != null ? delResult.getDeletedObjects().size() : chunk.size();
            }
            log.info("OSS deleteObjectsByPrefix bucket={} prefix={} deleted={}", b, prefix, deleted);
            return deleted;
        } catch (OSSException e) {
            throw wrapOssFailure("按前缀批量删除失败", e, b, prefix);
        } catch (ClientException e) {
            throw wrapClientFailure("按前缀批量删除失败", e, b, prefix);
        }
    }

    private void requireOss() {
        if (!isOssAvailable()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0506);
        }
    }

    private String resolveBucket(String bucket) {
        if (StringUtils.isNotBlank(bucket)) {
            return bucket.trim();
        }
        if (StringUtils.isBlank(ossProperties.getBucketName())) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "未指定 bucket 且未配置 oss.bucket-name");
        }
        return ossProperties.getBucketName().trim();
    }

    private static void validateKey(String objectKey) {
        if (StringUtils.isBlank(objectKey)) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "objectKey 不能为空");
        }
    }

    private static CommonException wrapOssFailure(String action, OSSException e, String bucket, String keyOrPrefix) {
        log.warn("OSS {} bucket={} keyOrPrefix={} errorCode={} requestId={}", action, bucket, keyOrPrefix,
                e.getErrorCode(), e.getRequestId());
        String msg = action + ": " + e.getErrorCode() + " — " + StringUtils.defaultString(e.getMessage());
        return new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, msg);
    }

    private static CommonException wrapClientFailure(String action, ClientException e, String bucket, String keyOrPrefix) {
        log.warn("OSS client {} bucket={} keyOrPrefix={} msg={}", action, bucket, keyOrPrefix, e.getMessage());
        String msg = action + ": " + StringUtils.defaultString(e.getMessage());
        return new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0001, msg);
    }
}

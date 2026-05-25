package com.datafusion.common.utils;

import cn.hutool.core.date.DatePattern;
import com.datafusion.common.exception.CommonException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * jackson工具类.
 *
 * @author pxh
 * @version 3.5.2, 2024/4/18
 * @since 1.0.0, 2020/5/19
 */
@Slf4j
public class JacksonUtils {

    /**
     * ObjectMapper.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * json输入不能为空提示信息.
     */
    private static final String JSON_CHECK_MSG = "json不能为空";

    /**
     * bytes不能为空.
     */
    private static final String BYTES_CHECK_MSG = "bytes不能为空";

    /**
     * obj不能为空.
     */
    private static final String OBJ_CHECK_MSG = "obj不能为空";

    /**
     * file不能为空.
     */
    private static final String FILE_CHECK_MSG = "file不能为空";

    /**
     * typeReference不能为空提示信息.
     */
    private static final String TYPEREFERENCE_CHECK_MSG = "typeReference不能为空";

    /**
     * cls不能为空.
     */
    private static final String CLASS_CHECK_MSG = "cls不能为空";

    /**
     * 默认LocalDateTime的日期格式.
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN);

    /**
     * JAVA_TIME_MODULE.
     */
    private static final JavaTimeModule JAVA_TIME_MODULE;

    static {
        JAVA_TIME_MODULE = new JavaTimeModule();
        // LocalDateTime序列化/反序列化 使用指定的日期格式
        JAVA_TIME_MODULE.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        JAVA_TIME_MODULE.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));

        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 不使用科学计数
        MAPPER.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        // null 值不输出(节省内存)
        MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        // 非标配置
        MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    //region 反序列化方法

    /**
     * 将字符串反序列化成java bean对象.
     *
     * @param json json字符串
     * @param cls  Class
     * @param <T>  泛型
     * @return T bean对象
     * @throws JsonProcessingException 异常信息
     */
    public static <T> T str2Bean(String json, Class<T> cls) throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(json, cls);
    }

    /**
     * 将字符串反序列化成java bean对象.
     *
     * @param json          json字符串
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return java bean对象
     * @throws JsonProcessingException 反序列化异常
     */
    public static <T> T str2Bean(String json, TypeReference<T> typeReference) throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        return MAPPER.readValue(json, typeReference);
    }

    /**
     * 尝试将字符串反序列化成指定类对象.
     *
     * @param json json
     * @param cls  Class
     * @param <T>  泛型
     * @return T 指定类对象，失败返回null
     */
    public static <T> T tryStr2Bean(String json, Class<T> cls) {
        try {
            return str2Bean(json, cls);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 尝试将字符串反序列化成指定类对象.
     *
     * @param json          json字符串
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 指定类对象，失败抛出JsonRuntimeException异常
     */
    public static <T> T tryStr2Bean(String json, TypeReference<T> typeReference) {
        try {
            return str2Bean(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 反序列化json.
     *
     * @param bytes byte列表
     * @param cls   类型
     * @param <T>   泛型
     * @return T bean对象
     * @throws IOException 异常信息
     */
    public static <T> T bytes2Bean(byte[] bytes, Class<T> cls) throws IOException {
        AssertUtils.notNull(bytes, JSON_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(bytes, cls);
    }

    /**
     * 反序列化json.
     *
     * @param bytes byte列表
     * @param cls   类型
     * @param <T>   泛型
     * @return T 成功返回bean对象，失败抛出JsonRuntimeException异常
     */
    public static <T> T tryBytes2Bean(byte[] bytes, Class<T> cls) {
        try {
            return bytes2Bean(bytes, cls);
        } catch (IOException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将json文件内容反序列化为java bean.
     *
     * @param file 文件
     * @param cls  类型
     * @param <T>  泛型
     * @return T bean对象
     * @throws IOException 异常信息
     */
    public static <T> T file2Bean(File file, Class<T> cls) throws IOException {
        AssertUtils.notNull(file, FILE_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(file, cls);
    }

    /**
     * 将json文件内容反序列化为java bean.
     *
     * @param file          文件
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 数据
     * @throws IOException 反序列化异常
     */
    public static <T> T file2Bean(File file, TypeReference<T> typeReference) throws IOException {
        AssertUtils.notNull(file, FILE_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        return MAPPER.readValue(file, typeReference);
    }

    /**
     * 将输入流中json内容反序列化为java bean.
     *
     * @param inputStream 输入流
     * @param cls         类型
     * @param <T>         泛型
     * @return T bean对象
     * @throws IOException 异常信息
     */
    public static <T> T stream2Bean(InputStream inputStream, Class<T> cls) throws IOException {
        AssertUtils.notNull(inputStream, "inputStream不能为空");
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(inputStream, cls);
    }

    /**
     * 将输入流中json内容反序列化为java bean.
     *
     * @param inputStream 输入流
     * @param cls         类型
     * @param <T>         泛型
     * @return T 成功返回bean对象，失败抛出JsonRuntimeException
     */
    public static <T> T tryStream2Bean(InputStream inputStream, Class<T> cls) {
        try {
            return stream2Bean(inputStream, cls);
        } catch (IOException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj 对象
     * @param cls Class对象
     * @param <T> 泛型
     * @return 数据
     * @throws JsonProcessingException 异常信息
     */
    public static <T> T obj2Bean(Object obj, Class<T> cls) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        if (obj instanceof String) {
            return str2Bean((String) obj, cls);
        }
        return MAPPER.convertValue(obj, cls);
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj           对象
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 数据
     */
    public static <T> T obj2Bean(Object obj, TypeReference<T> typeReference) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        if (obj instanceof String) {
            return str2Bean((String) obj, typeReference);
        }
        return MAPPER.convertValue(obj, typeReference);
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj 对象
     * @param cls Class对象
     * @param <T> 泛型
     * @return 数据
     */
    public static <T> T tryObj2Bean(Object obj, Class<T> cls) {
        try {
            return obj2Bean(obj, cls);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj           对象
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 数据
     */
    public static <T> T tryObj2Bean(Object obj, TypeReference<T> typeReference) {
        try {
            return obj2Bean(obj, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj 对象
     * @param cls Class对象
     * @param <T> 泛型
     * @return 数据
     * @throws JsonProcessingException 异常信息
     */
    public static <T> T obj2Map(Object obj, Class<T> cls) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        if (obj instanceof String) {
            return str2Bean((String) obj, cls);
        }
        return MAPPER.convertValue(obj, cls);
    }

    /**
     * 将字符串反序列化bean列表.
     *
     * @param json 字符串
     * @param cls  Class
     * @param <T>  泛型
     * @return list  bean列表
     * @throws JsonProcessingException 异常信息
     */
    public static <T> List<T> str2BeanList(String json, Class<T> cls)
            throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, cls));
    }

    /**
     * 将字符串反序列化bean列表.
     *
     * @param json     字符串
     * @param javaType JavaType
     * @param <T>      T
     * @return list bean列表
     * @throws JsonProcessingException 异常信息
     */
    public static <T> List<T> str2BeanList(String json, JavaType javaType)
            throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(javaType, "javaType不能为空");
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, javaType));
    }

    /**
     * 将字符串反序列化成java bean列表.
     *
     * @param json          json
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return java bean列表
     * @throws JsonProcessingException 异常信息
     */
    public static <T> List<T> str2BeanList(String json, TypeReference<List<T>> typeReference)
            throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(typeReference, "typeReference不能为空");
        return MAPPER.readValue(json, typeReference);
    }

    /**
     * 将字符串反序列化bean列表.
     *
     * @param json 字符串
     * @param cls  Class
     * @param <T>  泛型
     * @return list  bean列表，异常抛出JsonRuntimeException异常
     */
    public static <T> List<T> tryStr2BeanList(String json, Class<T> cls) {
        try {
            return str2BeanList(json, cls);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 反序列化列表.
     *
     * @param json          json
     * @param typeReference typeReference
     * @param <T>           T
     * @return list，失败返回null
     */
    public static <T> List<T> tryStr2BeanList(String json, TypeReference<List<T>> typeReference) {
        try {
            return str2BeanList(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将object反序列化为bean列表.
     *
     * @param obj 待反序列化对象
     * @param cls Class
     * @param <T> 泛型
     * @return bean列表
     * @throws JsonProcessingException json处理异常
     */
    public static <T> List<T> obj2BeanList(Object obj, Class<T> cls) throws JsonProcessingException {
        AssertUtils.notNull(obj, JSON_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        if (obj instanceof String) {
            return str2BeanList((String) obj, cls);
        }
        return MAPPER.convertValue(obj, MAPPER.getTypeFactory().constructCollectionType(List.class, cls));
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj           对象
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 数据
     * @throws JsonProcessingException json处理异常
     */
    public static <T> List<T> obj2BeanList(Object obj, TypeReference<List<T>> typeReference)
            throws JsonProcessingException {
        AssertUtils.notNull(obj, JSON_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        if (obj instanceof String) {
            return str2BeanList((String) obj, typeReference);
        }
        return MAPPER.convertValue(obj, typeReference);
    }

    /**
     * 将object反序列化为bean列表.
     *
     * @param obj 待反序列化对象
     * @param cls Class
     * @param <T> 泛型
     * @return bean列表
     */
    public static <T> List<T> tryObj2BeanList(Object obj, Class<T> cls) {
        try {
            return obj2BeanList(obj, cls);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 根据传入的TypeReference进行反序列化.
     *
     * @param obj           对象
     * @param typeReference TypeReference
     * @param <T>           泛型
     * @return 数据
     */
    public static <T> List<T> tryObj2BeanList(Object obj, TypeReference<List<T>> typeReference) {
        AssertUtils.notNull(obj, JSON_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        try {
            return obj2BeanList(obj, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 反序列化列表.
     *
     * @param bytes byte数组
     * @param cls   typeReference
     * @param <T>   T
     * @return list 反序列化数组
     * @throws JsonProcessingException 异常信息
     */
    public static <T> List<T> bytes2BeanList(byte[] bytes, Class<T> cls)
            throws IOException {
        AssertUtils.notNull(bytes, BYTES_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(bytes, MAPPER.getTypeFactory().constructCollectionType(List.class, cls));
    }

    /**
     * 将json文件内容反序列化成java bean列表.
     *
     * @param file 文件
     * @param cls  类
     * @param <T>  T
     * @return list 反序列化列表
     * @throws IOException 异常信息
     */
    public static <T> List<T> file2BeanList(File file, Class<T> cls) throws IOException {
        AssertUtils.notNull(file, FILE_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(file, MAPPER.getTypeFactory().constructCollectionType(List.class, cls));
    }

    /**
     * 将json文件内容反序列化成bean列表.
     *
     * @param file          文件
     * @param typeReference typeReference
     * @param <T>           T
     * @return list bean列表
     * @throws IOException 异常信息
     */
    public static <T> List<T> file2BeanList(File file, TypeReference<List<T>> typeReference)
            throws IOException {
        AssertUtils.notNull(file, FILE_CHECK_MSG);
        AssertUtils.notNull(typeReference, TYPEREFERENCE_CHECK_MSG);
        return MAPPER.readValue(file, typeReference);
    }

    /**
     * 将输入流中json反序列化成bean列表.
     *
     * @param inputStream 输入流
     * @param cls         类
     * @param <T>         泛型
     * @return list bean列表
     * @throws IOException 异常信息
     */
    public static <T> List<T> stream2BeanList(InputStream inputStream, Class<T> cls)
            throws IOException {
        AssertUtils.notNull(inputStream, "inputStream不能为空");
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.readValue(inputStream, MAPPER.getTypeFactory().constructCollectionType(List.class, cls));
    }

    /**
     * 将输入流中json反序列化成bean列表.
     *
     * @param inputStream 输入流
     * @param cls         类
     * @param <T>         泛型
     * @return list 成功返回bean列表，失败抛出JsonRuntimeException
     */
    public static <T> List<T> tryStream2BeanList(InputStream inputStream, Class<T> cls) {
        try {
            return stream2BeanList(inputStream, cls);
        } catch (IOException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将TreeNode反序列化为对应java对象.
     *
     * @param json json
     * @param cls  java类class
     * @param <T>  泛型参数
     * @return 对应java对象
     * @throws JsonProcessingException 反序列化异常
     */
    public static <T> T treeNode2Bean(TreeNode json, Class<T> cls) throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        AssertUtils.notNull(cls, CLASS_CHECK_MSG);
        return MAPPER.treeToValue(json, cls);
    }

    /**
     * 尝试将TreeNode反序列化成指定类对象.
     *
     * @param json json
     * @param cls  java类class
     * @param <T>  泛型参数
     * @return 对应java对象，失败抛出JsonRuntimeException异常
     */
    public static <T> T tryTreeNode2Bean(TreeNode json, Class<T> cls) {
        try {
            return treeNode2Bean(json, cls);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将对象转换为JsonNode.
     *
     * @param obj 待序列化对象
     * @return JsonNode对象
     * @throws JsonProcessingException json处理异常
     */
    public static JsonNode obj2JsonNode(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        if (obj instanceof String) {
            return str2JsonNode((String) obj);
        }
        return MAPPER.convertValue(obj, JsonNode.class);
    }

    /**
     * 将对象转换为JsonNode.
     *
     * @param obj 待序列化对象
     * @return 成功返回JsonNode对象，失败抛出JsonRuntimeException
     */
    public static JsonNode tryObj2JsonNode(Object obj) {
        try {
            return obj2JsonNode(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 从字符串反序列化为JsonNode.
     *
     * @param json json字符串
     * @return JsonNode
     * @throws JsonProcessingException 反序列化异常
     */
    public static JsonNode str2JsonNode(String json) throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        return MAPPER.readTree(json);
    }

    /**
     * 从字符串反序列化为JsonNode，比较明确字符串为json的前提下使用.
     *
     * @param json json字符串
     * @return JsonNode, 失败抛出JsonRuntimeException
     */
    public static JsonNode tryStr2JsonNode(String json) {
        try {
            return str2JsonNode(json);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将Object对象转换为ObjectNode.
     *
     * @param obj 待序列化对象
     * @return JsonNode对象
     * @throws JsonProcessingException json处理异常
     */
    public static ObjectNode obj2ObjectNode(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        if (obj instanceof String) {
            return MAPPER.readValue((String) obj, ObjectNode.class);
        }
        return MAPPER.convertValue(obj, ObjectNode.class);
    }

    /**
     * 将对象转换为JsonNode.
     *
     * @param obj 待序列化对象
     * @return JsonNode对象
     * @throws JsonProcessingException json处理异常
     */
    public static ArrayNode obj2ArrayNode(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        if (obj instanceof String) {
            return str2Bean((String) obj, ArrayNode.class);
        }
        return MAPPER.convertValue(obj, ArrayNode.class);
    }

    /**
     * 将对象转换为JsonNode.
     *
     * @param obj 待序列化对象
     * @return 成功返回JsonNode对象，失败抛出JsonProcessingException异常
     */
    public static ArrayNode tryObj2ArrayNode(Object obj) {
        AssertUtils.notNull(obj, OBJ_CHECK_MSG);
        try {
            return obj2ArrayNode(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 从字符串反序列化为JsonNode.
     *
     * @param json json字符串
     * @param <K>  map key泛型
     * @param <V>  map value泛型
     * @return Map
     * @throws JsonProcessingException 反序列化异常
     */
    public static <K, V> Map<K, V> str2Map(String json)
            throws JsonProcessingException {
        AssertUtils.notNull(json, JSON_CHECK_MSG);
        return MAPPER.readValue(json, new TypeReference<Map<K, V>>() {
        });
    }

    /**
     * 尝试将字符串反序列化为map.
     *
     * @param json json字符串
     * @param <K>  map key泛型
     * @param <V>  map value泛型
     * @return Map，失败抛出JsonRuntimeException异常
     */
    public static <K, V> Map<K, V> tryStr2Map(String json) {
        try {
            return str2Map(json);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将map反序列化成对应的bean.
     *
     * @param map map
     * @param <K> map key泛型
     * @param <V> map value泛型
     * @param <T> Bean类泛型
     * @param cls Class
     * @return java bean对象
     */
    public static <K, V, T> T map2Bean(Map<K, V> map, Class<T> cls) {
        AssertUtils.notNull(map, "map不能为空");
        AssertUtils.notNull(cls, "cls不能为空");
        return MAPPER.convertValue(map, cls);
    }

    //endregion区域区域

    //region 序列化方法

    /**
     * 将Object转换成json字符串.
     *
     * @param obj Object对象
     * @return json字符串
     * @throws JsonProcessingException 异常
     */
    public static String obj2Str(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, "obj不能为空");
        if (obj instanceof String) {
            // 字符串不做序列化处理，直接返回
            return (String) obj;
        }
        return MAPPER.writeValueAsString(obj);
    }

    /**
     * 将Object转换成json字符串.
     *
     * @param obj    Object对象
     * @param pretty 是否美化json
     * @return json字符串
     * @throws JsonProcessingException 异常
     */
    public static String obj2Str(Object obj, boolean pretty) throws JsonProcessingException {
        AssertUtils.notNull(obj, "obj不能为空");
        if (obj instanceof String) {
            // 字符串不做序列化处理，直接返回
            return (String) obj;
        }
        return pretty ? obj2PrettyStr(obj) : obj2Str(obj);
    }

    /**
     * 将Object转换成json字符串.
     *
     * @param obj    Object对象
     * @param pretty 是否美化json
     * @return json字符串
     */
    public static String tryObj2Str(Object obj, boolean pretty) {
        try {
            return pretty ? obj2PrettyStr(obj) : obj2Str(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 尝试将Object序列化成json字符串.
     *
     * @param obj 待序列化对象
     * @return 序列化值，失败抛出JsonRuntimeException异常
     */
    public static String tryObj2Str(Object obj) {
        try {
            return obj2Str(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将Object转换成json字符串并进行美化.
     *
     * @param obj Object对象
     * @return json字符串
     * @throws JsonProcessingException json处理异常
     */
    public static String obj2PrettyStr(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, "obj不能为空");
        if (obj instanceof String) {
            // 字符串不做序列化处理，直接返回
            return (String) obj;
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    /**
     * 将Object序列化成byte数组.
     *
     * @param obj obj
     * @return byte数组
     * @throws JsonProcessingException json处理异常
     */
    public static byte[] obj2Bytes(Object obj) throws JsonProcessingException {
        AssertUtils.notNull(obj, "obj不能为空");
        if (obj instanceof String) {
            return ((String) obj).getBytes(StandardCharsets.UTF_8);
        }
        return MAPPER.writeValueAsBytes(obj);
    }

    /**
     * 尝试将Object序列化成byte数组.
     *
     * @param obj 待序列化对象
     * @return 序列化值，失败抛出JsonRuntimeException异常
     */
    public static byte[] tryObj2Bytes(Object obj) {
        try {
            return obj2Bytes(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将实体对象转换成json并写入指定文件.
     *
     * @param file 文件
     * @param obj  Object对象
     * @throws IOException io异常
     */
    public static void obj2File(File file, Object obj) throws IOException {
        AssertUtils.notNull(file, "file不能为空");
        AssertUtils.notNull(obj, "obj不能为空");
        MAPPER.writeValue(file, obj);
    }

    /**
     * 将对象序列化成json并写入指定文件，异常抛出JsonRuntimeException.
     *
     * @param file 文件
     * @param obj  Object对象
     */
    public static void tryObj2File(File file, Object obj) {
        try {
            obj2File(file, obj);
        } catch (IOException e) {
            log.error("JSON序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将实体对象序列化成json并写入指定输出流.
     *
     * @param outputStream 输出流
     * @param obj          Object对象
     * @throws IOException io异常
     */
    public static void obj2Stream(OutputStream outputStream, Object obj) throws IOException {
        AssertUtils.notNull(outputStream, "outputStream不能为空");
        MAPPER.writeValue(outputStream, obj);
    }

    /**
     * 尝试将实体对象序列化成json并写入指定输出流，异常抛出JsonRuntimeException.
     *
     * @param outputStream 输出流
     * @param obj          Object对象
     */
    public static void tryObj2Stream(OutputStream outputStream, Object obj) {
        try {
            obj2Stream(outputStream, obj);
        } catch (IOException e) {
            log.error("JSON序列化异常: ", e);
            throw new CommonException("JSON反序列化异常");
        }
    }

    /**
     * 将对象序列化成json字符串并写入Writer.
     *
     * @param writer Writer
     * @param obj    待序列化数据
     * @throws IOException io异常
     */
    public static void obj2Writer(Writer writer, Object obj) throws IOException {
        AssertUtils.notNull(writer, "writer不能为空");
        AssertUtils.notNull(obj, "obj不能为空");
        MAPPER.writeValue(writer, obj);
    }

    //endregion

    //region 其他

    /**
     * 创建空ObjectNode对象.
     *
     * @return 空ObjectNode对象
     */
    public static ObjectNode createObjectNode() {
        return MAPPER.createObjectNode();
    }

    /**
     * 创建空ArrayNode对象.
     *
     * @return 空的ArrayNode对象
     */
    public static ArrayNode createArrayNode() {
        return MAPPER.createArrayNode();
    }

    /**
     * 将 tagSet 数组包装为 {"tagSet": [...]} 格式.
     * 兼容旧数据：如果已经是新格式则直接返回。
     *
     * @param tagArray tagSet数组
     * @param fieldName 键值key
     * @return 包装后的JsonNode
     */
    public static JsonNode wrapTagSet(JsonNode tagArray, String fieldName) {
        if (tagArray == null || tagArray.isNull() || (tagArray.isArray() && tagArray.isEmpty())) {
            return null;
        }
        if (tagArray.has(fieldName)) {
            if (tagArray.get(fieldName).isArray() && tagArray.get(fieldName).isEmpty()) {
                return null;
            }
            return tagArray;
        }
        ObjectNode wrapper = createObjectNode();
        wrapper.set(fieldName, tagArray);
        return wrapper;
    }

    /**
     * 从 edgeProp 中解包出 tagSet 数组.
     * 兼容旧数据：如果直接是数组格式则原样返回。
     *
     * @param edgeProp edgeProp字段值
     * @param fieldName 键值key
     * @return 解包后的tagSet数组
     */
    public static JsonNode unwrapTagSet(JsonNode edgeProp, String fieldName) {
        if (edgeProp == null || edgeProp.isNull()) {
            return createArrayNode();
        }
        if (edgeProp.has(fieldName)) {
            return edgeProp.get(fieldName);
        }
        return edgeProp;
    }

    /**
     * 判断json是否为空.
     *
     * @param node 待检测jsonnode
     * @return 为空返回true，反之返回false
     */
    public static boolean isEmpty(JsonNode node) {
        return null == node || node.isEmpty();
    }
    //endregion

    /**
     * 将给定的 Java 实体对象转换为 Jackson 的 JsonNode.
     *
     * @param pojo 待转换的 Java 实体对象 (Plain Old Java Object)。
     *             它可以是任何普通的 Java 对象，或者 List/Map 等集合类型。
     * @return 转换后的 JsonNode 对象。如果转换失败，返回 null 或抛出 IOException。
     * @throws IOException 如果对象无法被序列化为 JSON 格式。
     */
    public static JsonNode convertPojoToJsonNode(Object pojo) throws IOException {
        if (pojo == null) {
            return null; // 或者可以返回 objectMapper.createObjectNode() 等空 JsonNode
        }
        // valueToTree() 方法将 Java 对象序列化为一个 JsonNode 树
        return MAPPER.valueToTree(pojo);
    }

    /**
     * 将给定的 Java 实体对象转换为 JsonNode，并捕获 IOException，
     * 在发生异常时返回 null 或记录日志.
     *
     * @param pojo 待转换的 Java 实体对象。
     * @return 转换后的 JsonNode 对象，如果发生错误则返回 null。
     */
    public static JsonNode convertPojoToJsonNodeSafely(Object pojo) {
        try {
            return convertPojoToJsonNode(pojo);
        } catch (IOException e) {
            System.err.println("Error converting POJO to JsonNode: " + e.getMessage());
            // 生产环境中应该使用日志框架记录异常
            // log.error("Error converting POJO to JsonNode", e);
            return null;
        }
    }

    /**
     * 将 JsonNode 安全地转换为指定的 POJO 对象.
     * 如果转换失败，返回 null。
     *
     * @param jsonNode 待转换的 JsonNode
     * @param clazz 目标 POJO 类
     * @param <T> 目标类型
     * @return 转换后的对象，失败则为 null
     */
    public static <T> T convertJsonNodeToPojoSafely(JsonNode jsonNode, Class<T> clazz) {
        if (jsonNode == null || clazz == null) {
            return null;
        }
        try {
            return MAPPER.treeToValue(jsonNode, clazz);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to POJO of type {}. JsonNode: {}",
                    clazz.getName(), jsonNode.toString(), e);
            // 转换失败，安全返回 null
            return null;
        }
    }
}

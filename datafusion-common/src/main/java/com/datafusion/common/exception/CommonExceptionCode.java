package com.datafusion.common.exception;

/**
 * 错误码定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2022/01/18
 * @since 2021/11/25
 */
public enum CommonExceptionCode implements ErrorCode {
    
    /**
     * 其他未知错误.
     */
    OTHER("999", "其他未知错误,详细信息:"),
    
    /**
     * 类未找到错误.
     */
    ClassNotFound("003", "类未找到:"),
    
    /**
     * 不支持的返回类型.
     */
    UnsupportedReturnType("010", "SqlMapper中不支持的此返回类型:"),
    
    /**
     * 找不到对应的数据源.
     */
    DataSourceNotFound("208", "找不到对应的数据源"),
    /**
     * 注解 SqlDs 异常定义.
     */
    DsMustDataSourceInfo("219", "@SqlDs 类型必须是 DataSourceInfo "),
    
    /**
     * 注解 @SqlBatchData 异常定义.
     */
    SqlBatchDataMustSupport("210", "@SqlBatchData 类型必须是支持类型: List<Map<String, Object>>"),
    
    /**
     * 注解 @SqlParam 异常定义.
     */
    SqlBatchDataMustHasValue("211", "@SqlBatchData 的value 不能为空"),
    
    
    /**
     * 异常映射(单词定义驼峰式写法).
     */
    CfgHiveFileNotExists("991", "cfg-hive.properties文件不存在"),
    /**
     * 异常映射(单词定义驼峰式写法).
     */
    ColumnNotInResult("901", "查询结果集表中未包含展示字段"),
    /**
     * hiveSql执行失败.
     */
    HiveSqlExecFail("900", "hiveSql执行失败!"),
    /**
     * 异常映射(单词定义驼峰式写法).
     */
    ResolveType("888", "请确定XPath中解析成List(N)还是单个String(S)"),
    /**
     * 配置文件不存在.
     */
    FileNotExist("777", "文件路径不存在"),
    /**
     * 配置文件不存在.
     */
    TemplateFileNotExist("777", "模板文件不存在,加载失败"),
    /**
     * 创建目录失败.
     */
    CreateDirFailure("701", "创建目录失败,请检查目录是否已存在或无上层目录"),
    /**
     * 创建文件失败.
     */
    CreateFileFailure("711", "创建文,请检查目录是否已存"),
    /**
     * 执行引擎失败.
     */
    ExecEngineFailure("600", "执行任务失败,没有引擎类型EngineType"),
    /**
     * 方法没有正确的返回值.
     */
    NoRightReturnType("111", "方法没有正确的返回值,目前只支持 List<Object> 和 Boolean"),
    /**
     * sql中日期计算格式错误.
     */
    DayCalculateErr("211", "sql中日期计算格式错误[day,-1D,YYYYMMDD]"),
    /**
     * sql中有日期,参数中无日期.
     */
    NoDayErr("212", "sql中有日期,参数中无日期"),
    
    /**
     * 注解 SqlDs 异常定义.
     */
    DsMustObj("219", "@SqlDs 类型必须是 DataSourceInfo "),
    /**
     * 无效内置参数.
     */
    NotValidBuiltInParam("301", "无效内置参数"),
    /**
     * 无此表视图.
     */
    NoTableView("302", "无此表视图"),
    /**
     * 输入参数或源过多.
     */
    TooMoreInPut("303", "输入参数或源过多,请处理"),
    /**
     * 非法数据源.
     */
    NoDataSources("300", "非法数据源"),
    
    /**
     * 提交任务失败.
     */
    SubmitFailed("444", "提交任务失败"),
    
    /**
     * 未指定plugin.
     */
    NoPlugin("400", "未指定插件"),
    
    /**
     * 未知错误.
     */
    UnKnown("445", "未知错误，请查看日志"),
    
    /**
     * 服务端错误.
     */
    ServerError("500", "服务端错误"),
    /**
     * 注解 @SqlParam 异常定义.
     */
    ParamsMustSupport("213", "@SqlParam 类型必须是支持类型: T, List<T> , map<String,Object>"),
    
    /**
     * 注解 @SqlParam 异常定义.
     */
    ParamsMustHasValue("214", "@SqlParam 类型是 List<Object> 和 Object 时, @SqlParam 的 value 必须有值且与sql文件中一致"),
    /**
     * 注解 @SqlParam 批量参数异常定义.
     */
    TooManyBatchParams("215", "批量模式下只支持一个被@SqlParam注解的集合参数"),
    /**
     * 注解 @SqlParam 批量参数异常定义.
     */
    BatchParamNotCollection("215", "批量模式下只支持一个被@SqlParam注解的集合参数"),
    /**
     * 注解 @SqlParam 批量参数异常定义.
     */
    InconsistentCollectionType("215", "批量模式下只支持一个被@SqlParam注解的集合参数"),
    
    /**
     * 接口获取成功.
     */
    SUCCESS("000", "调用成功"),
    
    /**
     * 授权失败.
     */
    AUTH_FAILED("401", "授权失败"),
    /**
     * 服务端异常.
     */
    FORBIDDEN("403", "无权操作"),
    /**
     * 客户端错误,业务代码中不要使用.
     */
    BAD_REQUEST("400", "请求参数有误"),
    /**
     * 路径路由失败,客户端错误，业务代码中不要使用.
     */
    NOT_FOUND("404", "请求url不存在"),
    /**
     * 方法不支持,客户端错误,业务代码中不要使用.
     */
    METHOD_NOT_ALLOWED("405", "请求方法不对"),
    /**
     * 客户端错误,业务代码中不要使用.
     */
    NOT_ACCEPTABLE("406", "Not Acceptable"),
    /**
     * 执行超时.
     */
    REQUEST_TIMEOUT("408", "访问超时"),
    /**
     * 目前仅用来处理设备离线.
     */
    CONFLICT("409", "Conflict"),
    /**
     * 业务失败,客户端错误,业务代码中不要使用.
     */
    UNSUPPORTED_MEDIA_TYPE("415", "Content-Type类型有误"),
    /**
     * 服务端异常.
     */
    SERVER_ERROR("500", "服务端异常"),
    /**
     * 服务端异常.
     */
    BAD_GATEWAY("502", "服务连不上"),
    /**
     * 服务无法访问,服务端错误,业务代码中不要使用.
     */
    SERVICE_UNAVAILABLE("503", "服务不可用"),
    /**
     * 网关连接服务超时.
     */
    GATEWAY_TIMEOUT("504", "服务超时"),
    /**
     * 服务间调用降级.
     */
    SERVICE_FALLBACK("50000", "服务调用降级"),
    /**
     * 服务端异常.
     */
    RETRY_LATER("50001", "请稍后重试"),
    /**
     * 找不到数据.
     */
    RECORD_NOT_FOUND("50002", "找不到记录"),
    /**
     * 数据异常.
     */
    REDIS_DATA_BROKEN("50003", "数据异常"),
    /**
     * 业务失败.
     */
    BUSINESS_FAILURE("50004", "业务失败"),
    /**
     * 传参有问题.
     */
    PARAMS_INVALID("50005", "传参无效"),
    /**
     * 唯一性校验失败.
     */
    UNIQUE_VIOLATION("50006", "唯一性校验失败"),
    /**
     * 数据有问题.
     */
    DATA_CORRUPTION("50007", "数据有问题，请联系管理员"),
    /**
     * 数据不完整.
     */
    DATA_MISS("50008", "数据不完整，请联系管理员"),
    /**
     * 数据不完整.
     */
    BAD_CREDENTIAL("50009", "账户名或密码不对"),
    /**
     * 找不到数据.
     */
    RECORD_NOT_USING("50010", "记录已禁用"),
    
    /**
     * 找不到数据.
     */
    FATAL_ERROR("50011", "系统级故障，请联系管理员"),
    /**
     * 记录已存在.
     */
    ALREADY_EXIST("50012", "记录已存在"),
    /**
     * 校验失败，该错误码返回时调用端通过APIResultTO中validateFailMessages获取具体失败信息
     * 仅用于hibernate validate场景.
     */
    VALID_FAILED("50013", "校验失败"),
    /**
     * 登录已过期，需重新登录.
     */
    TOKEN_EXPIRED("50014", "登录已过期"),
    /**
     * 处理中.
     */
    PROCESSING("50015", "处理中"),
    
    /**
     * RPC 控制失败，当前状态不支持该操作.
     */
    RPC_STATUS_NOT_SUPPORT("50101", "当前状态不支持该操作"),
    
    /**
     * sql查询错误.
     */
    SQL_QUERY_ERROR("10080", "sql查询错误");
    
    /**
     * 错误码.
     */
    private String code;
    
    /**
     * 错误信息.
     */
    private String description;
    
    /**
     * 构造方法.
     *
     * @param code        错误码
     * @param description 错误信息
     */
    private CommonExceptionCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getDefaultKey() {
        return ErrorCode.super.getDefaultKey();
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}

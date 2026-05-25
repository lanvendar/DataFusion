package com.datafusion.common.exception;

/**
 * 基本错误码枚举（来自阿里编码规范泰山版） 错误产生来源分为 A/B/C，A 表示错误来源于用户，比如参数错误，用户安装版本过低，用户支付 超时等问题；B 表示错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题；C
 * 表示错误来源 于第三方服务，比如 CDN 服务出错，消息投递超时等问题；四位数字编号从 0001 到 9999，大类之间的 步长间距预留 100.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/27
 * @since 2025/8/27
 */
@SuppressWarnings("checkstyle:JavadocVariable")
public enum ErrorCodeEnum implements ErrorCode {
    
    /**
     * 表示正常执行.
     */
    SUCCESS("00000", "成功", "be_ErrorCode_00000"),
    
    /**
     * A类错误来源于用户.
     */
    USER_ERROR_A0001("A0001", "用户端错误", "be_ErrorCode_A0001"),
    
    /**
     * A0100为二级宏观错误码，表示A0100~A0199都属于用户注册错误.
     */
    USER_ERROR_A0100("A0100", "用户注册错误", "be_ErrorCode_A0100"),
    USER_ERROR_A0101("A0101", "用户未同意隐私协议", "be_ErrorCode_A0101"),
    USER_ERROR_A0102("A0102", "注册国家或地区受限", "be_ErrorCode_A0102"),
    USER_ERROR_A0110("A0110", "用户名校验失败", "be_ErrorCode_A0110"),
    USER_ERROR_A0111("A0111", "用户名已存在", "be_ErrorCode_A0111"),
    USER_ERROR_A0112("A0112", "用户名包含敏感词", "be_ErrorCode_A0112"),
    USER_ERROR_A0113("A0113", "用户名包含特殊字符", "be_ErrorCode_A0113"),
    USER_ERROR_A0120("A0120", "密码校验失败", "be_ErrorCode_A0120"),
    USER_ERROR_A0121("A0121", "密码长度不够", "be_ErrorCode_A0121"),
    USER_ERROR_A0122("A0122", "密码强度不够", "be_ErrorCode_A0122"),
    USER_ERROR_A0130("A0130", "校验码输入错误", "be_ErrorCode_A0130"),
    USER_ERROR_A0131("A0131", "短信校验码输入错误", "be_ErrorCode_A0131"),
    USER_ERROR_A0132("A0132", "邮件校验码输入错误", "be_ErrorCode_A0132"),
    USER_ERROR_A0133("A0133", "语音校验码输入错误", "be_ErrorCode_A0133"),
    USER_ERROR_A0140("A0140", "用户证件异常", "be_ErrorCode_A0140"),
    USER_ERROR_A0141("A0141", "用户证件类型未选择", "be_ErrorCode_A0141"),
    USER_ERROR_A0142("A0142", "大陆身份证编号校验非法", "be_ErrorCode_A0142"),
    USER_ERROR_A0143("A0143", "护照编号校验非法", "be_ErrorCode_A0143"),
    USER_ERROR_A0144("A0144", "军官证编号校验非法", "be_ErrorCode_A0144"),
    USER_ERROR_A0150("A0150", "用户基本信息校验失败", "be_ErrorCode_A0150"),
    USER_ERROR_A0151("A0151", "手机格式校验失败", "be_ErrorCode_A0151"),
    USER_ERROR_A0152("A0152", "地址格式校验失败", "be_ErrorCode_A0152"),
    USER_ERROR_A0153("A0153", "邮箱格式校验失败", "be_ErrorCode_A0153"),
    USER_INVALID_PARAM_A0154("A0154", "用户参数校验失败", "be_ErrorCode_A0154"),
    /**
     * A0200为二级宏观错误码，表示A0200~A0299都属于用户登陆异常.
     */
    USER_ERROR_A0200("A0200", "用户登陆异常", "be_ErrorCode_A0200"),
    USER_ERROR_A0201("A0201", "用户账户不存在", "be_ErrorCode_A0201"),
    USER_ERROR_A0202("A0202", "用户账户被冻结", "be_ErrorCode_A0202"),
    USER_ERROR_A0203("A0203", "用户账户已作废", "be_ErrorCode_A0203"),
    USER_ERROR_A0210("A0210", "用户密码错误", "be_ErrorCode_A0210"),
    USER_ERROR_A0211("A0211", "用户输入密码次数超限", "be_ErrorCode_A0211"),
    USER_ERROR_A0220("A0220", "用户身份校验失败", "be_ErrorCode_A0220"),
    USER_ERROR_A0221("A0221", "用户指纹识别失败", "be_ErrorCode_A0221"),
    USER_ERROR_A0222("A0222", "用户面容识别失败", "be_ErrorCode_A0222"),
    USER_ERROR_A0223("A0223", "用户未获得第三方登陆授权", "be_ErrorCode_A0223"),
    USER_ERROR_A0230("A0230", "用户登陆已过期", "be_ErrorCode_A0230"),
    USER_ERROR_A0240("A0240", "用户验证码错误", "be_ErrorCode_A0240"),
    USER_ERROR_A0241("A0241", "用户验证码尝试次数超限", "be_ErrorCode_A0241"),
    
    /**
     * A0300~A0399都属于访问权限异常.
     */
    USER_ERROR_A0300("A0300", "访问权限异常", "be_ErrorCode_A0300"), USER_ERROR_A0301("A0301", "访问未授权", "be_ErrorCode_A0301"),
    USER_ERROR_A0302("A0302", "正在授权中", "be_ErrorCode_A0302"),
    USER_ERROR_A0303("A0303", "用户授权申请被拒绝", "be_ErrorCode_A0303"),
    USER_ERROR_A0310("A0310", "因访问对象隐私设置被拦截", "be_ErrorCode_A0310"),
    USER_ERROR_A0311("A0311", "授权已过期", "be_ErrorCode_A0311"),
    USER_ERROR_A0312("A0312", "无权限使用API", "be_ErrorCode_A0312"),
    USER_ERROR_A0320("A0320", "用户访问被拦截", "be_ErrorCode_A0320"),
    USER_ERROR_A0321("A0321", "黑名单用户", "be_ErrorCode_A0321"), USER_ERROR_A0322("A0322", "账号被冻结", "be_ErrorCode_A0322"),
    USER_ERROR_A0323("A0323", "非法IP地址", "be_ErrorCode_A0323"),
    USER_ERROR_A0324("A0324", "网关访问受限", "be_ErrorCode_A0324"), USER_ERROR_A0325("A0325", "地域黑名单", "be_ErrorCode_A0325"),
    USER_ERROR_A0330("A0330", "服务已欠费", "be_ErrorCode_A0330"), USER_ERROR_A0340("A0340", "用户签名异常", "be_ErrorCode_A0340"),
    USER_ERROR_A0341("A0341", "RSA签名错误", "be_ErrorCode_A0341"),
    
    // A0400~A0499都属于用户请求参数错误
    /**
     * A0400~A0499都属于用户请求参数错误.
     */
    USER_ERROR_A0400("A0400", "用户请求参数错误", "be_ErrorCode_A0400"),
    USER_ERROR_A0401("A0401", "包含非法恶意跳转链接", "be_ErrorCode_A0401"),
    USER_ERROR_A0402("A0402", "无效的用户输入", "be_ErrorCode_A0402"),
    USER_ERROR_A0410("A0410", "请求必填参数为空", "be_ErrorCode_A0410"),
    USER_ERROR_A0411("A0411", "用户订单号为空", "be_ErrorCode_A0411"),
    USER_ERROR_A0412("A0412", "订购数量为空", "be_ErrorCode_A0412"),
    USER_ERROR_A0413("A0413", "缺少时间戳参数", "be_ErrorCode_A0413"),
    USER_ERROR_A0414("A0414", "非法的时间戳参数", "be_ErrorCode_A0414"),
    USER_ERROR_A0420("A0420", "请求参数值超出允许的范围", "be_ErrorCode_A0420"),
    USER_ERROR_A0421("A0421", "参数格式不匹配", "be_ErrorCode_A0421"),
    USER_ERROR_A0422("A0422", "地址不在服务范围", "be_ErrorCode_A0422"),
    USER_ERROR_A0423("A0423", "时间不在服务范围", "be_ErrorCode_A0423"),
    USER_ERROR_A0424("A0424", "金额超出限制", "be_ErrorCode_A0424"),
    USER_ERROR_A0425("A0425", "数量超出限制", "be_ErrorCode_A0425"),
    USER_ERROR_A0426("A0426", "请求批量处理总个数超出限制", "be_ErrorCode_A0426"),
    USER_ERROR_A0427("A0427", "请求JSON解析失败", "be_ErrorCode_A0427"),
    USER_ERROR_A0430("A0430", "用户输入内容非法", "be_ErrorCode_A0430"),
    USER_ERROR_A0431("A0431", "包含违禁敏感词", "be_ErrorCode_A0431"),
    USER_ERROR_A0432("A0432", "图片包含违禁信息", "be_ErrorCode_A0432"),
    USER_ERROR_A0433("A0433", "文件侵犯版权", "be_ErrorCode_A0433"),
    USER_ERROR_A0440("A0440", "用户操作异常", "be_ErrorCode_A0440"),
    USER_ERROR_A0441("A0441", "用户支付超时", "be_ErrorCode_A0441"),
    USER_ERROR_A0442("A0442", "确认订单超时", "be_ErrorCode_A0442"),
    USER_ERROR_A0443("A0443", "订单已关闭", "be_ErrorCode_A0443"),
    
    /**
     * A0500~A0599都属于用户请求服务异常.
     */
    USER_ERROR_A0500("A0500", "用户请求服务异常", "be_ErrorCode_A0500"),
    USER_ERROR_A0501("A0501", "请求次数超出限制", "be_ErrorCode_A0501"),
    USER_ERROR_A0502("A0502", "请求并发数超出限制", "be_ErrorCode_A0502"),
    USER_ERROR_A0503("A0503", "用户操作请等待", "be_ErrorCode_A0503"),
    USER_ERROR_A0504("A0504", "WebSocket连接异常", "be_ErrorCode_A0504"),
    USER_ERROR_A0505("A0505", "WebSocket连接断开", "be_ErrorCode_A0505"),
    USER_ERROR_A0506("A0506", "用户重复请求", "be_ErrorCode_A0506"),
    
    /**
     * A0600~A0699都属于用户资源异常.
     */
    USER_ERROR_A0600("A0600", "用户资源异常", "be_ErrorCode_A0600"),
    USER_ERROR_A0601("A0601", "账户余额不足", "be_ErrorCode_A0601"),
    USER_ERROR_A0602("A0602", "用户磁盘空间不足", "be_ErrorCode_A0602"),
    USER_ERROR_A0603("A0603", "用户内存空间不足", "be_ErrorCode_A0603"),
    USER_ERROR_A0604("A0604", "用户OSS容量不足", "be_ErrorCode_A0604"),
    USER_ERROR_A0605("A0605", "用户配额已用光", "be_ErrorCode_A0605"),
    
    USER_ERROR_A0700("A0700", "用户上传文件异常", "be_ErrorCode_A0700"),
    USER_ERROR_A0701("A0701", "用户上传文件类型不匹配", "be_ErrorCode_A0701"),
    USER_ERROR_A0702("A0702", "用户上传文件太大", "be_ErrorCode_A0702"),
    USER_ERROR_A0703("A0703", "用户上传图片太大", "be_ErrorCode_A0703"),
    USER_ERROR_A0704("A0704", "用户上传视频太大", "be_ErrorCode_A0704"),
    USER_ERROR_A0705("A0705", "用户上传压缩文件太大", "be_ErrorCode_A0705"),
    USER_ERROR_A0706("A0706", "用户上传文件校验错误", "be_ErrorCode_A0706"),
    
    USER_ERROR_A0800("A0800", "用户当前版本异常", "be_ErrorCode_A0800"),
    USER_ERROR_A0801("A0801", "用户安装版本与系统不匹配", "be_ErrorCode_A0801"),
    USER_ERROR_A0802("A0802", "用户安装版本过低", "be_ErrorCode_A0802"),
    USER_ERROR_A0803("A0803", "用户安装版本过高", "be_ErrorCode_A0803"),
    USER_ERROR_A0804("A0804", "用户安装版本已过期", "be_ErrorCode_A0804"),
    USER_ERROR_A0805("A0805", "用户API请求版本不匹配", "be_ErrorCode_A0805"),
    USER_ERROR_A0806("A0806", "用户API请求版本过高", "be_ErrorCode_A0806"),
    USER_ERROR_A0807("A0807", "用户API请求版本过低", "be_ErrorCode_A0807"),
    
    USER_ERROR_A0900("A0900", "用户隐私未授权", "be_ErrorCode_A0900"),
    USER_ERROR_A0901("A0901", "用户隐私未签署", "be_ErrorCode_A0901"),
    USER_ERROR_A0902("A0902", "用户摄像头未授权", "be_ErrorCode_A0902"),
    USER_ERROR_A0903("A0903", "用户相机未授权", "be_ErrorCode_A0903"),
    USER_ERROR_A0904("A0904", "用户图片库未授权", "be_ErrorCode_A0904"),
    USER_ERROR_A0905("A0905", "用户文件未授权", "be_ErrorCode_A0905"),
    USER_ERROR_A0906("A0906", "用户位置信息未授权", "be_ErrorCode_A0906"),
    USER_ERROR_A0907("A0907", "用户通讯录未授权", "be_ErrorCode_A0907"),
    
    USER_ERROR_A1000("A1000", "用户设备异常", "be_ErrorCode_A1000"),
    USER_ERROR_A1001("A1001", "用户相机异常", "be_ErrorCode_A1001"),
    USER_ERROR_A1002("A1002", "用户麦克风异常", "be_ErrorCode_A1002"),
    USER_ERROR_A1003("A1003", "用户听筒异常", "be_ErrorCode_A1003"),
    USER_ERROR_A1004("A1004", "用户扬声器异常", "be_ErrorCode_A1004"),
    USER_ERROR_A1005("A1005", "用户GPS定位异常", "be_ErrorCode_A1005"),
    
    SYSTEM_ERROR_B0001("B0001", "系统执行出错", "be_ErrorCode_B0001"),
    
    SYSTEM_ERROR_B0100("B0100", "系统执行超时", "be_ErrorCode_B0100"),
    SYSTEM_ERROR_B0101("B0101", "系统订单处理超时", "be_ErrorCode_B0101"),
    
    SYSTEM_ERROR_B0200("B0200", "系统容灾功能被触发", "be_ErrorCode_B0200"),
    SYSTEM_ERROR_B0210("B0210", "系统限流", "be_ErrorCode_B0210"),
    SYSTEM_ERROR_B0220("B0220", "系统功能降级", "be_ErrorCode_B0220"),
    
    SYSTEM_ERROR_B0300("B0300", "系统资源异常", "be_ErrorCode_B0300"),
    SYSTEM_ERROR_B0310("B0310", "系统资源耗尽", "be_ErrorCode_B0310"),
    SYSTEM_ERROR_B0311("B0311", "系统磁盘空间耗尽", "be_ErrorCode_B0311"),
    SYSTEM_ERROR_B0312("B0312", "系统内存耗尽", "be_ErrorCode_B0312"),
    SYSTEM_ERROR_B0313("B0313", "文件句柄耗尽", "be_ErrorCode_B0313"),
    SYSTEM_ERROR_B0314("B0314", "系统连接池耗尽", "be_ErrorCode_B0314"),
    SYSTEM_ERROR_B0315("B0315", "系统线程池耗尽", "be_ErrorCode_B0315"),
    SYSTEM_ERROR_B0320("B0320", "系统资源访问异常", "be_ErrorCode_B0320"),
    SYSTEM_ERROR_B0321("B0321", "系统读取磁盘文件失败", "be_ErrorCode_B0321"),
    
    SERVICE_ERROR_C0001("C0001", "调用第三方服务出错", "be_ErrorCode_C0001"),
    
    SERVICE_ERROR_C0100("C0100", "中间件服务出错", "be_ErrorCode_C0100"),
    SERVICE_ERROR_C0110("C0110", "RPC服务出错", "be_ErrorCode_C0110"),
    SERVICE_ERROR_C0111("C0111", "RPC服务未找到", "be_ErrorCode_C0111"),
    SERVICE_ERROR_C0112("C0112", "RPC服务未注册", "be_ErrorCode_C0112"),
    SERVICE_ERROR_C0113("C0113", "接口不存在", "be_ErrorCode_C0113"),
    SERVICE_ERROR_C0120("C0120", "消息服务出错", "be_ErrorCode_C0120"),
    SERVICE_ERROR_C0121("C0121", "消息投递出错", "be_ErrorCode_C0121"),
    SERVICE_ERROR_C0122("C0122", "消息消费出错", "be_ErrorCode_C0122"),
    SERVICE_ERROR_C0123("C0123", "消息订阅出错", "be_ErrorCode_C0123"),
    SERVICE_ERROR_C0124("C0124", "消息分组未查到", "be_ErrorCode_C0124"),
    SERVICE_ERROR_C0130("C0130", "缓存服务出错", "be_ErrorCode_C0130"),
    SERVICE_ERROR_C0131("C0131", "key长度超过限制", "be_ErrorCode_C0131"),
    SERVICE_ERROR_C0132("C0132", "value长度超过限制", "be_ErrorCode_C0132"),
    SERVICE_ERROR_C0133("C0133", "存储容量已满", "be_ErrorCode_C0133"),
    SERVICE_ERROR_C0134("C0134", "不支持的数据格式", "be_ErrorCode_C0134"),
    SERVICE_ERROR_C0140("C0140", "配置服务出错", "be_ErrorCode_C0140"),
    SERVICE_ERROR_C0150("C0150", "网络资源服务出错", "be_ErrorCode_C0150"),
    SERVICE_ERROR_C0151("C0151", "VPN服务出错", "be_ErrorCode_C0151"),
    SERVICE_ERROR_C0152("C0152", "CDN服务出错", "be_ErrorCode_C0152"),
    SERVICE_ERROR_C0153("C0153", "域名解析服务出错", "be_ErrorCode_C0153"),
    SERVICE_ERROR_C0154("C0154", "网关服务出错", "be_ErrorCode_C0154"),
    SERVICE_ERROR_C0200("C0200", "第三方系统执行超时", "be_ErrorCode_C0200"),
    SERVICE_ERROR_C0210("C0210", "RPC执行超时", "be_ErrorCode_C0210"),
    SERVICE_ERROR_C0220("C0220", "消息投递超时", "be_ErrorCode_C0220"),
    SERVICE_ERROR_C0230("C0230", "缓存服务超时", "be_ErrorCode_C0230"),
    SERVICE_ERROR_C0240("C0240", "配置服务超时", "be_ErrorCode_C0240"),
    SERVICE_ERROR_C0250("C0250", "数据库服务超时", "be_ErrorCode_C0250"),
    SERVICE_ERROR_C0300("C0300", "数据库服务出错", "be_ErrorCode_C0300"),
    SERVICE_ERROR_C0311("C0311", "表不存在", "be_ErrorCode_C0311"),
    SERVICE_ERROR_C0312("C0312", "列不存在", "be_ErrorCode_C0312"),
    SERVICE_SQL_EXCUTE_ERROR_C0313("C0313", "数据库执行SQL异常", "be_ErrorCode_C0313"),
    SERVICE_ERROR_C0321("C0321", "多表关联中存在多个相同名称的列", "be_ErrorCode_C0321"),
    SERVICE_ERROR_C0331("C0331", "数据库死锁", "be_ErrorCode_C0331"),
    SERVICE_ERROR_C0341("C0341", "主键冲突", "be_ErrorCode_C0341"),
    SERVICE_ERROR_C0400("C0400", "第三方容灾系统被触发", "be_ErrorCode_C0400"),
    SERVICE_ERROR_C0401("C0401", "第三方系统限流", "be_ErrorCode_C0401"),
    SERVICE_ERROR_C0402("C0402", "第三方功能降级", "be_ErrorCode_C0402"),
    SERVICE_ERROR_C0500("C0500", "通知服务出错", "be_ErrorCode_C0500"),
    SERVICE_ERROR_C0501("C0501", "短信提醒服务失败", "be_ErrorCode_C0501"),
    SERVICE_ERROR_C0502("C0502", "语音提醒服务失败", "be_ErrorCode_C0502"),
    SERVICE_ERROR_C0503("C0503", "邮件提醒服务失败", "be_ErrorCode_C0503"),
    SERVICE_BUSINESS_VERIFY_ERROR_C0504("C0504", "服务端业务校验失败", "be_ErrorCode_C0504"),
    SERVICE_BUSINESS_ERROR_C0505("C0505", "服务端业务运行异常", "be_ErrorCode_C0505"),
    /**
     * 对象存储（OSS）未开启或客户端未装配.
     */
    SERVICE_ERROR_C0506("C0506", "对象存储服务不可用或未开启", "be_ErrorCode_C0506");
    
    /**
     * code.
     */
    private final String code;
    
    /**
     * description.
     */
    private final String description;
    
    /**
     * defaultKey.
     */
    private final String defaultKey;
    
    /**
     * ErrorCodeEnum.
     *
     * @param code        code
     * @param description description
     * @param defaultKey  defaultKey
     */
    ErrorCodeEnum(String code, String description, String defaultKey) {
        this.code = code;
        this.description = description;
        this.defaultKey = defaultKey;
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
        return defaultKey;
    }
    
}
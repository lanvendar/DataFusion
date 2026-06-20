package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.common.exception.CommonException;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.master.param.builtin.BuiltinParamResolver;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 流程初始化消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowInitMsgHandler extends AbstractFlowMsgHandler {

    /**
     * 内置参数解析器.
     */
    private final BuiltinParamResolver builtinParamResolver = new BuiltinParamResolver();

    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowInitMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.INIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作只能从 INITIALIZING 状态开始
        return EnumSet.of(StatusEnum.INITIALIZING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从 INIT_FAILURE 重新初始化
        return EnumSet.of(StatusEnum.INIT_FAILURE);
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        FlowInstance flowIns = super.getInstanceById(msg.getFlowInstanceId());
        //检查流程实例是否已经存在,保证flowId+"_"+scheduleTime+"_"+version的UUID唯一
        if (null != flowIns) {
            //清理流程flow实例
            super.removeInstance(flowIns.getInstanceId());
            //清理任务task实例:参见 TaskAction 和 TaskInitMsgHandler
        }
        //初始化流程实例
        FlowInfo flowInfo = super.getFlowInfo(msg.getFlowId());
        if (flowInfo == null) {
            throw new CommonException("流程定义不存在, flowId=" + msg.getFlowId());
        }
        FlowInstance newFlowIns = createFlowInstance(msg, flowInfo);
        //流程更新为初始化中
        super.saveFlowInstance(newFlowIns);
        //创建任务实例:参见 TaskAction 和 TaskInitMsgHandler
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        // 手工初始化与自动初始化逻辑相同
        handleAction(msg, context);
    }

    /**
     * 组装流程实例.
     *
     * @param msg      初始化流程消息
     * @param flowInfo 流程信息
     * @return 流程实例
     */
    private FlowInstance createFlowInstance(FlowMsg msg, FlowInfo flowInfo) {
        FlowInstance flowIns = new FlowInstance();
        // 设置消息中的流程信息字段
        setField(flowIns::setInstanceId, msg::getFlowInstanceId, "flowInsId不能为空");
        setField(flowIns::setScheduleTime, msg::getScheduleTime, "scheduleTime调度时间不能为空", 0L);
        setField(flowIns::setFlowId, msg::getFlowId, "flowId不能为空");
        setField(flowIns::setVersion, msg::getVersion, "flowVersion不能为空");

        //此处运行时间须在真正开始运行实例时刷新
        //flowInstance.setStartTime(System.currentTimeMillis());
        //flowInstance.setEndTime(System.currentTimeMillis());
        flowIns.setState(StatusEnum.INITIALIZING);
        //设置流程属性到实例
        setField(flowIns::setFlowId, flowInfo::getFlowId, "flowId不能为空");
        setField(flowIns::setFlowName, flowInfo::getFlowName, "flowName不能为空");
        setField(flowIns::setFlowType, flowInfo::getFlowType, "flowType不能为空");
        //设置流程图
        //flowIns.setFlowDag(info.getFlowDag());
        ParamData flowParamData = copyParamData(flowInfo.getFlowParam());
        resolveBuiltinVars(flowParamData, flowIns.getScheduleTime());
        flowIns.setFlowParam(flowParamData);
        // 设置依赖事件ID和事件ID
        setField(flowIns::setDepEventIds, flowInfo::getDepEventIds, "depEventIds不能为空");
        setField(flowIns::setEventId, flowInfo::getEventId, "eventId不能为空");
        // 设置插件数据
        setField(flowIns::setPluginData, flowInfo::getPluginData, "pluginData不能为空");
        return flowIns;
    }

    /**
     * 解析内置时间变量.
     *
     * @param paramData    参数对象
     * @param scheduleTime 调度时间
     */
    private void resolveBuiltinVars(ParamData paramData, long scheduleTime) {
        if (paramData.getVars() == null) {
            paramData.setVars(new HashMap<>());
        }
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(scheduleTime)
                .variables(paramData.getVars())
                .build();
        builtinParamResolver.resolveBuiltinParams(context);
    }

    /**
     * 拷贝参数对象.
     *
     * @param source 源参数
     * @return 新参数
     */
    private ParamData copyParamData(ParamData source) {
        ParamData target = new ParamData();
        Map<String, Variable> copiedVars = new HashMap<>();
        if (source != null && source.getVars() != null) {
            source.getVars().forEach((key, value) -> copiedVars.put(key, copyVariable(value)));
        }
        target.setVars(copiedVars);
        return target;
    }

    /**
     * 拷贝变量对象.
     *
     * @param source 原变量
     * @return 新变量
     */
    private Variable copyVariable(Variable source) {
        if (source == null) {
            return null;
        }
        Variable target = new Variable();
        target.setName(source.getName());
        target.setType(source.getType());
        target.setValue(source.getValue());
        return target;
    }

    /**
     * 设置字段，如果为空则打印警告日志.
     *
     * @param setter         设置字段的函数式接口
     * @param getter         获取字段的函数式接口
     * @param warningMessage 警告信息
     */
    private <T> void setField(Consumer<T> setter, Supplier<T> getter, String warningMessage) {
        setField(setter, getter, warningMessage, null);
    }

    /**
     * 设置字段，如果为空则打印警告日志.
     *
     * @param setter         设置字段的函数式接口
     * @param getter         获取字段的函数式接口
     * @param warningMessage 警告信息
     * @param defaultValue   默认值
     */
    private <T> void setField(Consumer<T> setter, Supplier<T> getter, String warningMessage, T defaultValue) {
        T value = getter.get();
        if (value == null || value.equals(defaultValue)) {
            log.warn(warningMessage);
        } else {
            setter.accept(value);
        }
    }
}

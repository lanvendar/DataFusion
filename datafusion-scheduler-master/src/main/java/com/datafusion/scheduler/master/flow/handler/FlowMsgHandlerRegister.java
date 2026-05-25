package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.scheduler.enums.ActionType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程消息处理器上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowMsgHandlerRegister {

    /**
     * 处理器映射.
     */
    private final Map<ActionType, FlowMsgHandler> handlers = new HashMap<>();

    /**
     * 注册处理器.
     *
     * @param handler 处理器
     */
    public void registerHandler(FlowMsgHandler handler) {
        if (handler != null) {
            handlers.put(handler.getActionType(), handler);
            log.debug("Registered FlowMsgHandler: {}", handler.getActionType());
        }
    }

    /**
     * 获取处理器.
     *
     * @param actionType 动作类型
     * @return 处理器
     */
    public FlowMsgHandler getHandler(ActionType actionType) {
        return handlers.get(actionType);
    }
}

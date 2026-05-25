package com.datafusion.manager.asset.dto.skywalking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

/**
 * CallEdge(表示一条调用链路，即图中的边).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CallEdge {

    /**
     * 调用方服务名称.
     */
    private String callerService;

    /**
     * 调用方接口/端点 (Exit Span).
     * 格式：  服务名:方法:路径
     * 示例：  secp-run-sentinel:POST:/api/run-sentinel/alarm/alarmObject/{thing_id}/trend
     */
    private String callerEndpoint;

    /**
     * 被调用方服务名称.
     */
    private String calleeService;

    /**
     * 被调用方接口/端点 (Entry Span) / Peer 地址.
     */
    private String calleeEndpoint;

    /**
     * 调用类型 (HTTP, RPC, Database, Cache).
     */
    private String callType;

    /**
     * 本次调用的持续时间.
     */
    private long duration;

    /**
     * 测点集合.
     */
    private Set<MetricsTagDto> tagSet;

    /**
     * 用于去重和构建图的唯一标识.
     * @return 唯一标识
     */
    public String getUniqueId() {
        return callerService + "::" + callerEndpoint + "->" + calleeService + "::" + calleeEndpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CallEdge callEdge = (CallEdge) o;
        return Objects.equals(getUniqueId(), callEdge.getUniqueId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUniqueId());
    }

    /**
     * 重新实现的 toString 方法，打印所有字段.
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "CallEdge{"
                + "callerService='" + callerService + '\''
                + ", callerEndpoint='" + callerEndpoint + '\''
                + ", calleeService='" + calleeService + '\''
                + ", calleeEndpoint='" + calleeEndpoint + '\''
                + ", callType='" + callType + '\''
                + ", duration=" + duration
                + ", uniqueId='" + getUniqueId() + '\'' // 可选：打印计算属性
                + '}';
    }
}

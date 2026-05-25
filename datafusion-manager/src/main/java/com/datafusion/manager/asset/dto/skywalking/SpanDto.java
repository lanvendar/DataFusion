package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * SpanDto(单个链路追踪 Span 的详细信息).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class SpanDto {
    
    /**
     * traceId.
     */
    private String traceId;
    
    /**
     * segmentId.
     */
    private String segmentId;
    
    /**
     * spanId.
     */
    private int spanId;
    
    /**
     * parentSpanId.
     */
    private int parentSpanId;

    /**
     * refs.
     */
    private List<SpanRefDto> refs;
    
    /**
     * serviceCode.
     */
    private String serviceCode;
    
    /**
     * endpointName.
     */
    private String endpointName;
    
    /**
     * startTime.
     */
    private Long startTime;
    
    /**
     * endTime.
     */
    private Long endTime;
    
    /**
     * component.
     */
    private String component;
    
    /**
     * type.
     */
    private String type;
    
    /**
     * peer.
     */
    private String peer;
    
    /**
     * isError.
     */
    private Boolean isError;
    
    /**
     * layer.
     */
    private String layer;
    
    /**
     * tags.
     */
    private List<TagDto> tags;
    
    /**
     * headers.
     */
    @JsonProperty("http.headers")
    private String headers;
    
    /**
     * 辅助方法：获取 URL Tag 的值.
     * @return String
     */
    public String getUrlTagValue() {
        if (tags == null) {
            return null;
        }
        return tags.stream()
                .filter(t -> "url".equalsIgnoreCase(t.getKey()))
                .map(TagDto::getValue)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 辅助方法：获取 DB Type.
     * @return String
     */
    public String getDbType() {
        if (tags == null) {
            return null;
        }
        return tags.stream()
                .filter(t -> "db.type".equalsIgnoreCase(t.getKey()))
                .map(TagDto::getValue)
                .findFirst()
                .orElse(component);
    }
}

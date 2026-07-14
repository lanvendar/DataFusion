package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * tsdbreader builder.
 *
 * <p>DataX doc: https://github.com/alibaba/DataX/blob/master/tsdbreader/doc/tsdbreader.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class TsdbReaderBuilder implements DataxReaderBuilder {

    @Override
    public String supports() {
        return "tsdb";
    }

    @Override
    public ObjectNode buildReader(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        JsonNode config = ctx.getSourceConfig();
        DataSourceInfoEntity dsEntity = ctx.getSourceDsEntity();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("endpoint", resolveEndpoint(dsEntity));
        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getSourceFields(), config, false, om, "source column 未配置"));
        parameter.set("metric", requireTextArray(config, "metric", om, "source metric未配置"));
        parameter.put("beginDateTime", requireText(config, "beginDateTime", "source beginDateTime未配置"));
        parameter.put("endDateTime", requireText(config, "endDateTime", "source endDateTime未配置"));

        String splitIntervalMs = DataxBuilderUtils.getText(config, "splitIntervalMs");
        if (splitIntervalMs != null) {
            parameter.put("splitIntervalMs", splitIntervalMs);
        }

        return DataxBuilderUtils.wrapNameAndParameter("tsdbreader", parameter, om);
    }

    private String resolveEndpoint(DataSourceInfoEntity dsEntity) {
        if (dsEntity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "tsdb数据源不存在");
        }
        if (dsEntity.getJdbcUrl() != null) {
            return dsEntity.getJdbcUrl();
        }
        if (dsEntity.getHost() == null || dsEntity.getPort() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "tsdb endpoint未配置");
        }
        return "http://" + dsEntity.getHost() + ":" + dsEntity.getPort();
    }

    private ArrayNode requireTextArray(JsonNode config, String field, ObjectMapper om, String msg) {
        ArrayNode arr = DataxBuilderUtils.getTextArray(config, field, om);
        if (arr.isEmpty()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, msg);
        }
        return arr;
    }

    private String requireText(JsonNode config, String field, String msg) {
        String v = DataxBuilderUtils.getText(config, field);
        if (v == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, msg);
        }
        return v;
    }
}


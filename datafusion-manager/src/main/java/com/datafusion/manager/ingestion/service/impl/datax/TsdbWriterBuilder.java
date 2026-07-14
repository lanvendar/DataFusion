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
 * tsdbwriter builder.
 *
 * <p>DataX doc: https://github.com/alibaba/DataX/blob/master/tsdbwriter/doc/tsdbwriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class TsdbWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "tsdb";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        JsonNode config = ctx.getTargetConfig();
        DataSourceInfoEntity dsEntity = ctx.getTargetDsEntity();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("endpoint", resolveEndpoint(dsEntity));
        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));
        parameter.set("metric", requireTextArray(config, "metric", om, "target metric未配置"));

        String batchSize = DataxBuilderUtils.getText(config, "batchSize");
        if (batchSize != null) {
            parameter.put("batchSize", batchSize);
        }

        return DataxBuilderUtils.wrapNameAndParameter("tsdbwriter", parameter, om);
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
}


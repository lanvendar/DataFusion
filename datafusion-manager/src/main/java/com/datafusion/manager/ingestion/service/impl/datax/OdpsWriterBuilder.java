package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * odpswriter builder.
 *
 * DataX doc: https://github.com/alibaba/DataX/blob/master/odpswriter/doc/odpswriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class OdpsWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "maxcompute";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        DataSourceInfo ds = ctx.getTargetDsInfo();
        JsonNode config = ctx.getTargetConfig();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("accessId", ds.getUsername());
        parameter.put("accessKey", ds.getPassword());
        parameter.put("project", ds.getDatabaseName());
        parameter.put("odpsServer", getEndpoint(ds));
        parameter.put("table", getFirstTable(config));

        String partition = DataxBuilderUtils.getText(config, "partition");
        if (partition != null) {
            parameter.put("partition", partition);
        }

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));

        JsonNode truncate = config == null ? null : config.get("truncate");
        if (truncate != null && truncate.isBoolean()) {
            parameter.put("truncate", truncate.asBoolean());
        }

        return DataxBuilderUtils.wrapNameAndParameter("odpswriter", parameter, om);
    }

    private String getFirstTable(JsonNode config) {
        if (config == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "target table未配置");
        }
        JsonNode t = config.get("table");
        if (t == null || t.isNull()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "target table未配置");
        }
        if (t.isArray() && t.size() > 0) {
            return t.get(0).asText();
        }
        return t.asText();
    }

    private String getEndpoint(DataSourceInfo ds) {
        Properties p = ds.getExtendParam();
        if (p == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "MaxCompute endpoint未配置");
        }
        String endpoint = p.getProperty("endpoint");
        if (endpoint == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "MaxCompute endpoint未配置");
        }
        return endpoint;
    }
}


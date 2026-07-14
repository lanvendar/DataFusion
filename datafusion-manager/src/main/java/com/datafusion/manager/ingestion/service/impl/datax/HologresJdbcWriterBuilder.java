package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.datasource.model.DataSourceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * hologresjdbcwriter builder.
 *
 * <p>DataX doc: https://github.com/alibaba/DataX/blob/master/hologresjdbcwriter/doc/hologresjdbcwriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class HologresJdbcWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "hologres";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        DataSourceInfo ds = ctx.getTargetDsInfo();
        JsonNode config = ctx.getTargetConfig();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("username", ds.getUsername());
        parameter.put("password", ds.getPassword());
        parameter.put("endpoint", ds.getHost() + ":" + ds.getPort());
        parameter.put("database", ds.getDatabaseName());
        parameter.put("schema", ds.getSchemaName());
        parameter.put("table", DataxBuilderUtils.getFirstText(config, "table"));
        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));

        String writeMode = DataxBuilderUtils.getText(config, "writeMode");
        if (writeMode != null) {
            parameter.put("writeMode", writeMode);
        }

        return DataxBuilderUtils.wrapNameAndParameter("hologresjdbcwriter", parameter, om);
    }
}


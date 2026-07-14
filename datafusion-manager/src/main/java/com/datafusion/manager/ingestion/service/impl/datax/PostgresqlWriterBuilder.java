package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.datasource.model.DataSourceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * postgresqlwriter builder.
 *
 * <p>DataX doc: https://github.com/alibaba/DataX/blob/master/postgresqlwriter/doc/postgresqlwriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class PostgresqlWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "postgres";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        DataSourceInfo ds = ctx.getTargetDsInfo();
        ObjectNode parameter = om.createObjectNode();
        parameter.put("username", ds.getUsername());
        parameter.put("password", ds.getPassword());

        ObjectNode connection0 = om.createObjectNode();
        connection0.put("jdbcUrl", ds.getJdbcUrl());
        JsonNode config = ctx.getTargetConfig();
        connection0.set("table", DataxBuilderUtils.getTextArray(config, "table", om));

        ArrayNode connections = om.createArrayNode();
        connections.add(connection0);
        parameter.set("connection", connections);

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));

        String writeMode = DataxBuilderUtils.getText(config, "writeMode");
        if (writeMode != null) {
            parameter.put("writeMode", writeMode);
        }

        ArrayNode preSql = DataxBuilderUtils.getSqlArray(config, "preSql", om);
        if (!preSql.isEmpty()) {
            parameter.set("preSql", preSql);
        }
        ArrayNode postSql = DataxBuilderUtils.getSqlArray(config, "postSql", om);
        if (!postSql.isEmpty()) {
            parameter.set("postSql", postSql);
        }

        return DataxBuilderUtils.wrapNameAndParameter("postgresqlwriter", parameter, om);
    }
}

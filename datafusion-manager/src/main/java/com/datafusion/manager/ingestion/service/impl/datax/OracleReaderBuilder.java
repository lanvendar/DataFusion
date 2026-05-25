package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.datasource.model.DataSourceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * oraclereader builder.
 *
 * DataX doc: https://github.com/alibaba/DataX/blob/master/oraclereader/doc/oraclereader.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class OracleReaderBuilder implements DataxReaderBuilder {

    @Override
    public String supports() {
        return "oracle";
    }

    @Override
    public ObjectNode buildReader(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        DataSourceInfo ds = ctx.getSourceDsInfo();
        JsonNode config = ctx.getSourceConfig();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("username", ds.getUsername());
        parameter.put("password", ds.getPassword());

        ArrayNode jdbcUrls = om.createArrayNode();
        jdbcUrls.add(ds.getJdbcUrl());

        ObjectNode connection0 = om.createObjectNode();
        connection0.set("jdbcUrl", jdbcUrls);

        String querySql = DataxBuilderUtils.getText(config, "querySql");
        if (querySql != null) {
            ArrayNode querySqlArr = om.createArrayNode();
            querySqlArr.add(querySql);
            connection0.set("querySql", querySqlArr);
        } else {
            ArrayNode tables = DataxBuilderUtils.getTextArray(config, "table", om);
            connection0.set("table", tables);
        }

        ArrayNode connections = om.createArrayNode();
        connections.add(connection0);
        parameter.set("connection", connections);

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getSourceFields(), config, false, om, "source column 未配置"));

        String where = DataxBuilderUtils.getText(config, "where");
        if (where != null) {
            parameter.put("where", where);
        }
        String splitPk = DataxBuilderUtils.getText(config, "splitPk");
        if (splitPk != null) {
            parameter.put("splitPk", splitPk);
        }

        return DataxBuilderUtils.wrapNameAndParameter("oraclereader", parameter, om);
    }
}


package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.datasource.model.DataSourceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * starrockswriter builder.
 *
 * DataX doc: https://github.com/alibaba/DataX/blob/master/starrockswriter/doc/starrockswriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class StarrocksWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "starrocks";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        DataSourceInfo ds = ctx.getTargetDsInfo();
        JsonNode config = ctx.getTargetConfig();

        ObjectNode parameter = om.createObjectNode();
        parameter.put("username", ds.getUsername());
        parameter.put("password", ds.getPassword());

        String database = DataxBuilderUtils.getText(config, "selectedDatabase");
        if (database == null) {
            database = ds.getDatabaseName();
        }
        parameter.put("database", database);
        parameter.put("table", DataxBuilderUtils.getFirstText(config, "table"));

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));

        JsonNode loadUrlNode = config == null ? null : config.get("loadUrl");
        if (loadUrlNode != null) {
            if (loadUrlNode.isArray()) {
                ArrayNode loadUrl = om.createArrayNode();
                for (JsonNode e : loadUrlNode) {
                    loadUrl.add(e.asText());
                }
                parameter.set("loadUrl", loadUrl);
            } else {
                ArrayNode loadUrl = om.createArrayNode();
                loadUrl.add(loadUrlNode.asText());
                parameter.set("loadUrl", loadUrl);
            }
        }

        ObjectNode loadProps = om.createObjectNode();
        loadProps.put("format", "json");
        loadProps.put("strip_outer_array", true);
        parameter.set("loadProps", loadProps);

        return DataxBuilderUtils.wrapNameAndParameter("starrockswriter", parameter, om);
    }
}


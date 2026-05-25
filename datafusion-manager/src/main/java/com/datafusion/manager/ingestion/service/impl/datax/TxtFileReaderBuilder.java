package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * txtfilereader builder.
 *
 * DataX doc: https://github.com/alibaba/DataX/blob/master/txtfilereader/doc/txtfilereader.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class TxtFileReaderBuilder implements DataxReaderBuilder {

    @Override
    public String supports() {
        return "txtfile";
    }

    @Override
    public ObjectNode buildReader(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        JsonNode config = ctx.getSourceConfig();

        ObjectNode parameter = om.createObjectNode();
        ArrayNode path = DataxBuilderUtils.getTextArray(config, "path", om);
        if (path.isEmpty()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "source path未配置");
        }
        parameter.set("path", path);

        String encoding = DataxBuilderUtils.getText(config, "encoding");
        if (encoding != null) {
            parameter.put("encoding", encoding);
        }
        String fieldDelimiter = DataxBuilderUtils.getText(config, "fieldDelimiter");
        if (fieldDelimiter != null) {
            parameter.put("fieldDelimiter", fieldDelimiter);
        }

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getSourceFields(), config, false, om, "source column 未配置"));

        JsonNode skipHeader = config == null ? null : config.get("skipHeader");
        if (skipHeader != null && skipHeader.isBoolean()) {
            parameter.put("skipHeader", skipHeader.asBoolean());
        }
        String nullFormat = DataxBuilderUtils.getText(config, "nullFormat");
        if (nullFormat != null) {
            parameter.put("nullFormat", nullFormat);
        }
        String compress = DataxBuilderUtils.getText(config, "compress");
        if (compress != null) {
            parameter.put("compress", compress);
        }

        return DataxBuilderUtils.wrapNameAndParameter("txtfilereader", parameter, om);
    }
}


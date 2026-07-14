package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * txtfilewriter builder.
 *
 * <p>DataX doc: https://github.com/alibaba/DataX/blob/master/txtfilewriter/doc/txtfilewriter.md
 * Verified-At: 2026-05-09
 * Verified-Commit: 60ea07b
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Component
public class TxtFileWriterBuilder implements DataxWriterBuilder {

    @Override
    public String supports() {
        return "txtfile";
    }

    @Override
    public ObjectNode buildWriter(DataxJobContext ctx) {
        ObjectMapper om = ctx.getObjectMapper();
        JsonNode config = ctx.getTargetConfig();

        ObjectNode parameter = om.createObjectNode();
        String path = DataxBuilderUtils.getText(config, "path");
        if (path == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "target path未配置");
        }
        parameter.put("path", path);

        String fileName = DataxBuilderUtils.getText(config, "fileName");
        if (fileName == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "target fileName未配置");
        }
        parameter.put("fileName", fileName);

        String encoding = DataxBuilderUtils.getText(config, "encoding");
        if (encoding != null) {
            parameter.put("encoding", encoding);
        }
        String fieldDelimiter = DataxBuilderUtils.getText(config, "fieldDelimiter");
        if (fieldDelimiter != null) {
            parameter.put("fieldDelimiter", fieldDelimiter);
        }

        parameter.set("column",
                DataxBuilderUtils.resolveColumns(ctx.getTargetFields(), config, true, om, "target column 未配置"));

        String writeMode = DataxBuilderUtils.getText(config, "writeMode");
        if (writeMode != null) {
            parameter.put("writeMode", writeMode);
        }
        String nullFormat = DataxBuilderUtils.getText(config, "nullFormat");
        if (nullFormat != null) {
            parameter.put("nullFormat", nullFormat);
        }
        String dateFormat = DataxBuilderUtils.getText(config, "dateFormat");
        if (dateFormat != null) {
            parameter.put("dateFormat", dateFormat);
        }
        String compress = DataxBuilderUtils.getText(config, "compress");
        if (compress != null) {
            parameter.put("compress", compress);
        }
        String header = DataxBuilderUtils.getText(config, "header");
        if (header != null) {
            parameter.put("header", header);
        }

        return DataxBuilderUtils.wrapNameAndParameter("txtfilewriter", parameter, om);
    }
}


package com.datafusion.common.spring.typehandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * json对象创建接口.
 *
 * @author lanvendar
 * @version 3.0.0, 2024/3/13
 * @since 2024/3/13
 */
public interface JsonObjectHandler {
    
    /**
     * json对象创建.
     *
     * @param connection 连接信息
     * @param parameter  参数
     * @param mapper     jacksonMapper对象
     * @return Object
     * @throws SQLException 数据库异常
     */
    Object createJsonObject(Connection connection, JsonNode parameter, ObjectMapper mapper) throws SQLException;
}

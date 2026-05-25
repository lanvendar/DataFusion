package com.datafusion.common.spring.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * pg的创建对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2024/3/13
 * @since 2024/3/13
 */
public class PostgresJsonObjectHandler implements JsonObjectHandler {
    
    @Override
    public Object createJsonObject(Connection connection, JsonNode parameter, ObjectMapper mapper) throws SQLException {
        PGobject pGobject = new PGobject();
        pGobject.setType("json");
        if (Objects.isNull(parameter)) {
            return null;
        }
        try {
            pGobject.setValue(mapper.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Object to json string failed!" + parameter, e);
        }
        return pGobject;
    }
}

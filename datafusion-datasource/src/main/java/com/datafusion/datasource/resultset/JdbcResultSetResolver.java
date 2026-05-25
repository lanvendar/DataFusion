// 如果文件已存在，则替换其内容

package com.datafusion.datasource.resultset;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;
import com.datafusion.datasource.resultset.handler.jdbc.JdbcTypeHandlerFactory;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 针对 java.sql.ResultSet 的结果集解析器实现.
 * 该类继承自 AbstractResultSetResolver，并实现了与 JDBC ResultSet 交互的具体细节.
 * 它负责从 ResultSet 中提取元数据和行数据，而将对象的组装逻辑完全委托给父类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/10
 * @since 2025/7/9
 */
@Slf4j
public class JdbcResultSetResolver extends AbstractResultSetResolver<ResultSet> {
    
    @Override
    protected TypeHandlerFactory<? extends TypeHandler<ResultSet, ?>> createTypeHandlerFactory() {
        return new JdbcTypeHandlerFactory();
    }
    
    @Override
    protected List<String> getColumnLabels(ResultSet rs) throws SQLException {
        List<String> labels = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            labels.add(metaData.getColumnLabel(i));
        }
        return labels;
    }
    
    @Override
    protected boolean hasNext(ResultSet rs) throws Exception {
        return rs.next();
    }
}
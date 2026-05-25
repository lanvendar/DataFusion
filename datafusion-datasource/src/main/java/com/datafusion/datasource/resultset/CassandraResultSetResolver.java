package com.datafusion.datasource.resultset;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;
import com.datafusion.datasource.resultset.handler.cassandra.CassandraHandlerAdapterFactory;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cassandra 结果集解析器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
@Slf4j
public class CassandraResultSetResolver extends AbstractResultSetResolver<ResultSet> {
    /**
     * 迭代器.
     */
    private Iterator<Row> rowIterator;
    
    /**
     * 当前行.
     */
    private Row currentRow;
    
    @Override
    protected TypeHandlerFactory<? extends TypeHandler<ResultSet, ?>> createTypeHandlerFactory() {
        return new CassandraHandlerAdapterFactory(() -> this.currentRow);
    }
    
    // Override getResultSet to initialize the iterator state
    @Override
    public Object getResultSet(ResultSet rs, Type type) {
        if (rs == null) {
            return null;
        }
        // Initialize the state for this execution
        this.rowIterator = rs.iterator();
        this.currentRow = null;
        return super.getResultSet(rs, type);
    }
    
    @Override
    protected List<String> getColumnLabels(ResultSet rs) {
        List<String> labels = new ArrayList<>();
        // Get column definitions from the result set itself
        for (ColumnDefinitions.Definition definition : rs.getColumnDefinitions()) {
            labels.add(definition.getName());
        }
        return labels;
    }
    
    @Override
    protected boolean hasNext(ResultSet rs) {
        // Use the internal iterator
        if (rowIterator.hasNext()) {
            // Advance the iterator and store the current row
            this.currentRow = rowIterator.next();
            return true;
        }
        return false;
    }
}

package com.datafusion.manager.asset.handler;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlCreate;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlShuttle;

public class CreateTableAsToInsertConverter extends SqlShuttle {
    
    /**
     * 对 SQL 节点进行访问和转换的核心方法.
     * 当遇到 CREATE TABLE AS SELECT 结构时，会将其转换为 INSERT INTO SELECT。
     *
     * @param call 要访问的 SqlCall 节点。
     * @return 转换后的 SqlNode，如果不是目标结构则返回原始节点。
     */
    @Override
    public SqlNode visit(SqlCall call) {
        
        if (call.getKind() == SqlKind.CREATE_TABLE) {
            SqlCreate create = (SqlCreate) call;
            
            // 检查是否是 CREATE TABLE tableName AS SELECT ... 的结构
            // create.getOperandList() 结构通常是：[目标表名, (可选的列定义), (可选的 AS SELECT 语句)]
            // 对于 CREATE TABLE t AS SELECT ...，通常是 [SqlIdentifier(t), SqlSelect(...)]
            // Calcite 内部可能将 AS SELECT 视为 CREATE 的一个操作数，取决于具体解析器配置。
            // 假设我们这里的 CREATE TABLE AS SELECT 对应 Calcite SqlCreate 的两个主要操作数:
            // 0: targetTable (SqlIdentifier)
            // 2: sourceSelect (SqlSelect)
            // 1: 可能是空的 SqlNodeList 或者其他选项
            
            // 确保有足够的操作数且第三个操作数是 SELECT
            if (create.getOperandList().size() >= 3 && create.getOperandList().get(2) instanceof SqlSelect) {
                SqlNode targetTableNode = create.getOperandList().get(0);
                SqlSelect sourceSelect = (SqlSelect) create.getOperandList().get(2);
                // 检查目标表名是否是 SqlIdentifier (通常是这种情况)
                if (targetTableNode.getKind() == SqlKind.IDENTIFIER) {
                    SqlIdentifier targetTableIdentifier = (SqlIdentifier) targetTableNode;
                    
                    // 构建 INSERT INTO 语句
                    // INSERT INTO targetTable SELECT ...
                    // SqlInsert 的构造函数通常是:
                    // SqlInsert(SqlParserPos pos, SqlNode targetTable, SqlNodeList targetColumnList, SqlNode source, SqlNodeList extendedOperandList)
                    // 这里我们没有 targetColumnList (因为它对应 SELECT * 或隐式列)，也没有 extendedOperandList
                    
                    // 获取 SqlNode 的位置信息，用于构建新的 SqlNode
                    SqlParserPos pos = create.getParserPosition();
                    SqlNodeList keywords = new SqlNodeList(pos);
                    
                    // 创建 INSERT 语句
                    // targetTable: targetTableIdentifier
                    // targetColumnList: null (因为是 INSERT INTO ... SELECT ...)
                    // source: sourceSelect
                    // extendedOperandList: null
                    return new SqlInsert(
                            pos,
                            keywords,
                            targetTableIdentifier,
                            sourceSelect,
                            null
                    );
                }
            }
        }
        return call; // 如果不是 CREATE TABLE AS SELECT，则返回原始或已处理的子节点
    }
}
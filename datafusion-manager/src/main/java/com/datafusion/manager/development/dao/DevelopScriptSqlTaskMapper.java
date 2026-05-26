package com.datafusion.manager.development.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskQueryDto;
import com.datafusion.manager.development.po.DevelopScriptSqlTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据开发-SQL脚本任务Mapper.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Mapper
public interface DevelopScriptSqlTaskMapper extends BaseMapper<DevelopScriptSqlTaskEntity> {

    /**
     * 分页查询SQL脚本任务（仅未软删）.
     *
     * @param query 分页及条件
     * @return 列表
     */
    List<DevelopScriptSqlTaskDto> pageTaskList(@Param("query") PageQuery<DevelopScriptSqlTaskQueryDto> query);

    /**
     * 分页总数.
     *
     * @param query 分页及条件
     * @return 总数
     */
    Integer pageTaskCount(@Param("query") PageQuery<DevelopScriptSqlTaskQueryDto> query);

    /**
     * 列表查询（仅未软删，不分页）.
     *
     * @param query 条件
     * @return 列表
     */
    List<DevelopScriptSqlTaskDto> listTask(@Param("query") DevelopScriptSqlTaskQueryDto query);

    /**
     * 查询指定日期前缀下最大任务编码（用于生成序号）.
     *
     * @param prefix 含日期的前缀，如 KF250513
     * @return 最大完整 code，可能为 null
     */
    String selectMaxTaskCodeSeq(@Param("prefix") String prefix);
}

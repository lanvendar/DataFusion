package com.datafusion.manager.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dto.VariableInfoDto;
import com.datafusion.manager.system.dto.VariableInfoQueryDto;
import com.datafusion.manager.system.dto.VariableInfoSaveDto;
import com.datafusion.manager.system.dto.VariableInfoUpdateDto;
import com.datafusion.manager.system.po.VariableInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 系统-变量信息Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface VariableInfoService extends IService<VariableInfoEntity> {

    /**
     * 分页查询变量.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<VariableInfoDto> pageVariableInfo(PageQuery<VariableInfoQueryDto> query);

    /**
     * 查询变量列表.
     *
     * @param query 查询条件
     * @return 变量列表
     */
    List<VariableInfoDto> listVariableInfo(VariableInfoQueryDto query);

    /**
     * 根据ID查询变量.
     *
     * @param id 变量ID
     * @return 变量详情
     */
    VariableInfoDto getVariableInfoById(UUID id);

    /**
     * 新增变量(仅CUSTOM类型).
     *
     * @param dto 新增参数
     * @return 变量ID
     */
    UUID addVariableInfo(VariableInfoSaveDto dto);

    /**
     * 修改变量(SYSTEM类型仅可改value).
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateVariableInfo(VariableInfoUpdateDto dto);

    /**
     * 删除变量(仅CUSTOM类型).
     *
     * @param id 变量ID
     * @return 是否成功
     */
    boolean deleteVariableInfo(UUID id);
}

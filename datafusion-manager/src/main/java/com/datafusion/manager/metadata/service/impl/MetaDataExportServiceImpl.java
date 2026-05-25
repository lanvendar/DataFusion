package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.AssertUtils;
import com.datafusion.manager.metadata.dto.TableColumnExcelSheet;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.service.ColumnInfoService;
import com.datafusion.manager.metadata.service.MetaDataExportService;
import com.datafusion.manager.metadata.service.TableInfoService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MetaDataExportServiceImpl.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/6
 * @since 3.7.2, 2024/11/6
 */
@Service
@RequiredArgsConstructor
public class MetaDataExportServiceImpl implements MetaDataExportService {
    /**
     * 元数据-表服务.
     */
    private final TableInfoService tableInfoService;

    /**
     * 元数据-字段信息服务.
     */
    private final ColumnInfoService columnInfoService;

    @Override
    public void exportTableColumn(UUID tableId, HttpServletRequest request, HttpServletResponse response) {
        // 检查参数tableId是否为空
        AssertUtils.notNull(tableId, "参数tableId不能为空");

        // 获取表信息
        TableInfoEntity tableInfoEntity = tableInfoService.getWithCheckNonNull(tableId);

        // 获取列信息
        List<ColumnInfoEntity> columns = columnInfoService.getByTableId(tableId);
        if (CollectionUtils.isEmpty(columns)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, String.format("表【%s】字段不存在", tableInfoEntity.getTableName()));
        }

        // 创建Excel工作簿
        Workbook workbook = createWorkbook();

        // 创建并设置表头样式
        CellStyle headerCellStyle = createHeaderCellStyle(workbook);

        // 创建sheet
        TableColumnExcelSheet columnSheet = createColumnSheet(0, tableInfoEntity, columns);
        Sheet sheet = workbook.createSheet(columnSheet.getSheetName());

        // 写入表头
        writeHeaderRow(sheet, columnSheet.getHeader(), headerCellStyle);

        // 写入数据行
        writeDataRows(sheet, columnSheet.getRows());

        // 设置列宽自适应
        autoSizeColumns(sheet, columnSheet.getHeader().size());

        // 设置响应头
        setResponseHeaders(response, tableInfoEntity.getTableName());

        // 写入输出流
        writeWorkbookToOutputStream(workbook, response);
    }

    /**
     * 创建Excel工作簿.
     *
     * @return 工作簿对象
     */
    private Workbook createWorkbook() {
        return new XSSFWorkbook();
    }

    /**
     * 创建表头样式.
     *
     * @param workbook 工作簿对象
     * @return 表头样式
     */
    private CellStyle createHeaderCellStyle(Workbook workbook) {
        CellStyle headerCellStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        //headerFont.setColor(IndexedColors.BLACK.getIndex()); // 使用兼容的方法
        //headerCellStyle.setFont(headerFont);
        //headerCellStyle.setFillForegroundColor(IndexedColors.BLACK.getIndex());
        //headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return headerCellStyle;
    }

    /**
     * 创建一个sheet.
     *
     * @param sheetIndex sheet 索引号
     * @param table      表信息
     * @param columns    列信息
     * @return 示例sheet
     */
    private TableColumnExcelSheet createColumnSheet(int sheetIndex, TableInfoEntity table, List<ColumnInfoEntity> columns) {
        // 定义下载sheet.
        String sheetName = String.format("%s#%s", table.getTableName(), table.getTableDesc());
        TableColumnExcelSheet sheet = new TableColumnExcelSheet(sheetIndex, sheetName);

        // 设置excel表头
        List<String> header = Arrays.stream(new String[]{"名称", "存储类型", "查询类型", "字段长度", "字段精度", "字段序号", "默认值", "是否为空", "是否主键", "字段注释"})
                .collect(Collectors.toList());
        sheet.setHeader(header);

        sheet.setRows(columns);
        return sheet;
    }

    /**
     * 写入表头行.
     *
     * @param sheet           sheet 对象
     * @param header          表头列表
     * @param headerCellStyle 表头样式
     */
    private void writeHeaderRow(Sheet sheet, List<String> header, CellStyle headerCellStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < header.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(header.get(i));
            cell.setCellStyle(headerCellStyle);
        }
    }

    /**
     * 写入数据行.
     *
     * @param sheet sheet 对象
     * @param rows  数据行列表
     */
    private void writeDataRows(Sheet sheet, List<ColumnInfoEntity> rows) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.createRow(i + 1);
            ColumnInfoEntity column = rows.get(i);
            List<String> values = Arrays.asList(
                    column.getColumnName(), // 名称
                    column.getColumnType(), // 存储类型
                    column.getViewType(), // 查询类型
                    null == column.getColumnLength() ? StrUtil.EMPTY : String.valueOf(column.getColumnLength()), // 字段长度
                    null == column.getColumnPrecision() ? StrUtil.EMPTY : String.valueOf(column.getColumnPrecision()), // 字段精度
                    String.valueOf(column.getColumnSerial()), // 字段序号
                    column.getDefaultValue(), // 默认值
                    String.valueOf(column.getIsNullable()), //? "Y" : "N", 是否非空
                    String.valueOf(column.getIsPrimary()), // ? "Y" : "N", 是否主键
                    column.getColumnDesc() // 字段注释
            );
            for (int j = 0; j < values.size(); j++) {
                row.createCell(j).setCellValue(values.get(j));
            }
        }
    }

    /**
     * 设置列宽自适应.
     *
     * @param sheet   sheet 对象
     * @param columns 列数
     */
    private void autoSizeColumns(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 设置响应头.
     *
     * @param response 响应对象
     * @param fileName 文件名
     */
    private void setResponseHeaders(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");
    }

    /**
     * 写入工作簿到输出流.
     *
     * @param workbook 工作簿对象
     * @param response 响应对象
     */
    private void writeWorkbookToOutputStream(Workbook workbook, HttpServletResponse response) {
        try (OutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("导出Excel失败", e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}



package com.balancloud.common.utils;

import com.balancloud.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 列表导出共用的 XLSX 写入工具：只负责表头、单元格格式与响应输出，
 * 具体列与筛选条件由各业务服务决定。
 */
public final class ExcelExportSupport {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelExportSupport() {
    }

    public static XSSFWorkbook workbook() {
        return new XSSFWorkbook();
    }

    public static Sheet sheet(XSSFWorkbook workbook, String name, String[] headers, int[] widths) {
        Sheet sheet = workbook.createSheet(name);
        CellStyle headerStyle = headerStyle(workbook);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    public static void finish(XSSFWorkbook workbook, Sheet sheet, int rowCount,
                              int columnCount, HttpServletResponse response, String prefix) {
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(rowCount, 1), 0, columnCount - 1));
        String filename = prefix + "_" + LocalDateTime.now().format(FILE_TIME) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + filename + "; filename*=UTF-8''" + filename);
        try (workbook) {
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new ServiceException(500, "导出失败：" + e.getMessage());
        }
    }

    public static CellStyle moneyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
        return style;
    }

    public static void money(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(style);
    }

    public static String text(String value) {
        return value == null ? "" : value;
    }

    public static String time(LocalDateTime value) {
        return value == null ? "" : value.format(CELL_TIME);
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}

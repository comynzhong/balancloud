package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.system.domain.SysLogininfor;
import com.balancloud.system.domain.SysOperLog;
import com.balancloud.system.dto.LogininforQuery;
import com.balancloud.system.dto.OperLogQuery;
import com.balancloud.system.mapper.SysLogininforMapper;
import com.balancloud.system.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OperLogQueryService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXPORT_LIMIT = 10000;
    private static final String[] BUSINESS_TYPES = {"其他", "新增", "修改", "删除", "授权", "导出", "导入", "强制退出", "清空"};

    private final SysOperLogMapper operLogMapper;
    private final SysLogininforMapper logininforMapper;

    public OperLogQueryService(SysOperLogMapper operLogMapper, SysLogininforMapper logininforMapper) {
        this.operLogMapper = operLogMapper;
        this.logininforMapper = logininforMapper;
    }

    public TableDataInfo list(OperLogQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNum, pageSize), buildOperWrapper(query));
        return TableDataInfo.of(page.getTotal(), page.getRecords());
    }

    public SysOperLog detail(Long operId) {
        SysOperLog log = operLogMapper.selectById(operId);
        if (log == null) {
            throw new ServiceException(404, "审计日志不存在");
        }
        return log;
    }

    public List<String> titles() {
        return operLogMapper.selectDistinctTitles();
    }

    public TableDataInfo listLoginLog(LogininforQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<SysLogininfor> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUserName()), SysLogininfor::getUserName,
                query.getUserName() == null ? null : query.getUserName().trim());
        wrapper.eq(StringUtils.hasText(query.getStatus()), SysLogininfor::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysLogininfor::getIpaddr, keyword).or().like(SysLogininfor::getMsg, keyword));
        }
        wrapper.ge(query.getBeginTime() != null, SysLogininfor::getLoginTime, query.getBeginTime());
        wrapper.le(query.getEndTime() != null, SysLogininfor::getLoginTime, query.getEndTime());
        wrapper.orderByDesc(SysLogininfor::getInfoId);
        Page<SysLogininfor> page = logininforMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), page.getRecords());
    }

    public void export(OperLogQuery query, HttpServletResponse response) {
        List<SysOperLog> logs = operLogMapper.selectList(buildOperWrapper(query).last("LIMIT " + EXPORT_LIMIT));
        String filename = "oper_logs_" + LocalDateTime.now().format(FILE_TIME) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + filename + "; filename*=UTF-8''" + filename);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("操作审计日志");
            String[] headers = {"日志号", "模块", "业务类型", "操作人", "请求方式", "接口", "来源 IP",
                    "操作时间", "耗时(ms)", "结果", "错误信息"};
            int[] widths = {10, 16, 12, 14, 10, 40, 16, 20, 12, 10, 40};
            CellStyle headerStyle = headerStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            int rowIndex = 1;
            for (SysOperLog log : logs) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(String.valueOf(log.getOperId()));
                row.createCell(1).setCellValue(nvl(log.getTitle()));
                row.createCell(2).setCellValue(businessTypeLabel(log.getBusinessType()));
                row.createCell(3).setCellValue(nvl(log.getOperName()));
                row.createCell(4).setCellValue(nvl(log.getRequestMethod()));
                row.createCell(5).setCellValue(nvl(log.getOperUrl()));
                row.createCell(6).setCellValue(nvl(log.getOperIp()));
                row.createCell(7).setCellValue(log.getOperTime() == null ? "" : log.getOperTime().format(DATE_TIME));
                row.createCell(8).setCellValue(log.getCostTime() == null ? "" : String.valueOf(log.getCostTime()));
                row.createCell(9).setCellValue(Integer.valueOf(1).equals(log.getStatus()) ? "失败" : "成功");
                row.createCell(10).setCellValue(nvl(log.getErrorMsg()));
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowIndex - 1, 1), 0, headers.length - 1));
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new ServiceException(500, "导出审计日志失败：" + e.getMessage());
        }
    }

    private LambdaQueryWrapper<SysOperLog> buildOperWrapper(OperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getTitle()), SysOperLog::getTitle, query.getTitle());
        wrapper.like(StringUtils.hasText(query.getOperName()), SysOperLog::getOperName,
                query.getOperName() == null ? null : query.getOperName().trim());
        wrapper.eq(query.getBusinessType() != null, SysOperLog::getBusinessType, query.getBusinessType());
        wrapper.eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysOperLog::getOperUrl, keyword)
                    .or().like(SysOperLog::getOperIp, keyword)
                    .or().like(SysOperLog::getMethod, keyword));
        }
        wrapper.ge(query.getBeginTime() != null, SysOperLog::getOperTime, query.getBeginTime());
        wrapper.le(query.getEndTime() != null, SysOperLog::getOperTime, query.getEndTime());
        wrapper.orderByDesc(SysOperLog::getOperId);
        return wrapper;
    }

    private String businessTypeLabel(Integer businessType) {
        if (businessType == null || businessType < 0 || businessType >= BUSINESS_TYPES.length) {
            return BUSINESS_TYPES[0];
        }
        return BUSINESS_TYPES[businessType];
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}

package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.system.dto.DictDataQuery;
import com.balancloud.system.dto.DictDataRequest;
import com.balancloud.system.dto.DictTypeQuery;
import com.balancloud.system.dto.DictTypeRequest;
import com.balancloud.system.service.SysDictService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/dict")
public class SysDictController {

    private final SysDictService dictService;

    public SysDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @GetMapping("/types")
    public TableDataInfo types(DictTypeQuery query) {
        return dictService.listTypes(query);
    }

    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @PostMapping("/types")
    public AjaxResult createType(@RequestBody DictTypeRequest request) {
        return AjaxResult.success(dictService.createType(request));
    }

    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @PutMapping("/types/{id}")
    public AjaxResult updateType(@PathVariable Long id, @RequestBody DictTypeRequest request) {
        dictService.updateType(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/types/{id}")
    public AjaxResult deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @GetMapping("/data")
    public TableDataInfo data(DictDataQuery query) {
        return dictService.listData(query);
    }

    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping("/data")
    public AjaxResult createData(@RequestBody DictDataRequest request) {
        return AjaxResult.success(dictService.createData(request));
    }

    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping("/data/{id}")
    public AjaxResult updateData(@PathVariable Long id, @RequestBody DictDataRequest request) {
        dictService.updateData(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/data/{id}")
    public AjaxResult deleteData(@PathVariable Long id) {
        dictService.deleteData(id);
        return AjaxResult.success();
    }

    @GetMapping("/options/{dictType}")
    public AjaxResult options(@PathVariable String dictType) {
        return AjaxResult.success(dictService.options(dictType));
    }
}

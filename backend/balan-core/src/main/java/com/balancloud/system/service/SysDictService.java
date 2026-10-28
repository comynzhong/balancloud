package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.system.domain.SysDictData;
import com.balancloud.system.domain.SysDictType;
import com.balancloud.system.dto.DictDataQuery;
import com.balancloud.system.dto.DictDataRequest;
import com.balancloud.system.dto.DictTypeQuery;
import com.balancloud.system.dto.DictTypeRequest;
import com.balancloud.system.mapper.SysDictDataMapper;
import com.balancloud.system.mapper.SysDictTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class SysDictService {

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public SysDictService(SysDictTypeMapper typeMapper, SysDictDataMapper dataMapper) {
        this.typeMapper = typeMapper;
        this.dataMapper = dataMapper;
    }

    public TableDataInfo listTypes(DictTypeQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysDictType::getDictName, keyword)
                    .or().like(SysDictType::getDictType, keyword));
        }
        wrapper.eq(StringUtils.hasText(query.getStatus()), SysDictType::getStatus, query.getStatus());
        wrapper.orderByAsc(SysDictType::getDictId);
        Page<SysDictType> page = typeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), page.getRecords());
    }

    @Transactional
    public Long createType(DictTypeRequest request) {
        String dictName = requireText(request.getDictName(), "字典名称不能为空");
        String dictType = requireText(request.getDictType(), "字典类型不能为空");
        if (typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)) > 0) {
            throw new ServiceException("字典类型 " + dictType + " 已存在");
        }
        SysDictType entity = new SysDictType();
        entity.setDictName(dictName);
        entity.setDictType(dictType);
        entity.setStatus(defaultStatus(request.getStatus()));
        entity.setRemark(request.getRemark());
        entity.setCreateBy(SecurityUtils.getUsername());
        typeMapper.insert(entity);
        return entity.getDictId();
    }

    @Transactional
    public void updateType(Long id, DictTypeRequest request) {
        SysDictType existing = requireType(id);
        String dictName = requireText(request.getDictName(), "字典名称不能为空");
        String dictType = requireText(request.getDictType(), "字典类型不能为空");
        if (!dictType.equals(existing.getDictType())
                && typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(SysDictType::getDictId, id)) > 0) {
            throw new ServiceException("字典类型 " + dictType + " 已存在");
        }
        SysDictType update = new SysDictType();
        update.setDictId(id);
        update.setDictName(dictName);
        update.setDictType(dictType);
        update.setStatus(defaultStatus(request.getStatus()));
        update.setRemark(request.getRemark());
        update.setUpdateBy(SecurityUtils.getUsername());
        typeMapper.updateById(update);
        if (!dictType.equals(existing.getDictType())) {
            dataMapper.update(null, new LambdaUpdateWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, existing.getDictType())
                    .set(SysDictData::getDictType, dictType)
                    .set(SysDictData::getUpdateBy, SecurityUtils.getUsername()));
        }
    }

    @Transactional
    public void deleteType(Long id) {
        SysDictType existing = requireType(id);
        if (dataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, existing.getDictType())) > 0) {
            throw new ServiceException("请先删除字典 " + existing.getDictType() + " 下的数据项");
        }
        typeMapper.deleteById(id);
    }

    public TableDataInfo listData(DictDataQuery query) {
        if (!StringUtils.hasText(query.getDictType())) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictData::getDictType, query.getDictType().trim());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysDictData::getDictLabel, keyword)
                    .or().like(SysDictData::getDictValue, keyword));
        }
        wrapper.eq(StringUtils.hasText(query.getStatus()), SysDictData::getStatus, query.getStatus());
        wrapper.orderByAsc(SysDictData::getDictSort).orderByAsc(SysDictData::getDictCode);
        Page<SysDictData> page = dataMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), page.getRecords());
    }

    public List<SysDictData> options(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return Collections.emptyList();
        }
        return dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType.trim())
                .eq(SysDictData::getStatus, "0")
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getDictCode));
    }

    @Transactional
    public Long createData(DictDataRequest request) {
        String dictType = requireExistingType(request.getDictType());
        SysDictData entity = new SysDictData();
        fillData(entity, request, dictType);
        entity.setCreateBy(SecurityUtils.getUsername());
        dataMapper.insert(entity);
        return entity.getDictCode();
    }

    @Transactional
    public void updateData(Long id, DictDataRequest request) {
        SysDictData existing = requireData(id);
        String dictType = requireExistingType(request.getDictType());
        SysDictData update = new SysDictData();
        update.setDictCode(existing.getDictCode());
        fillData(update, request, dictType);
        update.setUpdateBy(SecurityUtils.getUsername());
        dataMapper.updateById(update);
    }

    @Transactional
    public void deleteData(Long id) {
        requireData(id);
        dataMapper.deleteById(id);
    }

    private void fillData(SysDictData entity, DictDataRequest request, String dictType) {
        entity.setDictSort(request.getDictSort() == null ? 0 : request.getDictSort());
        entity.setDictLabel(requireText(request.getDictLabel(), "字典标签不能为空"));
        entity.setDictValue(requireText(request.getDictValue(), "字典键值不能为空"));
        entity.setDictType(dictType);
        entity.setCssClass(request.getCssClass());
        entity.setListClass(request.getListClass());
        entity.setIsDefault("Y".equalsIgnoreCase(request.getIsDefault()) ? "Y" : "N");
        entity.setStatus(defaultStatus(request.getStatus()));
        entity.setRemark(request.getRemark());
    }

    private SysDictType requireType(Long id) {
        SysDictType type = id == null ? null : typeMapper.selectById(id);
        if (type == null) {
            throw new ServiceException(404, "字典类型不存在");
        }
        return type;
    }

    private SysDictData requireData(Long id) {
        SysDictData data = id == null ? null : dataMapper.selectById(id);
        if (data == null) {
            throw new ServiceException(404, "字典数据不存在");
        }
        return data;
    }

    private String requireExistingType(String dictType) {
        String normalized = requireText(dictType, "字典类型不能为空");
        if (typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, normalized)) == 0) {
            throw new ServiceException("字典类型 " + normalized + " 不存在");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(400, message);
        }
        return value.trim();
    }

    private String defaultStatus(String status) {
        return "1".equals(status) ? "1" : "0";
    }
}

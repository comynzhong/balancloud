package com.balancloud.system.vo;

import com.balancloud.system.domain.SysMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleMenuTreeVo {

    private Long roleId;

    private List<SysMenu> menus = new ArrayList<>();

    private List<Long> checkedMenuIds = new ArrayList<>();
}

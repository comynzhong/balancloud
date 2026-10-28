package com.balancloud.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色授权用的菜单树：与菜单管理页的上级菜单下拉不同，这里必须带上 F（按钮）节点。
 */
@Data
public class GrantMenuVo {

    private Long id;

    private String label;

    private String menuType;

    private String perms;

    private List<GrantMenuVo> children = new ArrayList<>();
}

package com.balancloud.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuRequest {

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过 50 个字符")
    private String menuName;

    private Long parentId;

    @NotNull(message = "显示顺序不能为空")
    @Min(value = 0, message = "显示顺序不能为负数")
    private Integer orderNum;

    @Size(max = 200, message = "路由地址长度不能超过 200 个字符")
    private String path;

    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String component;

    @Size(max = 255, message = "路由参数长度不能超过 255 个字符")
    private String query;

    private String isFrame;

    private String isCache;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    private String visible;

    private String status;

    @Size(max = 100, message = "权限标识长度不能超过 100 个字符")
    private String perms;

    @Size(max = 100, message = "图标长度不能超过 100 个字符")
    private String icon;
}

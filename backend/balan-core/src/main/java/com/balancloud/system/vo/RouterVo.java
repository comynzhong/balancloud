package com.balancloud.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class RouterVo {

    private String name;

    private String path;

    private boolean hidden;

    private String redirect;

    private String component;

    private Boolean alwaysShow;

    private MetaVo meta;

    private List<RouterVo> children;

    private String query;
}

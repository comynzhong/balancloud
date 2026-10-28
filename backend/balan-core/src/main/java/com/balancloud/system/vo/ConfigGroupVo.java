package com.balancloud.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class ConfigGroupVo {

    private String group;

    private String groupName;

    private List<ConfigItemVo> items;
}

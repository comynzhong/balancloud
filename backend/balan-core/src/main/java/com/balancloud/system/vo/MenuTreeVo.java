package com.balancloud.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuTreeVo {

    private Long id;

    private String label;

    private List<MenuTreeVo> children = new ArrayList<>();
}
